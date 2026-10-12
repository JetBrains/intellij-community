// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.IdFilter.FilterScopeType
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.ResizeableMappedFile
import com.intellij.util.io.StorageLockContext
import com.intellij.util.io.keyStorage.AppendableStorageBackedByResizableMappedFile
import junit.framework.TestCase
import org.junit.Assume.assumeTrue
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.lang.ref.SoftReference
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.IntPredicate

class KeyHashLogTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule: ApplicationRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val temporaryDirectory: TemporaryDirectory = TemporaryDirectory()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Test
  fun testAdd() {
    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      it.addKeyHashToVirtualFileMapping("qwe", 1)
      it.addKeyHashToVirtualFileMapping("qwe", 2)
      it.addKeyHashToVirtualFileMapping("asd", 2)

      val hashes = it.getSuitableKeyHashes(setOf(1, 2).toFilter(), project)
      assertAcceptsExactly(setOf("qwe", "asd"), setOf("qwe", "asd"), hashes)
    }
  }

  @Test
  fun testGetSuitableKeyHashesIncludesMappingsFromAppendBuffer() {
    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use { keyHashLog ->
      val mapping = keyHashLog.getFieldValue<AppendableStorageBackedByResizableMappedFile<IntArray>>("myKeyHashToVirtualFileMapping")

      keyHashLog.addKeyHashToVirtualFileMapping("qwe", 1)

      TestCase.assertTrue("The test mapping must contain changes that were not forced explicitly", mapping.isDirty)
      TestCase.assertNotNull("The test entry must remain in the append buffer", mapping.getFieldValue<Any?>("appendBuffer"))

      val hashes = keyHashLog.getSuitableKeyHashes(setOf(1).toFilter())
      TestCase.assertEquals("The scan must include entries that it flushes from the append buffer", setOf("qwe").toHashes(), hashes)
    }
  }

  @Test
  fun testAddRemove() {
    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      it.addKeyHashToVirtualFileMapping("qwe", 1)
      it.removeKeyHashToVirtualFileMapping("qwe", 1)
      it.removeKeyHashToVirtualFileMapping("asd", 2)
      it.addKeyHashToVirtualFileMapping("zxc", 3)
      it.removeKeyHashToVirtualFileMapping("zxc", 3)

      val hashes = it.getSuitableKeyHashes(setOf(1, 2, 3).toFilter(), project)
      assertAcceptsExactly(emptySet(), setOf("qwe", "asd", "zxc"), hashes)
    }
  }

  @Test
  fun testAddRemoveAdd() {
    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      it.addKeyHashToVirtualFileMapping("qwe", 1)
      it.removeKeyHashToVirtualFileMapping("qwe", 1)
      it.addKeyHashToVirtualFileMapping("qwe", 1)

      val hashes = it.getSuitableKeyHashes(setOf(1).toFilter(), project)
      assertAcceptsExactly(setOf("qwe"), setOf("qwe"), hashes)
    }
  }

  @Test
  fun testIdFilterIntersection() {
    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      it.addKeyHashToVirtualFileMapping("qwe", 1)

      val hashes = it.getSuitableKeyHashes(setOf(2).toFilter(), project)
      assertAcceptsExactly(emptySet(), setOf("qwe"), hashes)
    }
  }

  @Test
  fun testKeyMovedToAnotherFile() {
    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      it.addKeyHashToVirtualFileMapping("qwe", 1)
      it.addKeyHashToVirtualFileMapping("qwe", 2)
      it.addKeyHashToVirtualFileMapping("qwe", -1)

      val hashes = it.getSuitableKeyHashes(setOf(2, 1).toFilter(), project)
      assertAcceptsExactly(setOf("qwe"), setOf("qwe"), hashes)

      val hashes1 = it.getSuitableKeyHashes(setOf(2).toFilter(), project)
      assertAcceptsExactly(setOf("qwe"), setOf("qwe"), hashes1)
    }
  }

  @Test
  fun testCompaction() {
    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      it.addKeyHashToVirtualFileMapping("qwe", 1)
      it.removeKeyHashToVirtualFileMapping("qwe", 1)
      it.addKeyHashToVirtualFileMapping("qwe", 1)

      TestCase.assertFalse(it.isRequiresCompaction)
      val hashes = it.getSuitableKeyHashes(setOf(1).toFilter(), project)
      TestCase.assertTrue(it.isRequiresCompaction)

      assertAcceptsExactly(setOf("qwe"), setOf("qwe"), hashes)
    }

    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      TestCase.assertFalse(it.isRequiresCompaction)
      val hashes = it.getSuitableKeyHashes(setOf(1).toFilter(), project)
      assertAcceptsExactly(setOf("qwe"), setOf("qwe"), hashes)
    }
  }

  @Test
  fun testCompactionSwapsAllFiles() {
    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      it.addKeyHashToVirtualFileMapping("qwe", 1)
      it.removeKeyHashToVirtualFileMapping("qwe", 1)
      it.addKeyHashToVirtualFileMapping("qwe", 1)

      it.getSuitableKeyHashes(setOf(1).toFilter(), project)
      TestCase.assertTrue(it.isRequiresCompaction)
    }

    val beforeCompactionMainFileBytes: ByteArray
    val beforeCompactionLenFileBytes: ByteArray

    Files.newDirectoryStream(dir).use {
      val dirMap = it.groupBy { p -> p.fileName.toString() }
      UsefulTestCase.assertSize(3, dirMap.entries)

      beforeCompactionMainFileBytes = Files.readAllBytes(dirMap["keyHashLog.project"]!!.first())
      beforeCompactionLenFileBytes = Files.readAllBytes(dirMap["keyHashLog.project.len"]!!.first())
      UsefulTestCase.assertTrue(dirMap.containsKey("keyHashLog.project.require.compaction"))
    }

    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      TestCase.assertFalse(it.isRequiresCompaction)
      val hashes = it.getSuitableKeyHashes(setOf(1).toFilter(), project)
      assertAcceptsExactly(setOf("qwe"), setOf("qwe"), hashes)
    }

    Files.newDirectoryStream(dir).use {
      val dirMap = it.groupBy { p -> p.fileName.toString() }
      UsefulTestCase.assertSize(2, dirMap.entries)

      val afterCompactionMainFileBytes = Files.readAllBytes(dirMap["keyHashLog.project"]!!.first())
      val afterCompactionLenFileBytes = Files.readAllBytes(dirMap["keyHashLog.project.len"]!!.first())

      UsefulTestCase.assertFalse(beforeCompactionMainFileBytes.contentEquals(afterCompactionMainFileBytes))
      UsefulTestCase.assertFalse(beforeCompactionLenFileBytes.contentEquals(afterCompactionLenFileBytes))
    }
  }

  @Test
  fun testDoNotCacheResultsForNonProjectFilters() {
    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      it.addKeyHashToVirtualFileMapping("qwe", 1)
      it.addKeyHashToVirtualFileMapping("asd", 2)

      val resultForSubProjectFilterNotCached = it.getSuitableKeyHashes(setOf(1).toFilter(FilterScopeType.OTHER), project)
      assertAcceptsExactly(setOf("qwe"), setOf("qwe", "asd"), resultForSubProjectFilterNotCached)

      val resultForProjectAndLibraryFilter = it.getSuitableKeyHashes(setOf(1, 2).toFilter(FilterScopeType.PROJECT_AND_LIBRARIES), project)
      assertAcceptsExactly(setOf("qwe", "asd"), setOf("qwe", "asd"), resultForProjectAndLibraryFilter)

      val resultForSubProjectFilterCached = it.getSuitableKeyHashes(setOf(1).toFilter(FilterScopeType.OTHER), project)
      assertAcceptsExactly(setOf("qwe"), setOf("qwe", "asd"), resultForSubProjectFilterCached)

      val resultForProjectFilter = it.getSuitableKeyHashes(setOf(1).toFilter(FilterScopeType.PROJECT), project)
      assertAcceptsExactly(setOf("qwe"), setOf("qwe", "asd"), resultForProjectFilter)
    }
  }

  @Test
  fun testProjectAndLibrariesCacheReadsAppendedTail() {
    assumeProjectAndLibrariesCacheEnabled()

    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use { keyHashLog ->
      val filter = setOf(1, 2).toFilter(FilterScopeType.PROJECT_AND_LIBRARIES)
      keyHashLog.addKeyHashToVirtualFileMapping("qwe", 1)
      keyHashLog.addKeyHashToVirtualFileMapping("asd", 2)

      val initialHashes = keyHashLog.getSuitableKeyHashes(filter, project)
      assertAcceptsExactly(
        setOf("qwe", "asd"),
        setOf("qwe", "asd"),
        initialHashes,
        "The initial scan must return an exact hash set",
      )

      val initialCache = keyHashLog.getCachedProjectKeyHashes()
      keyHashLog.removeKeyHashToVirtualFileMapping("qwe", 1)
      keyHashLog.addKeyHashToVirtualFileMapping("zxc", 1)

      val updatedHashes = keyHashLog.getSuitableKeyHashes(filter, project)
      assertAcceptsExactly(
        setOf("qwe", "asd", "zxc"),
        setOf("qwe", "asd", "zxc"),
        updatedHashes,
        "The incremental cache must add hashes from the tail and can retain a removed hash",
      )

      val updatedCache = keyHashLog.getCachedProjectKeyHashes()
      TestCase.assertSame("The tail scan must update the existing cache", initialCache, updatedCache)
      val removesUnaccountedFor: Int = updatedCache.getFieldValue("removesUnaccountedFor")
      TestCase.assertEquals(
        "The cache must account for one removal that it could not apply exactly",
        1,
        removesUnaccountedFor,
      )

      val mapping = keyHashLog.getFieldValue<AppendableStorageBackedByResizableMappedFile<IntArray>>("myKeyHashToVirtualFileMapping")
      val scannedUpToOffsetExclusive: Long = updatedCache.getFieldValue("scannedUpToOffsetExclusive")
      TestCase.assertEquals(
        "The cache offset must advance to the end of the appended tail",
        mapping.currentLength.toLong(),
        scannedUpToOffsetExclusive,
      )
    }
  }

  @Test
  fun testProjectAndLibrariesCacheIsRebuiltAfterProjectRootsChange() {
    assumeProjectAndLibrariesCacheEnabled()

    val rootsTracker = TestProjectRootModificationTracker()
    project.registerOrReplaceServiceInstance(ProjectRootModificationTracker::class.java, rootsTracker, disposableRule.disposable)

    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use { keyHashLog ->
      keyHashLog.addKeyHashToVirtualFileMapping("qwe", 1)
      keyHashLog.addKeyHashToVirtualFileMapping("asd", 2)

      val initialHashes = keyHashLog.getSuitableKeyHashes(setOf(1).toFilter(FilterScopeType.PROJECT_AND_LIBRARIES), project)
      assertAcceptsExactly(setOf("qwe"), setOf("qwe", "asd"), initialHashes)
      val initialCache = keyHashLog.getCachedProjectKeyHashes()

      rootsTracker.incModificationCount()

      val rebuiltHashes = keyHashLog.getSuitableKeyHashes(setOf(1, 2).toFilter(FilterScopeType.PROJECT_AND_LIBRARIES), project)
      assertAcceptsExactly(
        setOf("qwe", "asd"),
        setOf("qwe", "asd"),
        rebuiltHashes,
        "The cache must include mappings that became project mappings after the roots changed",
      )
      TestCase.assertNotSame("A roots change must replace the cache state", initialCache, keyHashLog.getCachedProjectKeyHashes())
    }
  }

  @Test
  fun testProjectAndLibrariesCacheRestoresCheckpointAfterSoftReferenceIsCleared() {
    assumeProjectAndLibrariesCacheEnabled()

    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use { keyHashLog ->
      val filter = setOf(1, 2, 3).toFilter(FilterScopeType.PROJECT_AND_LIBRARIES)
      keyHashLog.addKeyHashToVirtualFileMapping("qwe", 1)
      keyHashLog.addKeyHashToVirtualFileMapping("asd", 2)
      keyHashLog.addKeyHashToVirtualFileMapping("zxc", 3)
      keyHashLog.getSuitableKeyHashes(filter, project)

      keyHashLog.removeKeyHashToVirtualFileMapping("qwe", 1)
      keyHashLog.addKeyHashToVirtualFileMapping("new", 1)
      val updatedHashes = keyHashLog.getSuitableKeyHashes(filter, project)
      val checkedKeys = setOf("qwe", "asd", "zxc", "new")
      assertAcceptsExactly(
        checkedKeys,
        checkedKeys,
        updatedHashes,
        "The weakly actual cache must retain the removed hash",
      )

      val reference = keyHashLog.getCachedProjectKeyHashesReference()
      val cacheBeforeClear = requireNotNull(reference.get()) { "The project key hashes cache must exist before it is cleared" }
      reference.clear()

      val restoredHashes = keyHashLog.getSuitableKeyHashes(filter, project)
      assertAcceptsExactly(
        checkedKeys,
        checkedKeys,
        restoredHashes,
        "The disk checkpoint must restore the weakly actual hash set",
      )

      val cacheAfterClear = keyHashLog.getCachedProjectKeyHashes()
      TestCase.assertNotSame("A cleared soft reference must load a new cache object", cacheBeforeClear, cacheAfterClear)
      val removesUnaccountedFor: Int = cacheAfterClear.getFieldValue("removesUnaccountedFor")
      TestCase.assertEquals(
        "The disk checkpoint must restore the removal counter",
        1,
        removesUnaccountedFor,
      )
    }
  }

  @Test
  fun testParallelTailScansUseOneCacheState() {
    assumeProjectAndLibrariesCacheEnabled()

    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use { keyHashLog ->
      val filter = setOf(1).toFilter(FilterScopeType.PROJECT_AND_LIBRARIES)
      keyHashLog.addKeyHashToVirtualFileMapping("initial", 1)
      keyHashLog.getSuitableKeyHashes(filter, project)

      val appendedKeys = (0 until 500).map { "appended-$it" }
      appendedKeys.forEach { keyHashLog.addKeyHashToVirtualFileMapping(it, 1) }
      val expectedHashes = (appendedKeys + "initial").toSet().toHashes()

      val executor = Executors.newFixedThreadPool(8)
      try {
        val start = CountDownLatch(1)
        val results = List(8) {
          executor.submit<Boolean> {
            start.await()
            val predicate = keyHashLog.getSuitableKeyHashes(filter, project)
            expectedHashes.all(predicate::test) && !predicate.test("absent".hashCode())
          }
        }
        start.countDown()

        results.forEach { future ->
          TestCase.assertTrue("Each query must use one cache state", future.get(30, TimeUnit.SECONDS))
        }
      }
      finally {
        executor.shutdownNow()
        TestCase.assertTrue("The cache test executor must stop", executor.awaitTermination(30, TimeUnit.SECONDS))
      }

      keyHashLog.getCachedProjectKeyHashesReference().clear()
      val restoredPredicate = keyHashLog.getSuitableKeyHashes(filter, project)
      TestCase.assertTrue("The checkpoint must contain all expected hashes", expectedHashes.all(restoredPredicate::test))
      TestCase.assertFalse("The checkpoint must reject an absent hash", restoredPredicate.test("absent".hashCode()))
    }
  }

  @Test
  fun testVfsAwareStoragePropagatesStorageLockContextToKeyHashMapping() {
    val dir = temporaryDirectory.createDir()
    val storageLockContext = StorageLockContext(false)
    val enumeratorStringDescriptor = EnumeratorStringDescriptor.INSTANCE

    VfsAwareMapIndexStorage(
      dir.resolve("index"),
      enumeratorStringDescriptor,
      enumeratorStringDescriptor,
      16,
      false,
      true,
      storageLockContext,
    ).use { storage ->
      val keyHashLog = storage.getFieldValue<KeyHashLog<String>>("myKeyHashToVirtualFileMapping")
      val mapping = keyHashLog.getFieldValue<AppendableStorageBackedByResizableMappedFile<IntArray>>("myKeyHashToVirtualFileMapping")
      val mappedFile = mapping.getFieldValue<ResizeableMappedFile>("storage")

      TestCase.assertSame(storageLockContext, mappedFile.storageLockContext)
    }
  }

  private val project = ProjectManager.getInstance().defaultProject

  private fun assumeProjectAndLibrariesCacheEnabled() {
    assumeTrue(
      "The project and libraries cache is disabled",
      KeyHashLog.ENABLE_CACHED_HASH_IDS,
    )
  }

  private class TestProjectRootModificationTracker : ProjectRootModificationTracker() {
    private var modificationCount = 0L

    override fun getModificationCount(): Long = modificationCount

    fun incModificationCount() {
      modificationCount++
    }
  }

  private fun Set<Int>.toFilter(type: FilterScopeType = FilterScopeType.PROJECT): IdFilter = object : IdFilter() {
    override fun containsFileId(id: Int): Boolean {
      return contains(id)
    }

    override fun getFilteringScopeType(): FilterScopeType {
      return type
    }
  }

  private fun Set<String>.toHashes(): Set<Int> {
    return map { it.hashCode() }.toSet()
  }

  private fun assertAcceptsExactly(
    expectedKeys: Set<String>,
    checkedKeys: Set<String>,
    predicate: IntPredicate,
    message: String = "The predicate result must match the expected result",
  ) {
    checkedKeys.forEach { key ->
      TestCase.assertEquals("$message: $key", key in expectedKeys, predicate.test(key.hashCode()))
    }
  }

  private fun KeyHashLog<*>.getCachedProjectKeyHashes(): Any {
    return requireNotNull(getCachedProjectKeyHashesReference().get()) { "The project key hashes cache must be available" }
  }

  private fun KeyHashLog<*>.getCachedProjectKeyHashesReference(): SoftReference<*> {
    val cache = getFieldValue<Any>("myPerProjectKeyHashes")
    return cache.getFieldValue("stateRef")
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> Any.getFieldValue(fieldName: String): T {
    var currentClass: Class<*>? = javaClass
    while (currentClass != null) {
      try {
        return currentClass.getDeclaredField(fieldName).also { it.isAccessible = true }.get(this) as T
      }
      catch (_: NoSuchFieldException) {
        currentClass = currentClass.superclass
      }
    }
    throw NoSuchFieldException(fieldName)
  }
}
