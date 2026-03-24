package com.intellij.codeowners.scripts.common

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils

fun foo() {

}
class UnifiedDiff private constructor(
  val path: String,
  val diff: String,
  val isEmpty: Boolean,
  val expected: String,
  val actual: String,
) {
  companion object {
    fun build(path: String, original: String, expected: String): UnifiedDiff {
      if (original == expected) {
        return UnifiedDiff(path, diff = "", isEmpty = true, expected = expected, actual = original)
      }

      val actualLines = original.lines()
      val expectedLines = expected.lines()

      val patch = DiffUtils.diff(actualLines, expectedLines)
      val diff = UnifiedDiffUtils
        .generateUnifiedDiff(path, path, actualLines, patch, 5)
        .joinToString(separator = "\n")
      return UnifiedDiff(path, diff, isEmpty = false, expected = expected, actual = original)
    }
  }
}