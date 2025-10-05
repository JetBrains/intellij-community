// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayProviderPassInfo
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeIndentedBlockInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase

abstract class DeclarativeInlayHintPassTestBase : LightPlatformCodeInsightFixture4TestCase() {
  fun getInlineInlays(): List<Inlay<out DeclarativeInlayRenderer>> {
    val editor = myFixture.editor
    return editor.inlayModel.getInlineElementsInRange(0, editor.document.textLength - 1, DeclarativeInlayRenderer::class.java)
  }

  fun getBlockInlays(): List<Inlay<out DeclarativeIndentedBlockInlayRenderer>> {
    val editor = myFixture.editor
    return editor.inlayModel.getBlockElementsInRange(0, editor.document.textLength - 1, DeclarativeIndentedBlockInlayRenderer::class.java)
  }

  fun collectAndApplyPass(pass: DeclarativeInlayHintsPass) {
    pass.doCollectInformation(DaemonProgressIndicator())
    pass.applyInformationToEditor()
  }

  fun createPass(vararg providerInfos: InlayProviderPassInfo, isProviderDisabled: Boolean = false): DeclarativeInlayHintsPass {
    return ActionUtil.underModalProgress(project, "") {
      DeclarativeInlayHintsPass(myFixture.file, myFixture.editor, providerInfos.toList(), isProviderDisabled, isProviderDisabled)
    }
  }

  fun runPass(vararg providerInfo: InlayProviderPassInfo, isProviderDisabled: Boolean = false) {
    collectAndApplyPass(createPass(*providerInfo, isProviderDisabled = isProviderDisabled))
  }

  fun Inlay<out DeclarativeInlayRendererBase<*>>.toText(): String =
    renderer.presentationLists
      .joinToString(separator = "|") { presentationList ->
        presentationList.getEntries().joinToString(separator = "") { entry ->
          require(entry is TextInlayPresentationEntry)
          entry.text
        }
      }

  class StoredHintsProvider : InlayHintsProvider {
    var hintAdder: (InlayTreeSink.() -> Unit)? = null
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
      return object : SharedBypassCollector {
        override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
          val adder = hintAdder
          if (adder != null) {
            hintAdder = null
            adder(sink)
          }
        }
      }
    }
  }
}