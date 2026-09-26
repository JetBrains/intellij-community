// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl.storage

import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.IntPair
import com.intellij.util.indexing.IdFilter
import junit.framework.TestCase
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class IntLogTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule: ApplicationRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val temporaryDirectory: TemporaryDirectory = TemporaryDirectory()

  @Test
  fun testCompaction() {
    val dir = temporaryDirectory.createDir()
    IntLog(dir.resolve("keyHashLog"), true).use {
      it.addData(1, 123)
      it.addData(0, 123)
      it.addData(2, 456)
      TestCase.assertEquals(data(1 to 123, 0 to 123, 2 to 456), it.collectData())
      TestCase.assertFalse(it.isRequiresCompaction())

      it.addData(0, 456)
      TestCase.assertEquals(data(1 to 123, 0 to 123, 2 to 456, 0 to 456), it.collectData())
      TestCase.assertTrue(it.isRequiresCompaction())

      it.addData(3, 789)
    }

    IntLog(dir.resolve("keyHashLog"), true).use {
      TestCase.assertFalse(it.isRequiresCompaction())
      TestCase.assertEquals(data(3 to 789), it.collectData())
    }
  }

  private fun data(vararg pairs: IntPair): List<IntPair> = pairs.toList()

  private infix fun Int.to(inputId: Int) : IntPair = IntPair(this, inputId)

  private fun IntLog.collectData(): List<IntPair> {
    val result = mutableListOf<IntPair>()
    this.processEntries(AbstractIntLog.IntLogEntryProcessor { data, inputId ->
      result.add(IntPair(data, inputId))
      true
    })
    return result
  }

  private val project = ProjectManager.getInstance().defaultProject

  private fun Set<Int>.toFilter() : IdFilter = object : IdFilter() {
    override fun containsFileId(id: Int): Boolean {
      return contains(id)
    }

    override fun getFilteringScopeType(): FilterScopeType {
      return FilterScopeType.PROJECT
    }
  }

  private fun Set<String>.toHashes(): Set<Int> {
    return map { it.hashCode() }.toSet()
  }
}