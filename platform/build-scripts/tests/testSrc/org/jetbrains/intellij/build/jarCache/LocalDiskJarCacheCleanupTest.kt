// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jarCache

import com.dynatrace.hash4j.hashing.HashStream64
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.InMemoryContentSource
import org.jetbrains.intellij.build.Source
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

internal class LocalDiskJarCacheCleanupTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `cleanup marks and then removes stale entries`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)

      val builder = TestSourceBuilder(produceCalls = AtomicInteger(), useCacheAsTargetFile = true)
      val sources = createSources()
      manager.computeIfAbsent(
        sources = sources,
        targetFile = tempDir.resolve("out/first.jar"),
        nativeFiles = null,
        span = Span.getInvalid(),
        producer = builder,
      )

      val versionDir = findVersionDir(cacheDir)
      val entryPaths = findSingleEntryPaths(cacheDir)
      val metadataFile = entryPaths.metadataFile
      val markFile = entryPaths.markFile
      val cleanupMarkerFile = versionDir.resolve(".last.cleanup.marker")

      Files.setLastModifiedTime(metadataFile, FileTime.fromMillis(System.currentTimeMillis() - 10.days.inWholeMilliseconds))

      manager.cleanup()

      assertThat(Files.exists(entryPaths.metadataFile)).isTrue()
      assertThat(Files.exists(markFile)).isTrue()

      Files.setLastModifiedTime(cleanupMarkerFile, FileTime.fromMillis(System.currentTimeMillis() - 2.days.inWholeMilliseconds))
      manager.cleanup()

      assertThat(Files.exists(entryPaths.metadataFile)).isFalse()
      assertThat(Files.exists(entryPaths.payloadFile)).isFalse()
    }
  }

  @Test
  fun `cleanup removes entry files when entry metadata is missing`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)

      val builder = TestSourceBuilder(produceCalls = AtomicInteger(), useCacheAsTargetFile = true)
      val sources = createSources()
      manager.computeIfAbsent(
        sources = sources,
        targetFile = tempDir.resolve("out/first.jar"),
        nativeFiles = null,
        span = Span.getInvalid(),
        producer = builder,
      )

      val entryPaths = findSingleEntryPaths(cacheDir)
      val metadataFile = entryPaths.metadataFile

      Files.delete(metadataFile)
      manager.cleanup()

      assertThat(Files.exists(entryPaths.payloadFile)).isFalse()
    }
  }

  @Test
  fun `cleanup removes entry files when payload is missing even for fresh metadata`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)

      val builder = TestSourceBuilder(produceCalls = AtomicInteger(), useCacheAsTargetFile = true)
      val sources = createSources()
      manager.computeIfAbsent(
        sources = sources,
        targetFile = tempDir.resolve("out/first.jar"),
        nativeFiles = null,
        span = Span.getInvalid(),
        producer = builder,
      )

      val entryPaths = findSingleEntryPaths(cacheDir)
      Files.delete(entryPaths.payloadFile)
      Files.setLastModifiedTime(entryPaths.metadataFile, FileTime.fromMillis(System.currentTimeMillis()))

      manager.cleanup()

      assertThat(Files.exists(entryPaths.payloadFile)).isFalse()
      assertThat(Files.exists(entryPaths.metadataFile)).isFalse()
      assertThat(Files.exists(entryPaths.markFile)).isFalse()
    }
  }

  @Test
  fun `cleanup deletes stale entry even if files are in non-matching shard directory`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)

      val builder = TestSourceBuilder(produceCalls = AtomicInteger(), useCacheAsTargetFile = true)
      val sources = createSources()
      manager.computeIfAbsent(
        sources = sources,
        targetFile = tempDir.resolve("out/first.jar"),
        nativeFiles = null,
        span = Span.getInvalid(),
        producer = builder,
      )

      val versionDir = findVersionDir(cacheDir)
      val cleanupMarkerFile = versionDir.resolve(".last.cleanup.marker")
      val originalEntry = findSingleEntryPaths(cacheDir)

      val movedShardDir = versionDir.resolve("entries").resolve("zz")
      Files.createDirectories(movedShardDir)
      val movedEntry = buildEntryPathsFromStem(movedShardDir, originalEntry.entryStem)
      Files.move(originalEntry.payloadFile, movedEntry.payloadFile)
      Files.move(originalEntry.metadataFile, movedEntry.metadataFile)
      Files.setLastModifiedTime(movedEntry.metadataFile, FileTime.fromMillis(System.currentTimeMillis() - 10.days.inWholeMilliseconds))

      manager.cleanup()
      assertThat(Files.exists(movedEntry.markFile)).isTrue()

      Files.setLastModifiedTime(cleanupMarkerFile, FileTime.fromMillis(System.currentTimeMillis() - 2.days.inWholeMilliseconds))
      manager.cleanup()

      assertThat(Files.exists(movedEntry.payloadFile)).isFalse()
      assertThat(Files.exists(movedEntry.metadataFile)).isFalse()
      assertThat(Files.exists(movedEntry.markFile)).isFalse()
    }
  }

  @Test
  fun `cleanup keeps reaccessed stale entry and clears mark`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)
      val produceCalls = AtomicInteger()
      val builder = TestSourceBuilder(produceCalls = produceCalls, useCacheAsTargetFile = true)
      val sources = createSources()

      manager.computeIfAbsent(
        sources = sources,
        targetFile = tempDir.resolve("out/first.jar"),
        nativeFiles = null,
        span = Span.getInvalid(),
        producer = builder,
      )

      val versionDir = findVersionDir(cacheDir)
      val cleanupMarkerFile = versionDir.resolve(".last.cleanup.marker")
      val entryPaths = findSingleEntryPaths(cacheDir)
      val metadataFile = entryPaths.metadataFile
      val markFile = entryPaths.markFile

      Files.setLastModifiedTime(metadataFile, FileTime.fromMillis(System.currentTimeMillis() - 10.days.inWholeMilliseconds))
      manager.cleanup()
      assertThat(Files.exists(markFile)).isTrue()

      manager.computeIfAbsent(
        sources = sources,
        targetFile = tempDir.resolve("out2/first.jar"),
        nativeFiles = null,
        span = Span.getInvalid(),
        producer = builder,
      )

      Files.setLastModifiedTime(cleanupMarkerFile, FileTime.fromMillis(System.currentTimeMillis() - 2.days.inWholeMilliseconds))
      manager.cleanup()

      assertThat(Files.exists(entryPaths.payloadFile)).isTrue()
      assertThat(Files.exists(markFile)).isFalse()
      assertThat(produceCalls.get()).isEqualTo(1)
    }
  }

  @Test
  fun `reaccessed marked entry clears mark immediately and restores optimistic path`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(
        cacheDir = cacheDir,
        maxAccessTimeAge = 1.days,
        metadataTouchInterval = 1.days,
      )
      val produceCalls = AtomicInteger()
      val builder = TestSourceBuilder(produceCalls = produceCalls, useCacheAsTargetFile = false)
      val sources = createSources()

      manager.computeIfAbsent(
        sources = sources,
        targetFile = tempDir.resolve("out/first.jar"),
        nativeFiles = null,
        span = Span.getInvalid(),
        producer = builder,
      )

      val versionDir = findVersionDir(cacheDir)
      val cleanupMarkerFile = versionDir.resolve(".last.cleanup.marker")
      val entryPaths = findSingleEntryPaths(cacheDir)
      Files.setLastModifiedTime(entryPaths.metadataFile, FileTime.fromMillis(System.currentTimeMillis() - 10.days.inWholeMilliseconds))

      manager.cleanup()
      assertThat(Files.exists(entryPaths.markFile)).isTrue()

      manager.computeIfAbsent(
        sources = sources,
        targetFile = tempDir.resolve("out2/first.jar"),
        nativeFiles = null,
        span = Span.getInvalid(),
        producer = builder,
      )

      assertThat(Files.exists(entryPaths.markFile)).isFalse()

      manager.computeIfAbsent(
        sources = sources,
        targetFile = tempDir.resolve("out3/first.jar"),
        nativeFiles = null,
        span = Span.getInvalid(),
        producer = builder,
      )

      assertThat(produceCalls.get()).isEqualTo(1)

      Files.setLastModifiedTime(cleanupMarkerFile, FileTime.fromMillis(System.currentTimeMillis() - 2.days.inWholeMilliseconds))
      manager.cleanup()
      assertThat(Files.exists(entryPaths.payloadFile)).isTrue()
    }
  }

  @Test
  fun `cleanup cadence prevents immediate second-pass deletion`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)
      val builder = TestSourceBuilder(produceCalls = AtomicInteger(), useCacheAsTargetFile = true)
      val sources = createSources()

      manager.computeIfAbsent(
        sources = sources,
        targetFile = tempDir.resolve("out/first.jar"),
        nativeFiles = null,
        span = Span.getInvalid(),
        producer = builder,
      )

      val entryPaths = findSingleEntryPaths(cacheDir)
      val metadataFile = entryPaths.metadataFile
      val markFile = entryPaths.markFile
      Files.setLastModifiedTime(metadataFile, FileTime.fromMillis(System.currentTimeMillis() - 10.days.inWholeMilliseconds))

      manager.cleanup()
      assertThat(Files.exists(markFile)).isTrue()

      manager.cleanup()
      assertThat(Files.exists(entryPaths.payloadFile)).isTrue()
      assertThat(Files.exists(markFile)).isTrue()
    }
  }

  @Test
  fun `cleanup keeps stale entry when metadata touch recently failed`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)
      val builder = TestSourceBuilder(produceCalls = AtomicInteger(), useCacheAsTargetFile = true)
      val sources = createSources()

      manager.computeIfAbsent(
        sources = sources,
        targetFile = tempDir.resolve("out/first.jar"),
        nativeFiles = null,
        span = Span.getInvalid(),
        producer = builder,
      )

      val entryPaths = findSingleEntryPaths(cacheDir)
      Files.setLastModifiedTime(entryPaths.metadataFile, FileTime.fromMillis(System.currentTimeMillis() - 10.days.inWholeMilliseconds))
      registerTouchFailure(manager = manager, entryStem = entryPaths.entryStem, attemptedTouchTime = System.currentTimeMillis())

      manager.cleanup()

      assertThat(entryPaths.payloadFile).exists()
      assertThat(entryPaths.metadataFile).exists()
      assertThat(entryPaths.markFile).doesNotExist()
    }
  }

  @Test
  fun `cleanup marks stale entry when metadata touch failure is older than grace`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)
      val builder = TestSourceBuilder(produceCalls = AtomicInteger(), useCacheAsTargetFile = true)
      val sources = createSources()

      manager.computeIfAbsent(
        sources = sources,
        targetFile = tempDir.resolve("out/first.jar"),
        nativeFiles = null,
        span = Span.getInvalid(),
        producer = builder,
      )

      val entryPaths = findSingleEntryPaths(cacheDir)
      Files.setLastModifiedTime(entryPaths.metadataFile, FileTime.fromMillis(System.currentTimeMillis() - 10.days.inWholeMilliseconds))
      registerTouchFailure(
        manager = manager,
        entryStem = entryPaths.entryStem,
        attemptedTouchTime = System.currentTimeMillis() - 2.hours.inWholeMilliseconds,
      )

      manager.cleanup()

      assertThat(entryPaths.payloadFile).exists()
      assertThat(entryPaths.metadataFile).exists()
      assertThat(entryPaths.markFile).exists()
    }
  }

  @Test
  fun `cleanup keeps fresh unmarked entries intact`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)
      val versionDir = findVersionDir(cacheDir)
      val key = "ab-key"
      val entryPaths = buildEntryPathsFromStem(
        shardDir = versionDir.resolve("entries").resolve("ab"),
        entryStem = "$key${entryNameSeparatorForTests}first.jar",
      )
      val metadataFile = entryPaths.metadataFile
      val markFile = entryPaths.markFile

      Files.createDirectories(entryPaths.payloadFile.parent)
      Files.writeString(entryPaths.payloadFile, "payload")
      Files.write(metadataFile, byteArrayOf(1))
      Files.setLastModifiedTime(metadataFile, FileTime.fromMillis(System.currentTimeMillis()))

      manager.cleanup()

      assertThat(Files.exists(entryPaths.payloadFile)).isTrue()
      assertThat(Files.exists(markFile)).isFalse()
    }
  }

  @Test
  fun `cleanup removes malformed key entries`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)
      val versionDir = findVersionDir(cacheDir)
      val key = "malformedkey"
      val entryPaths = buildEntryPathsFromStem(
        shardDir = versionDir.resolve("entries").resolve("ma"),
        entryStem = "$key${entryNameSeparatorForTests}first.jar",
      )
      val metadataFile = entryPaths.metadataFile
      val markFile = entryPaths.markFile

      Files.createDirectories(entryPaths.payloadFile.parent)
      Files.writeString(entryPaths.payloadFile, "payload")
      Files.write(metadataFile, byteArrayOf(1))
      Files.setLastModifiedTime(metadataFile, FileTime.fromMillis(System.currentTimeMillis() - 10.days.inWholeMilliseconds))

      manager.cleanup()

      assertThat(Files.exists(entryPaths.payloadFile)).isFalse()
      assertThat(Files.exists(metadataFile)).isFalse()
      assertThat(Files.exists(markFile)).isFalse()
    }
  }

  @Test
  fun `cleanup removes entries with missing key prefix`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)
      val versionDir = findVersionDir(cacheDir)
      val entryPaths = buildEntryPathsFromStem(
        shardDir = versionDir.resolve("entries").resolve("aa"),
        entryStem = "${entryNameSeparatorForTests}first.jar",
      )
      val metadataFile = entryPaths.metadataFile
      val markFile = entryPaths.markFile

      Files.createDirectories(entryPaths.payloadFile.parent)
      Files.writeString(entryPaths.payloadFile, "payload")
      Files.write(metadataFile, byteArrayOf(1))
      Files.setLastModifiedTime(metadataFile, FileTime.fromMillis(System.currentTimeMillis() - 10.days.inWholeMilliseconds))

      manager.cleanup()

      assertThat(Files.exists(entryPaths.payloadFile)).isFalse()
      assertThat(Files.exists(metadataFile)).isFalse()
      assertThat(Files.exists(markFile)).isFalse()
    }
  }

  @Test
  fun `cleanup scan cursor progresses inside shard and reaches stale tail entries`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)
      val versionDir = findVersionDir(cacheDir)
      val entriesDir = versionDir.resolve("entries")
      val shardDir = entriesDir.resolve("aa")
      Files.createDirectories(shardDir)

      val freshPrefixSize = 5_000
      repeat(freshPrefixSize) { index ->
        writeEntry(
          shardDir = shardDir,
          entryStem = createEntryStemInShard(index = index),
          metadataAgeDays = 0,
        )
      }

      val staleEntryStem = createEntryStemInShard(index = freshPrefixSize)
      writeEntry(shardDir = shardDir, entryStem = staleEntryStem, metadataAgeDays = 10)
      val staleEntryPaths = buildEntryPathsFromStem(shardDir = shardDir, entryStem = staleEntryStem)
      val cleanupMarkerFile = versionDir.resolve(".last.cleanup.marker")

      var deleted = false
      repeat(6) { pass ->
        if (pass > 0 && Files.exists(cleanupMarkerFile)) {
          Files.setLastModifiedTime(cleanupMarkerFile, FileTime.fromMillis(System.currentTimeMillis() - 2.days.inWholeMilliseconds))
        }
        registerQueuedCandidates(manager = manager, seed = pass * 100_000)
        manager.cleanup()
        if (Files.notExists(staleEntryPaths.payloadFile)) {
          deleted = true
          return@repeat
        }
      }

      assertThat(deleted).isTrue()
      assertThat(staleEntryPaths.payloadFile).doesNotExist()
      assertThat(staleEntryPaths.metadataFile).doesNotExist()
      assertThat(staleEntryPaths.markFile).doesNotExist()
    }
  }

  @Test
  fun `cleanup adaptive shard scan reaches entries past fixed sixty-four shard window`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)
      val versionDir = findVersionDir(cacheDir)
      val entriesDir = versionDir.resolve("entries")

      val targetShardIndex = 69
      var targetEntryPaths: TestEntryPaths? = null
      repeat(700) { shardIndex ->
        val shardDir = entriesDir.resolve(shardIndex.toString().padStart(3, '0'))
        Files.createDirectories(shardDir)
        val entryStem = createEntryStemInShard(index = 100_000 + shardIndex)
        writeEntry(shardDir = shardDir, entryStem = entryStem, metadataAgeDays = 10)
        if (shardIndex == targetShardIndex) {
          targetEntryPaths = buildEntryPathsFromStem(shardDir = shardDir, entryStem = entryStem)
        }
      }

      manager.cleanup()

      assertThat(targetEntryPaths).isNotNull
      assertThat(targetEntryPaths!!.markFile).exists()
    }
  }

  @Test
  fun `cleanup keeps scan cursor file readable across repeated updates`() {
    runBlocking {
      val cacheDir = tempDir.resolve("cache")
      val manager = createManager(cacheDir = cacheDir, maxAccessTimeAge = 1.days)
      val versionDir = findVersionDir(cacheDir)
      val entriesDir = versionDir.resolve("entries")
      val cleanupMarkerFile = versionDir.resolve(".last.cleanup.marker")
      val scanCursorFile = versionDir.resolve(".cleanup.scan.cursor")

      repeat(3) { shardIndex ->
        val shardDir = entriesDir.resolve(shardIndex.toString().padStart(2, '0'))
        Files.createDirectories(shardDir)
        repeat(20) { entryIndex ->
          writeEntry(
            shardDir = shardDir,
            entryStem = createEntryStemInShard(index = shardIndex * 1_000 + entryIndex),
            metadataAgeDays = 10,
          )
        }
      }

      repeat(8) { pass ->
        if (pass > 0 && Files.exists(cleanupMarkerFile)) {
          Files.setLastModifiedTime(cleanupMarkerFile, FileTime.fromMillis(System.currentTimeMillis() - 2.days.inWholeMilliseconds))
        }
        manager.cleanup()

        assertThat(scanCursorFile).exists()
        val cursorValue = Files.readString(scanCursorFile).trim()
        assertThat(cursorValue).isNotBlank()

        val shardName = cursorValue.substringBefore('|')
        assertThat(shardName).isNotBlank()

        val hasEntryStem = cursorValue.contains('|')
        if (hasEntryStem) {
          assertThat(cursorValue.substringAfter('|')).isNotBlank()
        }
      }
    }
  }

  @Test
  fun `scan cursor write supports fallback move strategy`() {
    val cursorDir = tempDir.resolve("cursor")
    Files.createDirectories(cursorDir)
    val scanCursorFile = cursorDir.resolve(".cleanup.scan.cursor")
    val tempFilePrefix = "scan-cursor-fallback"
    val maintenanceClass = Class.forName("org.jetbrains.intellij.build.jarCache.LocalDiskJarCacheMaintenanceKt")
    val writeMethod = maintenanceClass.declaredMethods.single {
      it.name.startsWith("writeCleanupScanCursorAtomically") &&
      it.parameterTypes.size == 4 &&
      it.parameterTypes[0] == Path::class.java &&
      it.parameterTypes[1] == String::class.java &&
      it.parameterTypes[2] == String::class.java
    }
    writeMethod.isAccessible = true

    writeMethod.invoke(
      null,
      scanCursorFile,
      "aa|key__first.jar",
      tempFilePrefix,
      { from: Path, to: Path -> Files.move(from, to, StandardCopyOption.REPLACE_EXISTING) },
    )

    assertThat(Files.readString(scanCursorFile)).isEqualTo("aa|key__first.jar")

    val leftovers = Files.newDirectoryStream(cursorDir).use { stream ->
      stream.filterTo(mutableListOf()) {
        it.fileName.toString().contains(".tmp.$tempFilePrefix-")
      }
    }
    assertThat(leftovers).isEmpty()
  }

  private fun createManager(
    cacheDir: Path,
    maxAccessTimeAge: Duration,
    metadataTouchInterval: Duration = 15.minutes,
  ): LocalDiskJarCacheManager {
    return LocalDiskJarCacheManager(
      cacheDir = cacheDir,
      productionClassOutDir = tempDir.resolve("classes/production"),
      maxAccessTimeAge = maxAccessTimeAge,
      metadataTouchInterval = metadataTouchInterval,
    )
  }

  private fun createSources(): List<Source> {
    return listOf(InMemoryContentSource("a.txt", "hello".encodeToByteArray()))
  }

  private data class TestEntryPaths(
    @JvmField val entryStem: String,
    @JvmField val payloadFile: Path,
    @JvmField val metadataFile: Path,
    @JvmField val markFile: Path,
  )

  private val metadataFileSuffixForTests = ".meta"
  private val markedForCleanupFileSuffixForTests = ".mark"
  private val entryNameSeparatorForTests = "__"

  private fun findSingleEntryPaths(cacheDir: Path): TestEntryPaths {
    return listEntryPaths(cacheDir).single()
  }

  private fun listEntryPaths(cacheDir: Path): List<TestEntryPaths> {
    val versionDir = findVersionDir(cacheDir)
    val entriesDir = versionDir.resolve("entries")
    if (Files.notExists(entriesDir)) {
      return emptyList()
    }

    val result = mutableListOf<TestEntryPaths>()
    for (shardDir in listDirectories(entriesDir)) {
      Files.newDirectoryStream(shardDir).use { files ->
        for (file in files) {
          if (!Files.isRegularFile(file)) {
            continue
          }

          val fileName = file.fileName.toString()
          if (fileName.endsWith(metadataFileSuffixForTests) || fileName.endsWith(markedForCleanupFileSuffixForTests)) {
            continue
          }

          if (parseKeyFromEntryStem(fileName) == null) {
            continue
          }

          result.add(buildEntryPathsFromStem(shardDir, fileName))
        }
      }
    }
    return result.sortedBy { it.entryStem }
  }

  private fun buildEntryPathsFromStem(shardDir: Path, entryStem: String): TestEntryPaths {
    return TestEntryPaths(
      entryStem = entryStem,
      payloadFile = shardDir.resolve(entryStem),
      metadataFile = shardDir.resolve(entryStem + metadataFileSuffixForTests),
      markFile = shardDir.resolve(entryStem + markedForCleanupFileSuffixForTests),
    )
  }

  private fun parseKeyFromEntryStem(entryStem: String): String? {
    val separatorIndex = entryStem.indexOf(entryNameSeparatorForTests)
    if (separatorIndex <= 0) {
      return null
    }
    return entryStem.substring(0, separatorIndex)
  }

  private fun writeEntry(shardDir: Path, entryStem: String, metadataAgeDays: Int) {
    val entryPaths = buildEntryPathsFromStem(shardDir = shardDir, entryStem = entryStem)
    Files.writeString(entryPaths.payloadFile, "payload")
    Files.write(entryPaths.metadataFile, byteArrayOf(1))
    Files.setLastModifiedTime(
      entryPaths.metadataFile,
      FileTime.fromMillis(System.currentTimeMillis() - metadataAgeDays.days.inWholeMilliseconds),
    )
  }

  private fun createEntryStemInShard(index: Int): String {
    val lsb = "aa" + index.toString(Character.MAX_RADIX).padStart(6, '0')
    val msb = "m" + index.toString(Character.MAX_RADIX).padStart(6, '0')
    return "$lsb-$msb${entryNameSeparatorForTests}first.jar"
  }

  private fun registerQueuedCandidates(manager: LocalDiskJarCacheManager, seed: Int) {
    val cleanupCandidateIndexField = LocalDiskJarCacheManager::class.java.getDeclaredField("cleanupCandidateIndex")
    cleanupCandidateIndexField.isAccessible = true
    val cleanupCandidateIndex = cleanupCandidateIndexField.get(manager)
    val registerMethod = cleanupCandidateIndex.javaClass.getDeclaredMethod("register", String::class.java, String::class.java)

    repeat(45_000) { offset ->
      registerMethod.invoke(cleanupCandidateIndex, "queued${seed + offset}${entryNameSeparatorForTests}dummy.jar", "zz")
    }
  }

  private fun registerTouchFailure(manager: LocalDiskJarCacheManager, entryStem: String, attemptedTouchTime: Long) {
    val metadataTouchTrackerField = LocalDiskJarCacheManager::class.java.getDeclaredField("metadataTouchTracker")
    metadataTouchTrackerField.isAccessible = true
    val metadataTouchTracker = metadataTouchTrackerField.get(manager)
    val onTouchFailureMethod = metadataTouchTracker.javaClass.getDeclaredMethod("onTouchFailure", String::class.java, Long::class.javaPrimitiveType)
    onTouchFailureMethod.invoke(metadataTouchTracker, entryStem, attemptedTouchTime)
  }

  private fun findVersionDir(cacheDir: Path): Path {
    return listDirectories(cacheDir).single { it.fileName.toString().matches(Regex("v\\d+")) }
  }

  private fun listDirectories(parent: Path): List<Path> {
    if (Files.notExists(parent)) {
      return emptyList()
    }

    return Files.newDirectoryStream(parent).use { stream ->
      stream.filterTo(mutableListOf()) { Files.isDirectory(it) }
    }
  }

  private class TestSourceBuilder(
    private val produceCalls: AtomicInteger,
    override val useCacheAsTargetFile: Boolean,
    private val produceDelayMs: Long = 0,
  ) : SourceBuilder {
    override fun updateDigest(digest: HashStream64) {
      digest.putString("test")
    }

    override suspend fun produce(targetFile: Path) {
      produceCalls.incrementAndGet()
      if (produceDelayMs > 0) {
        delay(produceDelayMs.milliseconds)
      }
      Files.createDirectories(targetFile.parent)
      Files.writeString(targetFile, "payload")
    }

    override fun consumeInfo(source: Source, size: Int, hash: Long) {
    }
  }
}
