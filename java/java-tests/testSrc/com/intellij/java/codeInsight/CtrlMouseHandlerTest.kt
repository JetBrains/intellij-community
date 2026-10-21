// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight

import com.intellij.codeInsight.navigation.CtrlMouseActionElement
import com.intellij.codeInsight.navigation.CtrlMouseHandler2
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.model.Pointer
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.editorModelFixture
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.stream.Collectors
import java.util.stream.Stream
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
class CtrlMouseHandlerTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  @Test
  fun `test highlighter disappears on mouse moving away`(): Unit = fixture.runTest {
    init("class A {}", JavaFileType.INSTANCE)
    val mouse = mouse()
    val handler = projectFixture.get().service<CtrlMouseHandler2>()
    mouse.ctrl().moveTo(0, 6)
    handler.handlerJob().joinPumping()
    assertHighlighted(6, 7)
    mouse.moveTo(0, 0)
    handler.handlerJob().joinPumping()
    assertHighlighted()
  }

  @Test
  fun `test the action Ctrl+hover computes for is readable from the thread context`(): Unit = fixture.runTest {
    val actionIds = Collections.synchronizedList(mutableListOf<String?>())
    ExtensionTestUtil.maskExtensions(
      PsiDocumentationTargetProvider.EP_NAME,
      listOf(ActionIdReadingDocumentationTargetProvider(actionIds)),
      testRootDisposable,
    )
    init("class Abc {}", JavaFileType.INSTANCE)
    val mouse = mouse()
    val handler = projectFixture.get().service<CtrlMouseHandler2>()

    mouse.ctrl().moveTo(0, 6)
    handler.handlerJob().joinPumping()

    assertEquals(setOf(IdeActions.ACTION_GOTO_DECLARATION), actionIds.toSet())
  }

  @Test
  fun `test the reference under the pointer is resolved once while the pointer stays on it`(): Unit = fixture.runTest {
    val actionIds = Collections.synchronizedList(mutableListOf<String?>())
    ExtensionTestUtil.maskExtensions(
      PsiDocumentationTargetProvider.EP_NAME,
      listOf(ActionIdReadingDocumentationTargetProvider(actionIds)),
      testRootDisposable,
    )
    init("class Abc {}", JavaFileType.INSTANCE)
    val mouse = mouse()
    val handler = projectFixture.get().service<CtrlMouseHandler2>()

    mouse.ctrl().moveTo(0, 6)
    handler.handlerJob().joinPumping()
    val resolvedOnFirstEvent = actionIds.size
    mouse.ctrl().moveTo(0, 6)
    pump()

    assertEquals(resolvedOnFirstEvent, actionIds.size,
                 "The second event was for the same offset, so it should have resolved nothing")
  }

  private class ActionIdReadingDocumentationTargetProvider(private val actionIds: MutableList<String?>) : PsiDocumentationTargetProvider {
    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget {
      // read it where a provider reads it: inside the read action that computes the ctrl-mouse data
      actionIds.add(CtrlMouseActionElement.current()?.actionId)
      return object : DocumentationTarget {
        override fun createPointer(): Pointer<out DocumentationTarget> = Pointer.hardPointer(this)
        override fun computePresentation(): TargetPresentation = TargetPresentation.builder("target").presentation()
        override fun computeDocumentationHint(): String = "hint"
      }
    }
  }

  // input parameters should have the following form:
  // firstHighlighterStartOffset, firstHighlighterEndOffset, secondHighlighterStartOffset, secondHighlighterEndOffset, ...
  private fun assertHighlighted(vararg offsets: Int) {
    assert(offsets.size % 2 == 0)
    val highlighterCount = offsets.size / 2
    val highlighters = getCurrentHighlighters()
    assertEquals(highlighterCount, highlighters.size, "Unexpected number of highlighters")
    for (i in 0 until highlighterCount) {
      assertEquals(offsets[i * 2], highlighters[i].startOffset, "Unexpected start of " + (i + 1) + " highlighter")
      assertEquals(offsets[i * 2 + 1], highlighters[i].endOffset, "Unexpected end of " + (i + 1) + " highlighter")
    }
  }

  private fun getCurrentHighlighters(): List<RangeHighlighter> {
    val attributes = EditorColorsManager.getInstance().globalScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)
    return Stream.of(*fixture.editor.markupModel.allHighlighters)
      .filter { h: RangeHighlighter ->
        attributes == h.getTextAttributes(null) || EditorColors.REFERENCE_HYPERLINK_COLOR == h.textAttributesKey
      }
      .sorted(RangeMarker.BY_START_OFFSET)
      .collect(Collectors.toList())
  }

  private fun pump() {
    timeoutRunBlocking {
      repeat(20) {
        delay(10.milliseconds)
        UIUtil.dispatchAllInvocationEvents()
      }
    }
  }

  private fun Job.joinPumping() {
    PsiDocumentManager.getInstance(projectFixture.get()).commitAllDocuments()
    UIUtil.dispatchAllInvocationEvents()
    timeoutRunBlocking {
      val job = this@joinPumping
      while (job.isActive) {
        delay(10.milliseconds)
        UIUtil.dispatchAllInvocationEvents()
      }
    }
  }
}
