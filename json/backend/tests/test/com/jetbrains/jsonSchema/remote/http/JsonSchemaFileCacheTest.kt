// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote.http

import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class JsonSchemaFileCacheTest {
  private lateinit var root: java.nio.file.Path

  @BeforeEach
  fun setUp() {
    root = clearRemoteSchemaCache()
  }

  @AfterEach
  fun tearDown() {
    clearRemoteSchemaCache()
  }

  @Test
  fun testRoundTripUsesNormalizedUrlKey() {
    val cache = JsonSchemaFileCache()
    val writtenUrl = SchemaUrl.parse("HTTPS://Example.COM:443/a/../schema.json#definition")
    val readUrl = SchemaUrl.parse("https://example.com/schema.json")

    cache.write(writtenUrl, DownloadResult.Success(SchemaContent("schema".toByteArray()), null, null, writtenUrl.value))

    assertEquals("schema", String((cache.read(readUrl) as CachedSchema.Content).content.bytes))
    assertEquals(2, Files.list(root).use { it.count() })
  }

  @Test
  fun testNormalizesUnsafeSchemaStoreUrls() {
    assertEquals(
      SchemaUrl.parse("https://schemastore.azurewebsites.net/schemas/json/example.json"),
      SchemaUrl.parse("http://json.schemastore.org/example"),
    )
  }

  @Test
  fun testKeepsPercentEscapes() {
    assertEquals("https://example.com/a%20b/schema.json?x=%2F", SchemaUrl.parse("HTTPS://Example.com:443/a%20b/schema.json?x=%2F#frag").value)
    assertEquals("https://u%40v@example.com:8443/schema.json", SchemaUrl.parse("https://u%40v@example.com:8443/schema.json").value)
  }

  @Test
  fun testEncodesNonAsciiPath() {
    assertEquals("https://example.com/%C3%A4.json", SchemaUrl.parse("https://example.com/ä.json").value)
  }

  @Test
  fun testMetadataUrlMismatchIsRejected() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")
    cache.write(url, DownloadResult.Success(SchemaContent("schema".toByteArray()), null, null, url.value))
    val metadata = Files.list(root).use { files -> files.toList().single { it.fileName.toString().endsWith(".meta") } }
    Files.writeString(metadata, Files.readString(metadata).replace(url.value, "https://attacker.test/schema.json"))

    assertNull(reloaded(url).read(url))
    assertEquals(0, Files.list(root).use { it.count() })
  }

  @Test
  fun testCorruptMetadataDeletesContentPair() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")
    cache.write(url, DownloadResult.Success(SchemaContent("schema".toByteArray()), null, null, url.value))
    val metadata = Files.list(root).use { files -> files.toList().single { it.fileName.toString().endsWith(".meta") } }
    Files.writeString(metadata, "not json")

    assertNull(reloaded(url).read(url))
    assertEquals(0, Files.list(root).use { it.count() })
  }

  @Test
  fun testWritePublishesContentAndMetadataAtomically() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")

    cache.write(url, DownloadResult.Success(SchemaContent("schema".toByteArray()), "etag", "yesterday", url.value))

    assertEquals("schema", String((cache.read(url) as CachedSchema.Content).content.bytes))
    assertFalse(Files.list(root).use { files -> files.anyMatch { it.fileName.toString().endsWith(".tmp") } })
  }

  @Test
  fun testCachedErrorExpiresAfterFourHours() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/missing.json")
    cache.write(url, DownloadResult.Error(404, "Not Found"))
    assertEquals(CachedSchema.Unavailable(404, "Not Found"), cache.read(url))

    ageRemoteSchemaCache(4.hours + 1.milliseconds)

    assertNull(reloaded(url).read(url))
    assertEquals(0, Files.list(root).use { it.count() })
  }

  @Test
  fun testStaleContentRemainsAvailableForRefreshFallback() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")
    cache.write(url, DownloadResult.Success(SchemaContent("schema".toByteArray()), null, null, url.value))

    ageRemoteSchemaCache(4.hours + 1.milliseconds)

    val cached = reloaded(url).read(url) as CachedSchema.Content
    assertTrue(cached.isStale)
    assertEquals("schema", String(cached.content.bytes))
  }

  @Test
  fun testNotModifiedRefreshUpdatesTimestampWithoutReplacingContent() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")
    cache.write(url, DownloadResult.Success(SchemaContent("schema".toByteArray()), "etag", "yesterday", url.value))
    ageRemoteSchemaCache(4.hours + 1.milliseconds)
    val aged = reloaded(url)
    assertTrue((aged.read(url) as CachedSchema.Content).isStale)

    aged.refreshTimestamp(url)

    assertFalse((aged.read(url) as CachedSchema.Content).isStale, "the memory must see the refresh")
    val cached = reloaded(url).read(url) as CachedSchema.Content
    assertFalse(cached.isStale)
    assertEquals("schema", String(cached.content.bytes))
    assertEquals("etag", cached.etag)
    assertEquals("yesterday", cached.lastModified)
  }

  @Test
  fun testRetrievalUrlSurvivesTimestampRefresh() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/root.json")
    cache.write(url, DownloadResult.Success(SchemaContent("{}".toByteArray()), "etag", null,
                                            SchemaUrl.parse("https://cdn.example.com/schemas/root.yaml").value))

    cache.refreshTimestamp(url)

    assertEquals(SchemaUrl.parse("https://cdn.example.com/schemas/root.yaml").value, cache.getRetrievalUrl(url))
    assertEquals(SchemaUrl.parse("https://cdn.example.com/schemas/root.yaml").value, reloaded(url).getRetrievalUrl(url))
  }

  @Test
  fun testAdoptedFileKeepsRequestAndRetrievalUrls() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("HTTPS://Example.com:443/root.json")
    val retrievalUrl = SchemaUrl.parse("https://cdn.example.com/schemas/root.json").value
    cache.write(url, DownloadResult.Success(SchemaContent("{}".toByteArray()), null, null, retrievalUrl))

    val file = (cache.lookupFile(url) as CachedFile.Available).file
    assertEquals(retrievalUrl, file.getUserData(SchemaOrigin.URL_KEY))
    assertEquals("https://example.com/root.json", file.getUserData(SchemaOrigin.REQUEST_URL_KEY))
    assertTrue(SchemaOrigin.isCachedContentOf(file, "https://EXAMPLE.com/root.json"))

    file.putUserData(SchemaOrigin.REQUEST_URL_KEY, null)
    val reloadedFile = (reloaded(url).lookupFile(url) as CachedFile.Available).file
    assertEquals("https://example.com/root.json", reloadedFile.getUserData(SchemaOrigin.REQUEST_URL_KEY))
  }

  @Test
  fun testReadReturnsNullForUnknownUrl() {
    val cache = JsonSchemaFileCache()
    assertNull(cache.read(SchemaUrl.parse("https://example.com/unknown.json")))
  }

  @Test
  fun testLookupFileReturnsTheFileAndStaleness() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")
    cache.write(url, DownloadResult.Success(SchemaContent("{}".toByteArray()), null, null, url.value))

    val found = cache.lookupFile(url) as CachedFile.Available

    assertFalse(found.isStale)
    assertEquals("{}", String(found.file.contentsToByteArray()))
  }

  @Test
  fun testColdCacheMissesWithoutTouchingTheDiskUntilLoad() {
    val url = SchemaUrl.parse("https://example.com/schema.json")
    JsonSchemaFileCache().write(url, DownloadResult.Success(SchemaContent("schema".toByteArray()), "etag", null, url.value))
    val cold = JsonSchemaFileCache()

    runReadActionBlocking {
      assertNull(cold.read(url))
      assertNull(cold.readStatus(url))
      assertNull(cold.lookupFile(url))
    }
    assertEquals(2, Files.list(root).use { it.count() }, "a memory miss must not change the disk")

    assertTrue(cold.load(url))
    assertFalse(cold.load(url), "a second load of the same entry must not change the memory")
    runReadActionBlocking {
      assertEquals("schema", String((cold.read(url) as CachedSchema.Content).content.bytes))
      assertEquals("etag", (cold.readStatus(url) as CachedStatus.Available).etag)
      assertEquals("schema", String((cold.lookupFile(url) as CachedFile.Available).file.contentsToByteArray()))
    }
  }

  @Test
  fun testDiskOperationsRequireNoReadAccess() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")
    val success = DownloadResult.Success(SchemaContent("{}".toByteArray()), null, null, url.value)

    assertThrows(RuntimeExceptionWithAttachments::class.java) { runReadActionBlocking { cache.write(url, success) } }
    assertThrows(RuntimeExceptionWithAttachments::class.java) { runReadActionBlocking { cache.load(url) } }
    assertThrows(RuntimeExceptionWithAttachments::class.java) { runReadActionBlocking { cache.refreshTimestamp(url) } }
    assertEquals(0, Files.list(root).use { it.count() })
  }

  @Test
  fun testLookupFileMissesAfterTheAdoptedFileBecomesInvalid() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")
    cache.write(url, DownloadResult.Success(SchemaContent("{}".toByteArray()), null, null, url.value))
    val file = (cache.lookupFile(url) as CachedFile.Available).file

    Files.delete(Path.of(file.path))
    VfsUtil.markDirtyAndRefresh(false, false, false, file)

    assertFalse(file.isValid)
    assertNull(cache.lookupFile(url))
    assertNull(cache.read(url))
  }

  @Test
  fun testExtensionFollowsTheRetrievalUrlNotTheRequestUrl() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schemas/config")
    cache.write(url, DownloadResult.Success(SchemaContent("type: object".toByteArray()), null, null,
                                            "https://cdn.example.com/schemas/config.yaml"))

    val content = Files.list(root).use { files -> files.toList().single { !it.fileName.toString().endsWith(".meta") } }
    assertTrue(content.fileName.toString().endsWith(".yaml"),
               "cached YAML must keep a YAML extension, was ${content.fileName}")
    assertEquals("type: object", String((cache.read(url) as CachedSchema.Content).content.bytes))
  }

  @Test
  fun testJsonRetrievalUrlKeepsJsonExtension() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schemas/config.json")
    cache.write(url, DownloadResult.Success(SchemaContent("{}".toByteArray()), null, null, url.value))

    val content = Files.list(root).use { files -> files.toList().single { !it.fileName.toString().endsWith(".meta") } }
    assertTrue(content.fileName.toString().endsWith(".json"))
  }

  @Test
  fun testExtensionlessRetrievalUrlFallsBackToJson() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schemas/config")
    cache.write(url, DownloadResult.Success(SchemaContent("{}".toByteArray()), null, null, url.value))

    val content = Files.list(root).use { files -> files.toList().single { !it.fileName.toString().endsWith(".meta") } }
    assertTrue(content.fileName.toString().endsWith(".json"))
  }

  @Test
  fun testLegacyEntryWithoutARetrievalUrlIsRejectedEvenWhenTheNameStillMatches() {
    val url = SchemaUrl.parse("https://example.com/schemas/root.json")
    val key = sha256Hex(url.value)
    Files.writeString(root.resolve("$key.json"), "type: object")
    Files.writeString(root.resolve("$key.meta"), legacyMetadataJson(url.value))

    val cache = JsonSchemaFileCache()

    assertFalse(cache.load(url))
    assertNull(cache.lookupFile(url), "an OK entry with no retrieval URL predates the identity fix and must be refetched")
    assertEquals(0, Files.list(root).use { it.count() }, "no legacy file may be left behind")
  }

  @Test
  fun testChangingExtensionOnRefreshLeavesNoOrphan() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schemas/config")
    cache.write(url, DownloadResult.Success(SchemaContent("{}".toByteArray()), null, null, url.value))

    cache.write(url, DownloadResult.Success(SchemaContent("type: object".toByteArray()), null, null,
                                            "https://cdn.example.com/schemas/config.yaml"))

    val key = sha256Hex(url.value)
    assertTrue(Files.isRegularFile(root.resolve("$key.yaml")), "the new YAML content file must remain")
    assertFalse(Files.exists(root.resolve("$key.json")), "the old JSON content file must be removed")
    assertEquals(2, Files.list(root).use { it.count() }, "exactly one content file and one sidecar must remain")
    assertEquals("type: object", String((cache.read(url) as CachedSchema.Content).content.bytes))
  }

  @Test
  fun testOriginalUrlIsRecoveredFromSidecar() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schemas/config.yaml")
    cache.write(url, DownloadResult.Success(SchemaContent("type: object".toByteArray()), null, null,
                                            "https://cdn.example.com/schemas/config.yaml"))

    val file = (cache.lookupFile(url) as CachedFile.Available).file
    file.putUserData(SchemaOrigin.URL_KEY, null)

    assertEquals("https://cdn.example.com/schemas/config.yaml", runReadActionBlocking { cache.getOriginalUrl(file) })
    assertEquals("https://cdn.example.com/schemas/config.yaml", JsonSchemaFileCache().getOriginalUrl(file),
                 "the disk fallback must give the same URL")
  }

  @Test
  fun testLookupFileReportsStaleAfterTtl() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")
    cache.write(url, DownloadResult.Success(SchemaContent("{}".toByteArray()), null, null, url.value))

    ageRemoteSchemaCache(5.hours)

    assertTrue((reloaded(url).lookupFile(url) as CachedFile.Available).isStale)
  }

  @Test
  fun testLookupFileDistinguishesAnUnavailableEntryFromAMiss() {
    val cache = JsonSchemaFileCache()
    val cached = SchemaUrl.parse("https://example.com/gone.json")
    val never = SchemaUrl.parse("https://example.com/never-fetched.json")
    cache.write(cached, DownloadResult.Error(404, "Not Found"))

    assertEquals(404, (cache.lookupFile(cached) as CachedFile.Unavailable).code)
    assertNull(cache.lookupFile(never))
  }

  @Test
  fun testLookupFileRejectsAndReportsAMissWhenTheContentIsGone() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")
    cache.write(url, DownloadResult.Success(SchemaContent("{}".toByteArray()), null, null, url.value))
    val content = Files.list(root).use { files -> files.toList().single { !it.fileName.toString().endsWith(".meta") } }
    Files.delete(content)

    assertTrue(cache.load(url), "the load must drop the memory entry")
    assertNull(cache.lookupFile(url), "a half-written pair must be a miss, not a fresh hit")
    assertEquals(0, Files.list(root).use { it.count() }, "the orphan metadata must be cleaned up")
  }

  @Test
  fun testReadStatusReportsValidators() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")
    cache.write(url, DownloadResult.Success(SchemaContent("{}".toByteArray()), "etag-1", "yesterday", url.value))

    val status = cache.readStatus(url) as CachedStatus.Available

    assertEquals("etag-1", status.etag)
    assertEquals("yesterday", status.lastModified)
  }

  @Test
  fun testMissingContentFileWithValidMetadataIsRejectedAndCleaned() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")
    cache.write(url, DownloadResult.Success(SchemaContent("schema".toByteArray()), null, null, url.value))
    val content = Files.list(root).use { files -> files.toList().single { !it.fileName.toString().endsWith(".meta") } }
    Files.delete(content)

    assertTrue(cache.load(url))
    assertNull(cache.read(url))
    assertEquals(0, Files.list(root).use { it.count() })
  }

  @Test
  fun testDifferentUrlsProduceIndependentCacheEntries() {
    val cache = JsonSchemaFileCache()
    val url1 = SchemaUrl.parse("https://example.com/alpha.json")
    val url2 = SchemaUrl.parse("https://example.com/beta.json")
    cache.write(url1, DownloadResult.Success(SchemaContent("alpha".toByteArray()), null, null, url1.value))
    cache.write(url2, DownloadResult.Success(SchemaContent("beta".toByteArray()), null, null, url2.value))

    assertEquals("alpha", String((cache.read(url1) as CachedSchema.Content).content.bytes))
    assertEquals("beta", String((cache.read(url2) as CachedSchema.Content).content.bytes))
  }

  @Test
  fun testConcurrentWritesToSameUrlNeverExposeTornContent() {
    val cache = JsonSchemaFileCache()
    val url = SchemaUrl.parse("https://example.com/schema.json")
    val threadCount = 8
    val iterations = 20
    val executor = Executors.newFixedThreadPool(threadCount)
    val barrier = CyclicBarrier(threadCount)
    val torn = AtomicInteger()
    val tagPattern = Regex("t\\d+-i\\d+")
    try {
      val futures = (0 until threadCount).map { t ->
        executor.submit {
          barrier.await()
          for (i in 0 until iterations) {
            val tag = "t$t-i$i"
            cache.write(url, DownloadResult.Success(SchemaContent(tag.toByteArray()), tag, null, url.value))
            val cached = cache.read(url) as? CachedSchema.Content ?: continue
            if (!tagPattern.matches(String(cached.content.bytes))) torn.incrementAndGet()
          }
        }
      }
      futures.forEach { it.get(60, TimeUnit.SECONDS) }
    }
    finally {
      executor.shutdown()
    }
    assertEquals(0, torn.get())
    val content = Files.list(root).use { files -> files.toList().single { !it.fileName.toString().endsWith(".meta") } }
    assertEquals(Files.readString(content), (reloaded(url).readStatus(url) as CachedStatus.Available).etag,
                 "the metadata on the disk must match the content on the disk")
  }

  /** Builds a new cache with an empty memory and loads [url] from the disk, as after a restart. */
  private fun reloaded(url: SchemaUrl): JsonSchemaFileCache = JsonSchemaFileCache().also { it.load(url) }

  private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

  private fun legacyMetadataJson(url: String): String =
    """{"url":"$url","status":{"type":"com.jetbrains.jsonSchema.remote.http.CacheMetadata.Status.OK"},"timestamp":${Clock.System.now().toEpochMilliseconds()}}"""
}
