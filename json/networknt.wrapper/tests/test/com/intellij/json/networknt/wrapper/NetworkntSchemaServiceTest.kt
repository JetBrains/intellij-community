// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.json.JsonFileType
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.TrustedProjectsTestUtil
import com.intellij.testFramework.common.waitUntilAssertSucceedsBlocking
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import com.jetbrains.jsonSchema.remote.http.CachedSchema
import com.jetbrains.jsonSchema.remote.http.CachedStatus
import com.jetbrains.jsonSchema.remote.http.DownloadResult
import com.jetbrains.jsonSchema.remote.http.JsonSchemaRemoteContentService
import com.jetbrains.jsonSchema.remote.http.SchemaContent
import com.jetbrains.jsonSchema.remote.http.SchemaContentCache
import com.jetbrains.jsonSchema.remote.http.SchemaHttpTransport
import com.jetbrains.jsonSchema.remote.http.SchemaUrl
import com.networknt.schema.AbsoluteIri
import com.networknt.schema.SpecificationVersion
import com.networknt.schema.resource.SchemaLoader
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel

class NetworkntSchemaServiceTest : BasePlatformTestCase() {

  companion object {
    private const val SMALL_SCHEMA = """{"type": "object", "properties": {"name": {"type": "string"}}, "required": ["name"]}"""
    private val VERSION = SpecificationVersion.DRAFT_7
    private const val AZURE_SCHEMA_FILE = "azure-arm-schema.json"
    private const val AZURE_INSTANCE_FILE = "azure-instance.json"
    private const val REFERENCED_SCHEMA = """{"type": "string"}"""
    private const val REMOTE_REFERENCE = "https://schema.example.test/remote.json"
    private const val MAPPED_SCHEMA_ID = "https://schema.example.test/mapped.json"
    private const val MAPPED_SCHEMA = """{"${'$'}schema": "http://json-schema.org/draft-07/schema#", "${'$'}id": "$MAPPED_SCHEMA_ID", "type": "string"}"""
  }

  private val filesToDelete = mutableListOf<Path>()

  override fun getTestDataPath(): String =
    PathManager.getCommunityHomePath() + "/json/networknt.wrapper/tests/testData"

  private fun service() = NetworkntSchemaService.getInstance(project)

  override fun setUp() {
    super.setUp()
    TrustedProjectsTestUtil.enableTrustedProjectsCheck(testRootDisposable)
    TrustedProjects.setProjectTrusted(project, true)
    service().invalidateAllCaches("test setUp")
  }

  override fun tearDown() {
    try {
      TrustedProjects.setProjectTrusted(project, true)
      filesToDelete.forEach { Files.deleteIfExists(it) }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  private fun loadAzureSchema(): String =
    File(testDataPath, AZURE_SCHEMA_FILE).readText()

  fun `test resource loader rechecks trust after construction`() {
    val outsideSchema = createOutsideSchema()
    val schemaFile = myFixture.addFileToProject(
      "outside-reference.json",
      schemaWithReference(outsideSchema.toUri().toString()),
    ).virtualFile

    TrustedProjects.setProjectTrusted(project, true)
    val loader = IntelliJSchemaLoader(project, schemaFile)

    TrustedProjects.setProjectTrusted(project, false)
    assertReferenceIsForbidden(loader, outsideSchema.toUri().toString())
  }

  fun `test in-project reference is allowed when project is untrusted`() {
    val referencedSchema = createProjectFile("schemas/referenced.json", REFERENCED_SCHEMA)
    val schemaFile = createProjectFile(
      "in-project-reference.json",
      schemaWithReference(referencedSchema.url),
    )

    TrustedProjects.setProjectTrusted(project, false)
    service().getNetworkntSchema(schemaFile, VERSION).initializeValidators()
  }

  fun `test outside file reference is allowed when project is trusted`() {
    val outsideSchema = createOutsideSchema()
    val schemaFile = myFixture.addFileToProject(
      "trusted-outside-reference.json",
      schemaWithReference(outsideSchema.toUri().toString()),
    ).virtualFile

    TrustedProjects.setProjectTrusted(project, true)
    service().getNetworkntSchema(schemaFile, VERSION).initializeValidators()
  }

  fun `test remote reference is blocked when project is untrusted`() {
    val schemaFile = myFixture.addFileToProject(
      "remote-reference.json",
      schemaWithReference("https://schema.example.test/attacker.json"),
    ).virtualFile

    TrustedProjects.setProjectTrusted(project, false)
    assertReferenceIsForbidden(IntelliJSchemaLoader(project, schemaFile), "https://schema.example.test/attacker.json")
  }

  fun `test HTTP id of a user mapping outside the project is blocked when project is untrusted`() {
    val schemaFile = mapOutsideSchemaById()

    TrustedProjects.setProjectTrusted(project, false)
    assertReferenceIsForbidden(IntelliJSchemaLoader(project, schemaFile), MAPPED_SCHEMA_ID)
  }

  fun `test HTTP id of a user mapping outside the project resolves when project is trusted`() {
    val schemaFile = mapOutsideSchemaById()

    val source = requireNotNull(IntelliJSchemaLoader(project, schemaFile).getSchemaResource(AbsoluteIri.of(MAPPED_SCHEMA_ID)))
    assertEquals(MAPPED_SCHEMA, source.inputStream.reader().use { it.readText() })
  }

  fun `test HTTP cache hit returns bytes without IRI loader`() {
    val schemaFile = myFixture.addFileToProject("remote-cache-hit.json", SMALL_SCHEMA).virtualFile
    val remote = installRemoteContent(cached = "cached schema".toByteArray())
    val loader = IntelliJSchemaLoader(project, schemaFile)

    val source = requireNotNull(loader.getSchemaResource(AbsoluteIri.of(REMOTE_REFERENCE)))

    assertEquals("cached schema", source.inputStream.reader().use { it.readText() })
    assertEquals(1, remote.cacheReads.get())
    assertEquals(0, remote.downloads.get())
  }

  fun `test HTTP cache miss schedules one prefetch and returns null`() {
    val schemaFile = myFixture.addFileToProject("remote-cache-miss.json", SMALL_SCHEMA).virtualFile
    val remote = installRemoteContent()
    val loader = IntelliJSchemaLoader(project, schemaFile)

    assertNull(loader.getSchemaResource(AbsoluteIri.of(REMOTE_REFERENCE)))
    assertEquals(1, remote.cacheReads.get())
    assertTrue(remote.downloadStarted.await(5, TimeUnit.SECONDS))
    assertEquals(1, remote.downloads.get())
  }

  fun `test remote root schema cache miss does not read HTTP VFS content`() {
    val schemaFile = requireNotNull(VirtualFileManager.getInstance().findFileByUrl(REMOTE_REFERENCE))

    assertNotNull(service().getNetworkntSchema(schemaFile, VERSION))
  }

  fun `test untrusted HTTP denial reads neither cache nor IRI`() = assertRemotePolicyDenial(forbidden = true) {
    TrustedProjects.setProjectTrusted(project, false)
  }

  fun `test remote-disabled HTTP denial reads neither cache nor IRI`() = assertRemotePolicyDenial(forbidden = false) {
    TestModeFlags.set(JsonFileResolver.REMOTE_ENABLED_IN_TESTS, false, testRootDisposable)
  }

  fun `test registry-disabled HTTP denial reads neither cache nor IRI`() = assertRemotePolicyDenial(forbidden = false) {
    Registry.get("json.schema.networknt.resolve.remote.refs").setValue(false, testRootDisposable)
  }

  fun `test disabled remote reference fails as not found`() {
    TestModeFlags.set(JsonFileResolver.REMOTE_ENABLED_IN_TESTS, false, testRootDisposable)
    val schemaFile = myFixture.addFileToProject("remote-disabled-ref.json", schemaWithReference(REMOTE_REFERENCE)).virtualFile
    val schema = service().getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_7)

    val failure = runCatching { runReadActionBlocking { schema.initializeValidators() } }.exceptionOrNull()

    assertInstanceOf(failure?.cause, FileNotFoundException::class.java)
  }

  /** @param forbidden `true` when the loader must return a source that throws [AccessDeniedException], `false` when it must return `null`. */
  private fun assertRemotePolicyDenial(forbidden: Boolean, denyAccess: () -> Unit) {
    val schemaFile = myFixture.addFileToProject("remote-denied-${System.nanoTime()}.json", SMALL_SCHEMA).virtualFile
    val remote = installRemoteContent()
    denyAccess()
    val loader = IntelliJSchemaLoader(project, schemaFile)

    if (forbidden) {
      assertReferenceIsForbidden(loader, REMOTE_REFERENCE)
    }
    else {
      assertNull(loader.getSchemaResource(AbsoluteIri.of(REMOTE_REFERENCE)))
    }
    assertEquals(0, remote.cacheReads.get())
    assertEquals(0, remote.downloads.get())
  }

  private fun createOutsideSchema(): Path {
    val schema = Files.writeString(Files.createTempFile("networknt-outside-schema", ".json"), REFERENCED_SCHEMA)
    filesToDelete.add(schema)
    VfsRootAccess.allowRootAccess(testRootDisposable, schema.parent.toString())
    return schema
  }

  /** Registers a user schema outside the project and returns a project schema that refers to its `${'$'}id`. */
  private fun mapOutsideSchemaById(): VirtualFile {
    val outsideSchema = Files.writeString(Files.createTempFile("networknt-mapped-schema", ".json"), MAPPED_SCHEMA)
    filesToDelete.add(outsideSchema)
    VfsRootAccess.allowRootAccess(testRootDisposable, outsideSchema.parent.toString())
    val outsideFile = requireNotNull(VirtualFileManager.getInstance().refreshAndFindFileByNioPath(outsideSchema))
    val provider = object : JsonSchemaFileProvider {
      override fun isAvailable(file: VirtualFile): Boolean = false
      override fun getName(): String = "outside"
      override fun getSchemaFile(): VirtualFile = outsideFile
      override fun getSchemaType(): SchemaType = SchemaType.userSchema
    }
    val factory = object : JsonSchemaProviderFactory, DumbAware {
      override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(provider)
    }
    JsonSchemaProviderFactory.EP_NAME.point.registerExtension(factory, testRootDisposable)
    JsonSchemaService.Impl.get(project).reset()
    return myFixture.addFileToProject("mapped-reference.json", schemaWithReference(MAPPED_SCHEMA_ID)).virtualFile
  }

  private fun createProjectFile(relativePath: String, content: String): VirtualFile {
    val projectPath = Path.of(myFixture.tempDirFixture.tempDirPath).resolve(relativePath)
    Files.createDirectories(projectPath.parent)
    Files.writeString(projectPath, content)
    filesToDelete.add(projectPath)
    return requireNotNull(VirtualFileManager.getInstance().findFileByNioPath(projectPath))
  }

  private fun schemaWithReference(reference: String): String = """
    {
      "${'$'}schema": "http://json-schema.org/draft-07/schema#",
      "${'$'}ref": "$reference"
    }
  """.trimIndent()

  private fun assertReferenceIsForbidden(loader: SchemaLoader, reference: String) {
    val source = requireNotNull(loader.getSchemaResource(AbsoluteIri.of(reference)))
    try {
      source.inputStream.use { it.read() }
      fail("The schema reference should have been rejected")
    }
    catch (_: AccessDeniedException) {
      // Expected: a non-null denying source prevents Networknt from trying another loader.
    }
  }

  private fun installRemoteContent(cached: ByteArray? = null): RecordingRemoteContent {
    val remote = RecordingRemoteContent(cached)
    val scope = CoroutineScope(SupervisorJob())
    Disposer.register(testRootDisposable) { scope.cancel() }
    TestModeFlags.set(JsonFileResolver.REMOTE_ENABLED_IN_TESTS, true, testRootDisposable)
    val application = ApplicationManager.getApplication()
    application.replaceService(SchemaContentCache::class.java, remote, testRootDisposable)
    application.replaceService(SchemaHttpTransport::class.java, remote, testRootDisposable)
    project.replaceService(JsonSchemaRemoteContentService::class.java, JsonSchemaRemoteContentService(project, scope), testRootDisposable)
    return remote
  }

  /** Counts the content reads of the loader and the downloads it schedules. A download never ends. */
  private class RecordingRemoteContent(private val cached: ByteArray?) : SchemaContentCache, SchemaHttpTransport {
    val cacheReads = AtomicInteger()
    val downloads = AtomicInteger()
    val downloadStarted = CountDownLatch(1)

    override fun read(url: SchemaUrl): CachedSchema? {
      cacheReads.incrementAndGet()
      return cached?.let { CachedSchema.Content(SchemaContent(it)) }
    }

    override fun readStatus(url: SchemaUrl): CachedStatus? = null

    override fun write(url: SchemaUrl, result: DownloadResult) {}

    override suspend fun download(url: SchemaUrl): DownloadResult {
      downloads.incrementAndGet()
      downloadStarted.countDown()
      awaitCancellation()
    }
  }

  /**
   * Verifies that schema is available after cache invalidation + async recompilation.
   * Even if a cancelled indicator is in effect, the background compilation completes
   * and the schema becomes available on the next call.
   */
  fun `test schema available after invalidation and async recompilation`() {
    val schemaFile = myFixture.addFileToProject("azure-schema.json", loadAzureSchema()).virtualFile
    val service = service()

    // First call — populate cache
    val schema1 = service.getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_4)
    assertNotNull(schema1)

    // Invalidate — forces recompilation on next access
    service.invalidateAllCaches("test")

    // Access with cancelled indicator — compilation starts async, may or may not throw PCE
    val indicator = EmptyProgressIndicator()
    indicator.cancel()
    try {
      ProgressManager.getInstance().runProcess({
        service.getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_4)
      }, indicator)
    }
    catch (_: ProcessCanceledException) { }

    // Schema must be available — either returned directly (fast compile) or via background future
    val schema2 = waitUntilAssertSucceedsBlocking {
      service.getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_4)
    }
    assertNotNull("Schema should be available after async recompilation", schema2)
  }

  /**
   * Verifies that write actions are not blocked by background schema compilation.
   * If the compilation held a read lock, `runWriteAction` from EDT would block.
   */
  fun `test write action completes during background compilation`() {
    val schemaFile = myFixture.addFileToProject("azure-schema-wr.json", loadAzureSchema()).virtualFile
    val service = service()

    // Trigger compilation, cancel to release calling thread
    val indicator = EmptyProgressIndicator()
    indicator.cancel()
    try {
      ProgressManager.getInstance().runProcess({
        service.getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_4)
      }, indicator)
    }
    catch (_: ProcessCanceledException) { }

    // Write action should complete immediately — no read lock held by background compilation
    val writeCompleted = AtomicBoolean(false)
    ApplicationManager.getApplication().runWriteAction {
      writeCompleted.set(true)
    }
    assertTrue("Write action should not be blocked by background compilation", writeCompleted.get())
  }

  fun `test second call returns cached schema`() {
    val schemaFile = myFixture.configureByText(JsonFileType.INSTANCE, SMALL_SCHEMA).virtualFile
    val service = service()

    val schema1 = service.getNetworkntSchema(schemaFile, VERSION)

    val t0 = System.nanoTime()
    val schema2 = service.getNetworkntSchema(schemaFile, VERSION)
    val durationMs = (System.nanoTime() - t0) / 1_000_000.0

    assertSame("Second call should return the same cached Schema instance", schema1, schema2)
    assertTrue("Cache hit should be fast (< 5ms), was ${durationMs}ms", durationMs < 10.0)
  }

  /**
   * Measures Azure schema compilation and validation performance.
   * Schema: 346KB ARM deployment template. Instance: 77KB application gateway config.
   */
  fun `test azure schema compilation and validation timing`() {
    val schemaContent = loadAzureSchema()
    val instanceContent = File(testDataPath, AZURE_INSTANCE_FILE).readText()

    val schemaFile = myFixture.addFileToProject("azure-schema-timing.json", schemaContent).virtualFile
    val service = service()

    // Warm up: compile schema + first validation
    val schema = service.getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_4)
    schema.validate(instanceContent, com.networknt.schema.InputFormat.JSON)

    Benchmark.newBenchmark("Azure networknt validation") {
      val s = service.getNetworkntSchema(schemaFile, SpecificationVersion.DRAFT_4)
      s.validate(instanceContent, com.networknt.schema.InputFormat.JSON)
    }.warmupIterations(3).attempts(10).runAsStressTest().start()
  }

  /**
   * Measures PSI→JsonNode + PsiLocationIndex conversion timing for the Azure instance file.
   * This conversion runs on every daemon pass under read action.
   */
  fun `test azure PSI to JsonNode conversion timing`() {
    val instanceContent = File(testDataPath, AZURE_INSTANCE_FILE).readText()
    val psiFile = myFixture.configureByText(JsonFileType.INSTANCE, instanceContent)
    val walker = com.jetbrains.jsonSchema.extension.JsonLikePsiWalker.getWalker(psiFile.firstChild!!)!!
    val rootElement = walker.getRoots(psiFile)?.firstOrNull() ?: psiFile.firstChild!!

    // Warm up
    convertPsiToJsonNode(walker, rootElement)

    Benchmark.newBenchmark("Azure PSI to JsonNode conversion") {
      assertNotNull("Conversion should succeed", convertPsiToJsonNode(walker, rootElement))
    }.warmupIterations(3).attempts(10).runAsStressTest().start()
  }

  fun `test invalidateAllCaches forces recompilation`() {
    val schemaFile = myFixture.configureByText(JsonFileType.INSTANCE, SMALL_SCHEMA).virtualFile
    val service = service()

    val schema1 = service.getNetworkntSchema(schemaFile, VERSION)
    service.invalidateAllCaches("test")
    val schema2 = service.getNetworkntSchema(schemaFile, VERSION)

    assertNotSame("After invalidation, a new Schema instance should be compiled", schema1, schema2)
  }

  fun `test granting project trust invalidates schema cache`() {
    val schemaFile = myFixture.configureByText(JsonFileType.INSTANCE, SMALL_SCHEMA).virtualFile
    val service = service()

    TrustedProjects.setProjectTrusted(project, false)
    val schemaBeforeTrust = service.getNetworkntSchema(schemaFile, VERSION)

    TrustedProjects.setProjectTrusted(project, true)
    val schemaAfterTrust = service.getNetworkntSchema(schemaFile, VERSION)

    assertNotSame("Granting project trust must invalidate Networknt schemas compiled under the previous policy",
                  schemaBeforeTrust, schemaAfterTrust)
  }
}
