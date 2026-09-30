// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight

import com.intellij.codeInsight.navigation.CtrlMouseActionElement
import com.intellij.codeInsight.navigation.CtrlMouseHandler2
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.model.Pointer
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.util.Collections
import java.util.stream.Collectors
import java.util.stream.Stream

class CtrlMouseHandlerTest : AbstractEditorTest() {

  fun `test highlighter disappears on mouse moving away`() {
    init("class A {}", JavaFileType.INSTANCE)
    val mouse = mouse()
    val handler = project.service<CtrlMouseHandler2>()
    mouse.ctrl().moveTo(0, 6)
    handler.handlerJob().joinPumping()
    assertHighlighted(6, 7)
    mouse.moveTo(0, 0)
    handler.handlerJob().joinPumping()
    assertHighlighted()
  }

  fun `test the action Ctrl+hover computes for is readable from the thread context`() {
    val actionIds = Collections.synchronizedList(mutableListOf<String?>())
    ExtensionTestUtil.maskExtensions(
      PsiDocumentationTargetProvider.EP_NAME,
      listOf(ActionIdReadingDocumentationTargetProvider(actionIds)),
      testRootDisposable,
    )
    init("class Abc {}", JavaFileType.INSTANCE)
    val mouse = mouse()
    val handler = project.service<CtrlMouseHandler2>()

    mouse.ctrl().moveTo(0, 6)
    handler.handlerJob().joinPumping()

    assertEquals(setOf(IdeActions.ACTION_GOTO_DECLARATION), actionIds.toSet())
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
    assertEquals("Unexpected number of highlighters", highlighterCount, highlighters.size)
    for (i in 0 until highlighterCount) {
      assertEquals("Unexpected start of " + (i + 1) + " highlighter", offsets[i * 2], highlighters[i].startOffset)
      assertEquals("Unexpected end of " + (i + 1) + " highlighter", offsets[i * 2 + 1], highlighters[i].endOffset)
    }
  }

  private fun getCurrentHighlighters(): List<RangeHighlighter> {
    val attributes = EditorColorsManager.getInstance().globalScheme.getAttributes(EditorColors.REFERENCE_HYPERLINK_COLOR)
    return Stream.of(*editor.markupModel.allHighlighters)
      .filter { h: RangeHighlighter ->
        attributes == h.getTextAttributes(null) || EditorColors.REFERENCE_HYPERLINK_COLOR == h.textAttributesKey
      }
      .sorted(RangeMarker.BY_START_OFFSET)
      .collect(Collectors.toList())
  }

  private fun Job.joinPumping() {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    UIUtil.dispatchAllInvocationEvents()
    timeoutRunBlocking {
      val job = this@joinPumping
      while (job.isActive) {
        delay(10)
        UIUtil.dispatchAllInvocationEvents()
      }
    }
  }
}
