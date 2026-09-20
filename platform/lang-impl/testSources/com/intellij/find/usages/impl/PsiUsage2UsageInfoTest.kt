// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.usages.impl

import com.intellij.find.usages.api.PsiUsage
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * A text usage is anchored on the file range. Its [com.intellij.usageView.UsageInfo.getElement] reports the element
 * that spans the range, so a usage view groups the usage by its declaration and an action reads the element position.
 */
@TestApplication
class PsiUsage2UsageInfoTest {
  @RegisterExtension
  private val projectModel: ProjectModelExtension = ProjectModelExtension()

  private fun psiFile(): PsiFile = runBlocking {
    val file = projectModel.baseProjectDir.newVirtualFile("usage.txt", "alpha beta gamma".toByteArray())
    readAction { checkNotNull(PsiManager.getInstance(projectModel.project).findFile(file)) }
  }

  @Test
  fun `the element spans the usage range and the file offsets stay`() = runBlocking {
    val file = psiFile()
    val range = TextRange(6, 10)
    val info = readAction { PsiUsage2UsageInfo(PsiUsage.textUsage(file, range)) }
    readAction {
      val element = checkNotNull(info.element)
      assertFalse(element is PsiFile, "the element is the leaf at the range, not the file")
      assertTrue(element.textRange.contains(range), "the element spans the range: ${element.textRange}")
      assertEquals(range.startOffset, info.navigationOffset)
      assertEquals(range, TextRange.create(info.navigationRange))
      assertEquals(range.shiftLeft(element.textRange.startOffset), info.rangeInElement)
      assertTrue(info.isValid)
    }
  }

  @Test
  fun `an empty range keeps the file as the element`() = runBlocking {
    val file = psiFile()
    val info = readAction { PsiUsage2UsageInfo(PsiUsage.textUsage(file, TextRange(6, 6))) }
    readAction {
      assertInstanceOf<PsiFile>(info.element)
      assertEquals(6, info.navigationOffset)
    }
  }
}
