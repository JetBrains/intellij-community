// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.runInDumbMode
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.testFramework.IndexingTestUtil.Companion.suspendUntilIndexesAreReady
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.*
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
class DumbDeclarativeInlayHintsPassTest {
  companion object {
    val projectFixture = projectFixture()
    val project get() = projectFixture.get()
  }
  val psiFileFixture = projectFixture.moduleFixture().sourceRootFixture().psiFileFixture("a.txt", "Hello, World!")
  val psiFile get() = psiFileFixture.get()
  val editor by psiFileFixture.editorFixture()

  @Test
  fun `non-DumbAware providers do not flicker`() = timeoutRunBlocking(context = Dispatchers.EDT) {
    suspendUntilIndexesAreReady(project)
    project.waitForSmartMode()
    var passCounter = 0
    val providers = listOf(
      "smart" to OwnBypassStoredHintsProvider {
        addPresentation(InlineInlayPosition(0, false, priority = 0), hintFormat = HintFormat.default) {
          text("smart#$passCounter")
        }
      },
      "dumb-aware" to DumbAwareOwnBypassStoredHintsProvider {
        addPresentation(InlineInlayPosition(0, false, priority = 1), hintFormat = HintFormat.default) {
          text("dumb-aware#$passCounter")
        }
      }
    )
    providers.runPass(psiFile, editor)
    passCounter++
    assertEquals(listOf("dumb-aware#0", "smart#0"), editor.inlineInlayTexts())
    DumbService.getInstance(project).runInDumbMode {
      providers.runPass(psiFile, editor)
      passCounter++
    }
    assertEquals(listOf("dumb-aware#1", "smart#0"), editor.inlineInlayTexts())
    providers.runPass(psiFile, editor)
    assertEquals(listOf("dumb-aware#2", "smart#2"), editor.inlineInlayTexts())
  }
}

fun Editor.inlineInlayTexts() = inlineInlays.map { it.toText() }

fun <T : Any> assertEquals(a: List<T>, b: List<T>) {
  assertEquals(a.size, b.size)
  a.zip(b).forEach { assertEquals(it.first, it.second) }
}