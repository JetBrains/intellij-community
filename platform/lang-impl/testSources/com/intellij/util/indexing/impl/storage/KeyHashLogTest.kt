// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage

import com.intellij.util.indexing.IdFilter
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.indexing.IdFilter.FilterScopeType
import com.intellij.util.io.EnumeratorStringDescriptor
import junit.framework.TestCase
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files

class KeyHashLogTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule: ApplicationRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val temporaryDirectory: TemporaryDirectory = TemporaryDirectory()

  @Test
  fun testAdd() {
    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      it.addKeyHashToVirtualFileMapping("qwe", 1)
      it.addKeyHashToVirtualFileMapping("qwe", 2)
      it.addKeyHashToVirtualFileMapping("asd", 2)

      val hashes = it.getSuitableKeyHashes(setOf(1, 2).toFilter(), project)
      TestCase.assertEquals(setOf("qwe", "asd").toHashes(), hashes)
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
      TestCase.assertEquals(setOf<String>().toHashes(), hashes)
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
      TestCase.assertEquals(setOf("qwe").toHashes(), hashes)
    }
  }

  @Test
  fun testIdFilterIntersection() {
    val dir = temporaryDirectory.createDir()
    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      it.addKeyHashToVirtualFileMapping("qwe", 1)

      val hashes = it.getSuitableKeyHashes(setOf(2).toFilter(), project)
      TestCase.assertEquals(setOf<String>().toHashes(), hashes)
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
      TestCase.assertEquals(setOf("qwe").toHashes(), hashes)

      val hashes1 = it.getSuitableKeyHashes(setOf(2).toFilter(), project)
      TestCase.assertEquals(setOf("qwe").toHashes(), hashes1)
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

      TestCase.assertEquals(setOf("qwe").toHashes(), hashes)
    }

    KeyHashLog(EnumeratorStringDescriptor.INSTANCE, dir.resolve("keyHashLog")).use {
      TestCase.assertFalse(it.isRequiresCompaction)
      val hashes = it.getSuitableKeyHashes(setOf(1).toFilter(), project)
      TestCase.assertEquals(setOf("qwe").toHashes(), hashes)
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
      TestCase.assertEquals(setOf("qwe").toHashes(), hashes)
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
      UsefulTestCase.assertEquals(setOf("qwe").toHashes(), resultForSubProjectFilterNotCached)

      val resultForProjectAndLibraryFilter = it.getSuitableKeyHashes(setOf(1, 2).toFilter(FilterScopeType.PROJECT_AND_LIBRARIES), project)
      UsefulTestCase.assertEquals(setOf("qwe", "asd").toHashes(), resultForProjectAndLibraryFilter)

      val resultForSubProjectFilterCached = it.getSuitableKeyHashes(setOf(1).toFilter(FilterScopeType.OTHER), project)
      UsefulTestCase.assertEquals(setOf("qwe").toHashes(), resultForSubProjectFilterCached)

      val resultForProjectFilter = it.getSuitableKeyHashes(setOf(1).toFilter(FilterScopeType.PROJECT), project)
      UsefulTestCase.assertEquals(setOf("qwe").toHashes(), resultForProjectFilter)
    }
  }

  private val project = ProjectManager.getInstance().defaultProject

  private fun Set<Int>.toFilter(type: FilterScopeType = FilterScopeType.PROJECT) : IdFilter = object : IdFilter() {
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
}