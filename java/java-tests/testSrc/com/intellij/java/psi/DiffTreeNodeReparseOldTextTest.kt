// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.BlockSupportImpl
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.psi.reparseFixture
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Checks the old text that `DiffTree` reads in a reparse of a node, not of the whole file. */
@TestApplication
class DiffTreeNodeReparseOldTextTest {
  private val reparseFixture = reparseFixture(projectFixture)

  @Test
  fun testNodeReparseReadsOldTextInsideOldRoot() {
    val oldText = "class A {\n  void foo() {\n    int a = 1;\n    int b = 2;\n  }\n}\n"
    val changeOffset = oldText.indexOf("2;")
    val newText = oldText.replaceRange(changeOffset, changeOffset + 1, "3")
    lateinit var file: PsiFile
    runInEdtAndWait {
      file = reparseFixture.get().createDefaultDummyFile("A.java", oldText)
    }
    val reads = mutableListOf<Int>()

    ReadAction.runBlocking<RuntimeException> {
      val result = BlockSupportImpl.reparse(file, file.node, TextRange(changeOffset, changeOffset + 1), newText,
                                            EmptyProgressIndicator(), RecordingText(oldText, 0, reads))
      val oldRootRange = result.oldRoot.textRange
      assertFalse(result.oldRoot is FileElement, "the reparse must take a node, not the file")
      assertTrue(oldRootRange.startOffset > 0, "the old root must start after the file start: $oldRootRange")
      assertTrue(reads.isNotEmpty(), "DiffTree must compare the old text")
      val outside = reads.filterNot { oldRootRange.contains(it) }
      assertTrue(outside.isEmpty()) {
        "DiffTree read the old text outside the old root $oldRootRange '${oldRootRange.substring(oldText)}' at the file offsets " +
        "$outside: '${outside.map { oldText[it] }.joinToString("")}'"
      }
    }
  }

  companion object {
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }
}

/** A text that adds the file offset of each read char to [reads]. A sub-sequence keeps the file offsets. */
private class RecordingText(private val text: CharSequence, private val fileOffset: Int, private val reads: MutableList<Int>) : CharSequence {
  override val length: Int
    get() = text.length

  override fun get(index: Int): Char {
    reads.add(fileOffset + index)
    return text[index]
  }

  override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
    RecordingText(text.subSequence(startIndex, endIndex), fileOffset + startIndex, reads)

  override fun toString(): String = text.toString()
}
