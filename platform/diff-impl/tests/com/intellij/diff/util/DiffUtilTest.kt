/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.util

import com.intellij.diff.DiffRequestFactoryImpl
import com.intellij.diff.DiffTestCase
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.util.containers.ContainerUtil
import java.io.File
import java.util.*

class DiffUtilTest : DiffTestCase() {
  fun `test getSortedIndexes`() {
    fun <T> doTest(vararg values: T, comparator: (T, T) -> Int) {
      val list = values.toList()

      val sortedIndexes = DiffUtil.getSortedIndexes(list, comparator)
      val expected = ContainerUtil.sorted(list, comparator)
      val actual = (0..values.size - 1).map { values[sortedIndexes[it]] }

      assertOrderedEquals(expected, actual)
      assertEquals(sortedIndexes.toSet().size, list.size)
    }

    doTest(1, 2, 3, 4, 5, 6, 7, 8) { v1, v2 -> v1 - v2 }

    doTest(8, 7, 6, 5, 4, 3, 2, 1) { v1, v2 -> v1 - v2 }

    doTest(1, 3, 5, 7, 8, 6, 4, 2) { v1, v2 -> v1 - v2 }

    doTest(1, 2, 3, 4, 5, 6, 7, 8) { v1, v2 -> v2 - v1 }

    doTest(8, 7, 6, 5, 4, 3, 2, 1) { v1, v2 -> v2 - v1 }

    doTest(1, 3, 5, 7, 8, 6, 4, 2) { v1, v2 -> v2 - v1 }
  }

  fun `test merge conflict partially resolved confirmation message`() {
    fun doTest(changes: Int, conflicts: Int, expected: String) {
      val actual = DiffBundle.message("merge.dialog.apply.partially.resolved.changes.confirmation.message", changes, conflicts)
      assertTrue(actual.startsWith(expected), actual)
    }

    doTest(1, 0, "There is one change left")
    doTest(0, 1, "There is one conflict left")
    doTest(1, 1, "There is one change and one conflict left")

    doTest(2, 0, "There are 2 changes left")
    doTest(0, 2, "There are 2 conflicts left")
    doTest(2, 2, "There are 2 changes and 2 conflicts left")

    doTest(1, 2, "There is one change and 2 conflicts left")
    doTest(2, 1, "There are 2 changes and one conflict left")
    doTest(2, 3, "There are 2 changes and 3 conflicts left")
  }

  fun `test diff content titles`() {
    fun doTest(path: String, expected: String) {
      val filePath = createFilePath(path)
      val actual1 = DiffRequestFactoryImpl.getContentTitle(filePath)
      val actual2 = DiffRequestFactoryImpl.getTitle(filePath, null, " <-> ")
      val actual3 = DiffRequestFactoryImpl.getTitle(null, filePath, " <-> ")

      val expectedNative = expected.replace('/', File.separatorChar)
      assertEquals(expectedNative, actual1)
      assertEquals(expectedNative, actual2)
      assertEquals(expectedNative, actual3)
    }

    doTest("file.txt", "file.txt")
    doTest("/path/to/file.txt", "file.txt (/path/to)")
    doTest("/path/to/dir/", "/path/to/dir")
  }

  fun `test diff request titles`() {
    fun doTest(path1: String, path2: String, expected: String) {
      val filePath1 = createFilePath(path1)
      val filePath2 = createFilePath(path2)
      val actual = DiffRequestFactoryImpl.getTitle(filePath1, filePath2, " <-> ")
      assertEquals(expected.replace('/', File.separatorChar), actual)
    }

    doTest("file1.txt", "file1.txt", "file1.txt")
    doTest("/path/to/file1.txt", "/path/to/file1.txt", "file1.txt (/path/to)")
    doTest("/path/to/dir1/", "/path/to/dir1/", "/path/to/dir1")

    doTest("file1.txt", "file2.txt", "file1.txt <-> file2.txt")
    doTest("/path/to/file1.txt", "/path/to/file2.txt", "file1.txt <-> file2.txt (/path/to)")
    doTest("/path/to/dir1/", "/path/to/dir2/", "dir1 <-> dir2 (/path/to)")

    doTest("/path/to/file1.txt", "/path/to_another/file1.txt", "file1.txt (/path/to <-> /path/to_another)")
    doTest("/path/to/file1.txt", "/path/to_another/file2.txt", "file1.txt <-> file2.txt (/path/to <-> /path/to_another)")
    doTest("/path/to/dir1/", "/path/to_another/dir2/", "dir1 <-> dir2 (/path/to <-> /path/to_another)")

    doTest("file1.txt", "/path/to/file1.txt", "file1.txt <-> /path/to/file1.txt")
    doTest("file1.txt", "/path/to/file2.txt", "file1.txt <-> /path/to/file2.txt")

    doTest("/path/to/dir1/", "/path/to/file2.txt", "dir1/ <-> file2.txt (/path/to)")
    doTest("/path/to/file1.txt", "/path/to/dir2/", "file1.txt <-> dir2/ (/path/to)")
    doTest("/path/to/dir1/", "/path/to_another/file2.txt", "dir1/ <-> file2.txt (/path/to <-> /path/to_another)")
  }

  fun `test applyModification`() {
    val runs = 10000
    val textLength = 30

    doAutoTest(System.currentTimeMillis(), runs) {
      val text1 = generateText(textLength)
      val text2 = generateText(textLength)

      val lineOffsets1 = LineOffsetsUtil.create(text1)
      val lineOffsets2 = LineOffsetsUtil.create(text2)

      val fragments = MANAGER.compareLines(text1, text2, ComparisonPolicy.DEFAULT, INDICATOR)

      val ranges = fragments.map {
        Range(it.startLine1, it.endLine1, it.startLine2, it.endLine2)
      }

      val patched = DiffUtil.applyModification(text1, lineOffsets1, text2, lineOffsets2, ranges)

      val base = textToReadableFormat(text1)
      val expected = textToReadableFormat(text2)
      val actual = textToReadableFormat(patched)
      assertEquals(expected, actual, "$base\n$expected\n$actual")
    }
  }

  fun `test getLines`() {
    fun doTest(text: String, expectedLines: List<String>) {
      val document = DocumentImpl(text)
      assertEquals(expectedLines, DiffUtil.getLines(document))

      val lineOffsets = LineOffsetsUtil.create(text)
      assertEquals(expectedLines, DiffUtil.getLines(text, lineOffsets))
    }

    doTest("", listOf(""))
    doTest(" ", listOf(" "))
    doTest("\n", listOf("", ""))
    doTest("\na\n", listOf("", "a", ""))
    doTest("\na", listOf("", "a"))
    doTest("a\n\nb", listOf("a", "", "b"))
    doTest("ab\ncd", listOf("ab", "cd"))
    doTest("ab\ncd\n", listOf("ab", "cd", ""))
    doTest("\nab\ncd", listOf("", "ab", "cd"))
    doTest("\nab\ncd\n", listOf("", "ab", "cd", ""))
  }
}
