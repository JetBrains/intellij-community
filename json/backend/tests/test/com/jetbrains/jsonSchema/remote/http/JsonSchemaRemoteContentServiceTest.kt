// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote.http

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.TrustedProjectsTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.hours

class JsonSchemaRemoteContentServiceTest : BasePlatformTestCase() {
  // The tests wait for downloads. A sync VFS refresh of a download needs the EDT, so a test must not block it.
  override fun runInDispatchThread(): Boolean = false

  private lateinit var scope: CoroutineScope

  override fun setUp() {
    super.setUp()
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    setRemoteSchemaAccessAllowed(true, testRootDisposable)
    clearRemoteSchemaCache()
  }

  override fun tearDown() {
    try {
      scope.cancel()
      clearRemoteSchemaCache()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testCacheHitDoesNotIssueHttp() {
    val cache = FakeCache(CachedSchema.Content(SchemaContent("cached".toByteArray())))
    val transport = FakeTransport()
    val service = service(cache, transport)

    assertEquals("cached", String(service.getCached(URL)!!.bytes))
    assertEquals(0, transport.calls.get())
  }

  fun testCacheMissReturnsImmediatelyAndDownloads() {
    val cache = FakeCache()
    val transport = FakeTransport()
    val service = service(cache, transport)

    val started = System.nanoTime()
    assertTrue(service.prefetch(URL))
    assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 100)
    assertTrue(cache.written.await(5, TimeUnit.SECONDS))
  }

  fun testStaleCacheIsReturnedAndRefreshed() {
    val cache = FakeCache(CachedSchema.Content(SchemaContent("stale".toByteArray()), isStale = true))
    val transport = FakeTransport()
    val service = service(cache, transport)

    assertEquals("stale", String(service.getCached(URL)!!.bytes))
    assertTrue(cache.written.await(5, TimeUnit.SECONDS))
    assertEquals(1, transport.calls.get())
  }

  fun testStaleCacheWithValidatorsRefreshesTimestampAfterNotModifiedResponse() {
    val cache = FakeCache(CachedSchema.Content(SchemaContent("stale".toByteArray()), etag = "etag", isStale = true))
    val transport = NotModifiedTransport()
    val service = service(cache, transport)

    assertEquals("stale", String(service.getCached(URL)!!.bytes))
    assertTrue(cache.refreshed.await(5, TimeUnit.SECONDS))
    assertEquals(1, transport.refreshes.get())
    assertEquals(0, transport.downloads.get())
  }

  fun testNotModifiedDoesNotNotifyListeners() {
    val cache = FakeCache(CachedSchema.Content(SchemaContent("stale".toByteArray()), etag = "etag", isStale = true))
    val transport = NotModifiedTransport()
    val service = service(cache, transport)
    val disposable = Disposer.newDisposable()
    val notified = AtomicBoolean(false)
    subscribeToDownloads(disposable) { notified.set(true) }

    assertNotNull(service.getCachedFile(URL))
    assertTrue(cache.refreshed.await(5, TimeUnit.SECONDS))
    assertEquals(1, transport.refreshes.get())
    assertFalse("a 304 must not reset schema consumers", notified.get())
    Disposer.dispose(disposable)
  }

  fun testNotModifiedNotifiesWhenTheLoadChangesTheMemory() = timeoutRunBlocking {
    val cache = FakeCache(CachedSchema.Content(SchemaContent("stale".toByteArray()), etag = "etag", isStale = true))
    cache.answerLookupWith(null)
    cache.loadChangesMemory = true
    val transport = NotModifiedTransport()
    val service = service(cache, transport)
    val disposable = Disposer.newDisposable()
    val notified = CountDownLatch(1)
    subscribeToDownloads(disposable) { notified.countDown() }

    assertNull(readAction { service.getCachedFile(URL) })
    assertTrue("stale disk + VFS miss + 304 must adopt and notify", notified.await(5, TimeUnit.SECONDS))
    assertEquals(1, transport.refreshes.get())
    assertEquals(0, transport.downloads.get())
    Disposer.dispose(disposable)
  }

  fun testUntrustedProjectReadsNeitherCacheNorNetwork() = assertDeniedDoesNoWork {
    TrustedProjectsTestUtil.enableTrustedProjectsCheck(testRootDisposable)
    TrustedProjects.setProjectTrusted(project, false)
    Disposer.register(testRootDisposable) { TrustedProjects.setProjectTrusted(project, true) }
  }

  fun testRemotePreferenceDisabledReadsNeitherCacheNorNetwork() = assertDeniedDoesNoWork {
    val configuration = JsonSchemaCatalogProjectConfiguration.getInstance(project)
    val catalogEnabled = configuration.isCatalogEnabled
    val preferRemoteSchemas = configuration.isPreferRemoteSchemas
    val implicitSchemasEnabled = configuration.isImplicitSchemasEnabled
    configuration.setState(catalogEnabled, false, preferRemoteSchemas, implicitSchemasEnabled)
    Disposer.register(testRootDisposable) {
      configuration.setState(catalogEnabled, true, preferRemoteSchemas, implicitSchemasEnabled)
    }
  }

  fun testRegistryDisabledReadsNeitherCacheNorNetwork() = assertDeniedDoesNoWork {
    Registry.get("json.schema.networknt.resolve.remote.refs").setValue(false, testRootDisposable)
  }

  fun testUnitTestModeWithoutTheTestModeFlagReadsNeitherCacheNorNetwork() = assertDeniedDoesNoWork {
    setRemoteSchemaAccessAllowed(false, testRootDisposable)
  }

  fun testConcurrentMissesShareOneRequest() {
    val gate = CompletableDeferred<Unit>()
    val cache = FakeCache()
    val transport = FakeTransport(gate)
    val service = service(cache, transport)

    repeat(20) { assertTrue(service.prefetch(URL)) }
    assertTrue(transport.started.await(5, TimeUnit.SECONDS))
    assertEquals(1, transport.calls.get())
    gate.complete(Unit)
    assertTrue(cache.written.await(5, TimeUnit.SECONDS))
  }

  fun testAccessRevokedWhileQueuedPerformsNoWork() = timeoutRunBlocking {
    val gate = CompletableDeferred<Unit>()
    val cache = FakeCache()
    val transport = FakeTransport(gate)
    val disposable = Disposer.newDisposable()
    val notified = CopyOnWriteArrayList<SchemaUrl>()
    val service = service(cache, transport)
    subscribeToDownloads(disposable) { notified.add(it) }

    val queued = SchemaUrl.parse("https://example.com/queued.json")
    val queuedStatusRead = cache.statusReadFor(queued)
    repeat(32) { service.prefetch(SchemaUrl.parse("https://example.com/$it.json")) }
    assertTrue(transport.thirtyTwoStarted.await(5, TimeUnit.SECONDS))
    service.prefetch(queued)
    assertTrue(queuedStatusRead.await(5, TimeUnit.SECONDS))

    setRemoteSchemaAccessAllowed(false, testRootDisposable)
    gate.complete(Unit)

    waitUntil("the queued download must complete") { !service.hasInFlight(queued) }
    assertEquals("the queued URL must not be fetched after access is revoked", 1L, transport.thirtyThreeStarted.count)
    assertFalse("the queued URL must not be cached", cache.writtenUrls().contains(queued))
    assertFalse("the queued URL must not be published", notified.contains(queued))
    Disposer.dispose(disposable)
  }

  fun testAccessRevokedDuringTheRequestDiscardsTheResponse() = timeoutRunBlocking {
    val gate = CompletableDeferred<Unit>()
    val cache = FakeCache()
    val transport = FakeTransport(gate)
    val disposable = Disposer.newDisposable()
    val notified = CopyOnWriteArrayList<SchemaUrl>()
    val service = service(cache, transport)
    subscribeToDownloads(disposable) { notified.add(it) }

    service.prefetch(URL)
    assertTrue(transport.started.await(5, TimeUnit.SECONDS))
    setRemoteSchemaAccessAllowed(false, testRootDisposable)
    gate.complete(Unit)

    waitUntil("the download must complete after access is revoked") { !service.hasInFlight(URL) }
    assertFalse("a response fetched before access is revoked must not be cached", cache.writtenUrls().contains(URL))
    assertFalse("a response fetched before access is revoked must not be published", notified.contains(URL))
    Disposer.dispose(disposable)
  }

  fun testCancelledScopeDoesNotLeaveAnInFlightEntry() = timeoutRunBlocking {
    val service = service(FakeCache(), FakeTransport())
    scope.cancel()
    scope.coroutineContext.job.join()

    service.prefetch(URL)
    service.prefetch(URL)

    assertFalse("a cancelled scope must not pin the URL in inFlight", service.hasInFlight(URL))
  }

  fun testAtMostThirtyTwoDownloadsRunConcurrently() {
    val gate = CompletableDeferred<Unit>()
    val transport = FakeTransport(gate)
    val service = service(FakeCache(), transport)

    repeat(40) { service.prefetch(SchemaUrl.parse("https://example.com/$it.json")) }
    assertTrue(transport.thirtyTwoStarted.await(5, TimeUnit.SECONDS))
    assertEquals(32, transport.maxConcurrent.get())
    gate.complete(Unit)
  }

  fun testDisposedProjectCancelsDownloads() {
    val gate = CompletableDeferred<Unit>()
    val transport = FakeTransport(gate)
    val service = service(FakeCache(), transport)
    service.prefetch(URL)
    assertTrue(transport.started.await(5, TimeUnit.SECONDS))

    scope.cancel()

    assertTrue(transport.cancelled.await(5, TimeUnit.SECONDS))
  }

  fun testSuccessfulDownloadNotifiesListenerOnce() {
    val cache = FakeCache()
    val service = service(cache, FakeTransport())
    val disposable = Disposer.newDisposable()
    val notifications = AtomicInteger()
    val notified = CountDownLatch(1)
    subscribeToDownloads(disposable) {
      notifications.incrementAndGet()
      notified.countDown()
    }

    service.prefetch(URL)
    assertTrue(notified.await(5, TimeUnit.SECONDS))
    assertEquals(1, notifications.get())
    Disposer.dispose(disposable)
  }

  fun testGetCachedFileStringOverloadReturnsNullOnMalformedUrl() {
    val service = service(FakeCache(), FakeTransport())

    assertNull(service.getCachedFile("not a url"))
  }

  fun testNotFoundDownloadIsCachedAndPreventsFurtherDownloadAttempts() {
    val cache = FakeCache()
    val transport = NotFoundTransport()
    val service = service(cache, transport)

    assertTrue(service.prefetch(URL))
    assertTrue(cache.written.await(5, TimeUnit.SECONDS))
    assertEquals(1, transport.calls.get())

    assertTrue(service.prefetch(URL))
    Thread.sleep(200)
    assertEquals(1, transport.calls.get())
  }

  fun testErrorDownloadNotifiesListenersAndCachesUnavailable() {
    val cache = FakeCache()
    val service = service(cache, NotFoundTransport())
    val disposable = Disposer.newDisposable()
    val notified = CountDownLatch(1)
    subscribeToDownloads(disposable) { notified.countDown() }

    assertNull(service.downloadAndWaitForTest(URL))
    assertTrue("terminal Error must refresh widget/annotator", notified.await(5, TimeUnit.SECONDS))
    assertTrue(service.isUnavailable(URL))
    assertNull(service.lastFailure(URL))
    Disposer.dispose(disposable)
  }

  fun testGetCachedReturnsNullOnMissWithoutTriggeringDownload() {
    val cache = FakeCache()
    val transport = FakeTransport()
    val service = service(cache, transport)

    assertNull(service.getCached(URL))
    Thread.sleep(200)
    assertEquals(0, transport.calls.get())
  }

  fun testGetCachedReturnsNullForCachedNotFound() {
    val cache = FakeCache(CachedSchema.Unavailable(404, "Not Found"))
    val transport = FakeTransport()
    val service = service(cache, transport)

    assertNull(service.getCached(URL))
    assertEquals(0, transport.calls.get())
  }

  fun testDownloadListenerNotInvokedOnFreshCacheHit() {
    val cache = FakeCache(CachedSchema.Content(SchemaContent("cached".toByteArray())))
    val service = service(cache, FakeTransport())
    val disposable = Disposer.newDisposable()
    val notifications = AtomicInteger()
    subscribeToDownloads(disposable) { notifications.incrementAndGet() }

    service.getCached(URL)

    Thread.sleep(200)
    assertEquals(0, notifications.get())
    Disposer.dispose(disposable)
  }

  fun testGetCachedFileTriggersPrefetchOnMiss() {
    val cache = FakeCache()
    val transport = FakeTransport()
    val service = service(cache, transport)

    assertNull(service.getCachedFile(URL))
    assertTrue(cache.written.await(5, TimeUnit.SECONDS))
    assertEquals(1, transport.calls.get())
  }

  fun testGetCachedFileDoesNotCallTransportForFreshCacheHit() {
    val cache = FakeCache(CachedSchema.Content(SchemaContent("cached".toByteArray())))
    val transport = FakeTransport()
    val service = service(cache, transport)

    service.getCachedFile(URL)

    Thread.sleep(200)
    assertEquals(0, transport.calls.get())
  }

  fun testFileLookupDoesNotReadSchemaContent() {
    val cache = FakeCache(CachedSchema.Content(SchemaContent("cached".toByteArray())))
    val service = service(cache, FakeTransport())

    assertNotNull(service.getCachedFile(URL))

    assertEquals("file lookup must not read the schema bytes", 0, cache.readCalls.get())
    assertEquals(1, cache.lookupFileCalls.get())
  }

  fun testCachedUnavailableDoesNotScheduleADownload() {
    val cache = FakeCache()
    cache.answerLookupWith(CachedFile.Unavailable(404, "Not Found"))
    val transport = FakeTransport()
    val service = service(cache, transport)

    assertNull(service.getCachedFile(URL))

    assertFalse("a cached 404 must not schedule a request", transport.started.await(100, TimeUnit.MILLISECONDS))
    assertEquals("a cached 404 must not be re-requested", 0, transport.calls.get())
  }

  fun testDownloadStartsWhenTheContentIsGone() = timeoutRunBlocking {
    val root = JsonSchemaFileCache.rootForTest()
    val cache = ApplicationManager.getApplication().service<SchemaContentCache>()
    val transport = FakeTransport()
    val service = service(transport = transport)
    cache.write(URL, DownloadResult.Success(SchemaContent("{}".toByteArray()), null, null, URL.value))
    val content = Files.list(root).use { files -> files.toList().single { !it.fileName.toString().endsWith(".meta") } }
    Files.delete(content)
    VfsUtil.markDirtyAndRefresh(false, false, false, content.toFile())

    service.downloadAndWaitForTest(URL)

    assertEquals("a half-written pair must fall through to a download", 1, transport.calls.get())
  }

  fun testDiskEntryMissingInMemoryIsLoadedWithoutADownload() = timeoutRunBlocking {
    val cache = ApplicationManager.getApplication().service<SchemaContentCache>() as JsonSchemaFileCache
    cache.write(URL, DownloadResult.Success(SchemaContent("{}".toByteArray()), null, null, URL.value))
    cache.clearMemoryForTest()
    val transport = FakeTransport()
    val service = service(transport = transport)
    val disposable = Disposer.newDisposable()
    val notified = CountDownLatch(1)
    subscribeToDownloads(disposable) { notified.countDown() }

    assertNull("a memory miss must not read the disk", readAction { service.getCachedFile(URL) })
    assertTrue("the background load must notify the listeners", notified.await(5, TimeUnit.SECONDS))
    assertNotNull(readAction { service.getCachedFile(URL) })
    assertEquals(0, transport.calls.get())
    Disposer.dispose(disposable)
  }

  fun testSuccessfulDownloadAdoptsFileBeforeListenersObserveIt() = timeoutRunBlocking {
    val transport = FakeTransport()
    val service = service(transport = transport)
    val adoptedInListener = AtomicReference<VirtualFile?>()
    val disposable = Disposer.newDisposable()
    subscribeToDownloads(disposable) { url ->
      adoptedInListener.set(runReadActionBlocking { service.getCachedFile(url) })
    }

    assertNull(service.getCachedFile(URL))
    waitUntil("the listener must observe the adopted file") { adoptedInListener.get() != null }

    val file = adoptedInListener.get()
    assertNotNull("listeners must observe an adopted local file", file)
    assertEquals(URL.value, file!!.getUserData(SchemaOrigin.URL_KEY))
    assertEquals(URL.value, String(file.contentsToByteArray()))
    Disposer.dispose(disposable)
  }

  fun testFreshDiskCachePublishesWhenTheLoadChangesTheMemory() = timeoutRunBlocking {
    val cache = FakeCache(CachedSchema.Content(SchemaContent("{}".toByteArray())))
    cache.answerLookupWith(null)
    cache.loadChangesMemory = true
    val transport = FakeTransport()
    val service = service(cache, transport)
    val disposable = Disposer.newDisposable()
    val notified = CountDownLatch(1)
    subscribeToDownloads(disposable) { notified.countDown() }

    assertNull(readAction { service.getCachedFile(URL) })
    assertTrue("a fresh cache must publish after VFS adoption", notified.await(5, TimeUnit.SECONDS))
    assertEquals(0, transport.calls.get())
    Disposer.dispose(disposable)
  }

  fun testRepeatedAdoptionKeepsTheLocalFile() = timeoutRunBlocking {
    val service = service(transport = FakeTransport())

    assertNull(service.getCachedFile(URL))
    waitUntil("the download must adopt the file") { service.getCachedFile(URL) != null }
    val first = service.getCachedFile(URL)
    assertNotNull(first)

    assertTrue(service.prefetch(URL))
    waitUntil("the file must stay valid after another download") {
      val again = readAction { service.getCachedFile(URL) }
      again != null && again.isValid
    }
    val second = service.getCachedFile(URL)
    assertNotNull(second)
    assertTrue(second!!.isValid)
    assertEquals(first!!.path, second.path)
  }

  fun testRevalidatedContentReachesTheAdoptedFile() = timeoutRunBlocking {
    val transport = VersionedTransport("{\"v\":1}")
    val service = service(transport = transport)

    assertNull(service.getCachedFile(URL))
    waitUntil("the download must adopt the file") { service.getCachedFile(URL) != null }
    val file = service.getCachedFile(URL)!!
    assertEquals("{\"v\":1}", String(file.contentsToByteArray()))

    ageRemoteSchemaCache(5.hours)
    transport.content = "{\"v\":2}"
    assertNull("the aging clears the memory, so the next lookup is a miss", service.getCachedFile(URL))

    waitUntil("the adopted file must show the downloaded content") { String(file.contentsToByteArray()) == "{\"v\":2}" }
    assertEquals(2, transport.calls.get())
  }

  fun testConcurrentLookupUnderReadActionDoesNotDeleteValidContentWhileWriterAdopts() = timeoutRunBlocking {
    val root = JsonSchemaFileCache.rootForTest()
    val cache = ApplicationManager.getApplication().service<SchemaContentCache>()
    val gate = CompletableDeferred<Unit>()
    val transport = FakeTransport(gate)
    val service = service(transport = transport)

    assertNull(service.getCachedFile(URL))
    assertTrue(transport.started.await(5, TimeUnit.SECONDS))

    val lookup = async(Dispatchers.Default) {
      while (isActive) {
        readAction { cache.lookupFile(URL) }
      }
    }
    try {
      gate.complete(Unit)
      waitUntil("the download must adopt the file") { service.getCachedFile(URL) != null }
    }
    finally {
      lookup.cancelAndJoin()
    }

    assertEquals("valid nio content/meta must survive concurrent Group A lookups", 2, Files.list(root).use { it.count() })
    val adopted = readAction { service.getCachedFile(URL) }
    assertNotNull(adopted)
  }

  fun testGetCachedFileDoesNotPrefetchAfterFailed() {
    val transport = FailingTransport()
    val service = service(FakeCache(), transport)
    assertNull(service.downloadAndWaitForTest(URL))
    assertEquals(1, transport.calls.get())
    assertNull(service.getCachedFile(URL))
    assertEquals(1, transport.calls.get())
    assertFalse(service.hasInFlight(URL))
  }

  fun testGetCachedFileDoesNotPrefetchAfterRejected() {
    val transport = RejectedTransport()
    val service = service(FakeCache(), transport)
    assertNull(service.downloadAndWaitForTest(URL))
    assertEquals(1, transport.calls.get())
    assertNull(service.getCachedFile(URL))
    assertEquals(1, transport.calls.get())
    assertFalse(service.hasInFlight(URL))
  }

  fun testFailedDownloadNotifiesListenersWithoutCaching() {
    val cache = FakeCache()
    val service = service(cache, FailingTransport())
    val disposable = Disposer.newDisposable()
    val notified = CountDownLatch(1)
    subscribeToDownloads(disposable) { notified.countDown() }

    assertNull(service.downloadAndWaitForTest(URL))
    assertTrue("terminal Failed must refresh widget/annotator", notified.await(5, TimeUnit.SECONDS))
    assertEquals(RemoteDownloadFailure.Failed, service.lastFailure(URL))
    assertTrue(cache.writtenUrls().isEmpty())
    Disposer.dispose(disposable)
  }

  fun testRejectedDownloadNotifiesListenersWithoutCaching() {
    val cache = FakeCache()
    val service = service(cache, RejectedTransport())
    val disposable = Disposer.newDisposable()
    val notified = CountDownLatch(1)
    subscribeToDownloads(disposable) { notified.countDown() }

    assertNull(service.downloadAndWaitForTest(URL))
    assertTrue(notified.await(5, TimeUnit.SECONDS))
    assertEquals(RemoteDownloadFailure.Rejected, service.lastFailure(URL))
    assertTrue(cache.writtenUrls().isEmpty())
    Disposer.dispose(disposable)
  }

  fun testSuccessClearsLastFailure() {
    val cache = FakeCache()
    val failing = service(cache, FailingTransport())
    assertNull(failing.downloadAndWaitForTest(URL))
    assertEquals(RemoteDownloadFailure.Failed, failing.lastFailure(URL))

    val ok = service(FakeCache(), FakeTransport())
    assertNotNull(ok.downloadAndWaitForTest(URL))
    assertNull(ok.lastFailure(URL))
  }

  fun testPrefetchDoesNotDownloadAgainAfterFailed() {
    val transport = FailingTransport()
    val service = service(FakeCache(), transport)
    assertNull(service.downloadAndWaitForTest(URL))
    assertEquals(1, transport.calls.get())

    assertTrue(service.prefetch(URL))
    assertFalse("a failed URL must wait for an explicit retry", service.hasInFlight(URL))
    Thread.sleep(200)
    assertEquals(1, transport.calls.get())
  }

  fun testUnexpectedTransportExceptionIsRecordedAsFailure() {
    val transport = ThrowingTransport()
    assertUnexpectedExceptionIsRecorded(service(FakeCache(), transport)) { transport.calls.get() }
  }

  fun testUnexpectedCacheWriteExceptionIsRecordedAsFailure() {
    val transport = FakeTransport()
    assertUnexpectedExceptionIsRecorded(service(ThrowingWriteCache(), transport)) { transport.calls.get() }
  }

  private fun assertUnexpectedExceptionIsRecorded(service: JsonSchemaRemoteContentService, calls: () -> Int) {
    val notified = CountDownLatch(1)
    subscribeToDownloads(testRootDisposable) { notified.countDown() }

    assertNull(service.downloadAndWaitForTest(URL))

    assertEquals(RemoteDownloadFailure.Failed, service.lastFailure(URL))
    assertTrue("an unexpected exception must refresh the widget and the annotator", notified.await(5, TimeUnit.SECONDS))
    assertTrue(service.prefetch(URL))
    assertFalse("a failed URL must not start a new download on each prefetch", service.hasInFlight(URL))
    assertEquals(1, calls())
  }

  fun testRetryDownloadClearsFailureAndDownloadsAgain() {
    val cache = FakeCache()
    val transport = FailingTransport()
    val service = service(cache, transport)
    assertNull(service.downloadAndWaitForTest(URL))
    assertEquals(RemoteDownloadFailure.Failed, service.lastFailure(URL))
    val disposable = Disposer.newDisposable()
    val notified = CountDownLatch(1)
    subscribeToDownloads(disposable) { notified.countDown() }

    transport.failing.set(false)
    service.retryDownload(URL.value)

    assertTrue(notified.await(5, TimeUnit.SECONDS))
    assertEquals(2, transport.calls.get())
    assertNull(service.lastFailure(URL))
    assertNotNull(service.peekCachedFile(URL))
    Disposer.dispose(disposable)
  }

  fun testRetryDownloadSkipsTheCachedError() {
    val cache = FakeCache(CachedSchema.Unavailable(403, "Forbidden"))
    val transport = FakeTransport()
    val service = service(cache, transport)

    service.retryDownload(URL)

    assertTrue("an explicit retry must not wait for the error TTL", cache.written.await(5, TimeUnit.SECONDS))
    assertEquals(1, transport.calls.get())
    assertFalse(service.isUnavailable(URL))
  }

  fun testRetryDownloadRevalidatesFreshContent() {
    val cache = FakeCache(CachedSchema.Content(SchemaContent("fresh".toByteArray()), etag = "etag"))
    val transport = NotModifiedTransport()
    val service = service(cache, transport)

    service.retryDownload(URL)

    assertTrue(cache.refreshed.await(5, TimeUnit.SECONDS))
    assertEquals(1, transport.refreshes.get())
    assertEquals(0, transport.downloads.get())
  }

  fun testPrefetchDoesNotSkipTheCachedError() = timeoutRunBlocking {
    val cache = FakeCache(CachedSchema.Unavailable(403, "Forbidden"))
    val transport = FakeTransport()
    val service = service(cache, transport)
    val statusRead = cache.statusReadFor(URL)

    assertTrue(service.prefetch(URL))
    assertTrue(statusRead.await(5, TimeUnit.SECONDS))
    waitUntil("the download must complete") { !service.hasInFlight(URL) }

    assertEquals(0, transport.calls.get())
  }

  fun testRetryDownloadDoesNothingWhenNotAllowed() {
    val transport = FakeTransport()
    val service = service(FakeCache(), transport)
    setRemoteSchemaAccessAllowed(false, testRootDisposable)

    service.retryDownload(URL)

    assertFalse(service.hasInFlight(URL))
    assertEquals(0, transport.calls.get())
  }

  fun testPeekCachedFileDoesNotPrefetchOnMiss() {
    val cache = FakeCache()
    val transport = FakeTransport()
    val service = service(cache, transport)

    assertNull(service.peekCachedFile(URL))
    assertEquals(0, transport.calls.get())
    assertFalse(service.hasInFlight(URL))
  }

  fun testPeekCachedFileReturnsAvailableWithoutRefresh() {
    val cache = FakeCache(CachedSchema.Content(SchemaContent("cached".toByteArray())))
    val transport = FakeTransport()
    val service = service(cache, transport)

    assertNotNull(service.peekCachedFile(URL))
    assertEquals(0, transport.calls.get())
  }

  private fun subscribeToDownloads(disposable: Disposable, listener: (SchemaUrl) -> Unit) {
    project.messageBus.connect(disposable).subscribe(RemoteSchemaDownloadListener.TOPIC, RemoteSchemaDownloadListener {
      listener(SchemaUrl.parse(it))
    })
  }

  private fun assertDeniedDoesNoWork(denyAccess: () -> Unit) {
    val cache = FakeCache()
    val transport = FakeTransport()
    val service = service(cache, transport)
    denyAccess()

    assertFalse(service.isAllowed())
    assertNull(service.getCached(URL))
    assertFalse(service.prefetch(URL))
    assertEquals(0, cache.readCalls.get())
    assertEquals(0, transport.calls.get())
  }

  private fun service(
    cache: SchemaContentCache? = null,
    transport: SchemaHttpTransport,
  ): JsonSchemaRemoteContentService = replaceRemoteSchemaServices(project, scope, testRootDisposable, cache, transport)

  private class FakeCache(private var cached: CachedSchema? = null) : SchemaContentCache {
    val readCalls = AtomicInteger()
    val readStatusCalls = AtomicInteger()
    val lookupFileCalls = AtomicInteger()
    val written = CountDownLatch(1)
    val refreshed = CountDownLatch(1)
    private val writtenUrls = CopyOnWriteArrayList<SchemaUrl>()
    private val statusReadLatches = ConcurrentHashMap<SchemaUrl, CountDownLatch>()
    private var cachedFile = cached.toCachedFile()

    @Volatile
    var loadChangesMemory: Boolean = false

    fun writtenUrls(): List<SchemaUrl> = writtenUrls.toList()

    fun statusReadFor(url: SchemaUrl): CountDownLatch = statusReadLatches.computeIfAbsent(url) { CountDownLatch(1) }

    override fun read(url: SchemaUrl): CachedSchema? {
      readCalls.incrementAndGet()
      return cached
    }

    override fun readStatus(url: SchemaUrl): CachedStatus? {
      readStatusCalls.incrementAndGet()
      statusReadLatches[url]?.countDown()
      return when (val value = cached) {
        is CachedSchema.Content -> CachedStatus.Available(value.isStale, value.etag, value.lastModified)
        is CachedSchema.Unavailable -> CachedStatus.Unavailable(value.code, value.message)
        null -> null
      }
    }

    override fun lookupFile(url: SchemaUrl): CachedFile? {
      lookupFileCalls.incrementAndGet()
      return cachedFile
    }

    fun answerLookupWith(value: CachedFile?) {
      cachedFile = value
    }

    override fun load(url: SchemaUrl): Boolean = loadChangesMemory

    override fun write(url: SchemaUrl, result: DownloadResult) {
      writtenUrls.add(url)
      when (result) {
        is DownloadResult.Success -> cached = CachedSchema.Content(result.content)
        is DownloadResult.Error -> cached = CachedSchema.Unavailable(result.code, result.message)
        else -> Unit
      }
      cachedFile = cached.toCachedFile()
      written.countDown()
    }

    override fun refreshTimestamp(url: SchemaUrl) {
      cached = (cached as? CachedSchema.Content)?.copy(isStale = false)
      refreshed.countDown()
    }

    private fun CachedSchema?.toCachedFile(): CachedFile? = when (this) {
      is CachedSchema.Content -> CachedFile.Available(LightVirtualFile("cached.json", String(content.bytes)), isStale)
      is CachedSchema.Unavailable -> CachedFile.Unavailable(code, message)
      null -> null
    }
  }

  private class FakeTransport(private val gate: CompletableDeferred<Unit>? = null) : SchemaHttpTransport {
    val calls = AtomicInteger()
    val active = AtomicInteger()
    val maxConcurrent = AtomicInteger()
    val started = CountDownLatch(1)
    val thirtyTwoStarted = CountDownLatch(32)
    val thirtyThreeStarted = CountDownLatch(33)
    val cancelled = CountDownLatch(1)

    override suspend fun download(url: SchemaUrl): DownloadResult {
      calls.incrementAndGet()
      val nowActive = active.incrementAndGet()
      maxConcurrent.updateAndGet { maxOf(it, nowActive) }
      started.countDown()
      thirtyTwoStarted.countDown()
      thirtyThreeStarted.countDown()
      return try {
        gate?.await()
        DownloadResult.Success(SchemaContent(url.value.toByteArray()), null, null, url.value)
      }
      catch (e: kotlinx.coroutines.CancellationException) {
        cancelled.countDown()
        throw e
      }
      finally {
        active.decrementAndGet()
      }
    }
  }

  private class VersionedTransport(@Volatile var content: String) : SchemaHttpTransport {
    val calls = AtomicInteger()

    override suspend fun download(url: SchemaUrl): DownloadResult {
      calls.incrementAndGet()
      return DownloadResult.Success(SchemaContent(content.toByteArray()), null, null, url.value)
    }
  }

  private class NotModifiedTransport : SchemaHttpTransport {
    val downloads = AtomicInteger()
    val refreshes = AtomicInteger()

    override suspend fun download(url: SchemaUrl): DownloadResult {
      downloads.incrementAndGet()
      return DownloadResult.Failed(AssertionError("Expected conditional refresh"))
    }

    override suspend fun refresh(
      url: SchemaUrl,
      etag: String?,
      lastModified: String?,
    ): DownloadResult {
      refreshes.incrementAndGet()
      assertEquals("etag", etag)
      return DownloadResult.NotModified
    }
  }

  private class NotFoundTransport : SchemaHttpTransport {
    val calls = AtomicInteger()

    override suspend fun download(url: SchemaUrl): DownloadResult {
      calls.incrementAndGet()
      return DownloadResult.Error(404, "Not Found")
    }
  }

  private class FailingTransport : SchemaHttpTransport {
    val calls = AtomicInteger()
    val failing = AtomicBoolean(true)

    override suspend fun download(url: SchemaUrl): DownloadResult {
      calls.incrementAndGet()
      if (!failing.get()) return DownloadResult.Success(SchemaContent(url.value.toByteArray()), null, null, url.value)
      return DownloadResult.Failed(IOException("boom"))
    }
  }

  private class ThrowingTransport : SchemaHttpTransport {
    val calls = AtomicInteger()

    override suspend fun download(url: SchemaUrl): DownloadResult {
      calls.incrementAndGet()
      throw IllegalStateException("unexpected")
    }
  }

  private class ThrowingWriteCache : SchemaContentCache {
    override fun read(url: SchemaUrl): CachedSchema? = null

    override fun write(url: SchemaUrl, result: DownloadResult) {
      throw IOException("disk is full")
    }
  }

  private class RejectedTransport : SchemaHttpTransport {
    val calls = AtomicInteger()

    override suspend fun download(url: SchemaUrl): DownloadResult {
      calls.incrementAndGet()
      return DownloadResult.Rejected("unsupported")
    }
  }

  companion object {
    private val URL = SchemaUrl.parse("https://example.com/schema.json")
  }
}
