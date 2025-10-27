// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.hints.declarative.*
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeIndentedBlockInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRenderer
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.codeInsight.hints.declarative.impl.views.TextInlayPresentationEntry
import com.intellij.codeInsight.multiverse.codeInsightContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase

abstract class DeclarativeInlayHintPassTestBase : LightPlatformCodeInsightFixture4TestCase() {
  fun getInlineInlays(): List<Inlay<out DeclarativeInlayRenderer>> = myFixture.editor.inlineInlays

  fun getBlockInlays(): List<Inlay<out DeclarativeIndentedBlockInlayRenderer>> = myFixture.editor.blockInlays

  fun collectAndApplyPass(pass: DeclarativeInlayHintsPass) {
    pass.collectAndApply()
  }

  fun createPass(vararg providerInfos: InlayProviderPassInfo, isProviderDisabled: Boolean = false): DeclarativeInlayHintsPass =
    createPass(myFixture.file, myFixture.editor, *providerInfos, isProviderDisabled = isProviderDisabled)

  fun runPass(vararg providerInfo: InlayProviderPassInfo, isProviderDisabled: Boolean = false) {
    collectAndApplyPass(createPass(*providerInfo, isProviderDisabled = isProviderDisabled))
  }
}

fun TextEditorHighlightingPass.collectAndApply() {
  runReadAction { doCollectInformation(DaemonProgressIndicator()) }
  applyInformationToEditor()
}

private fun createPass(
  file: PsiFile,
  editor: Editor,
  vararg providerInfos: InlayProviderPassInfo,
  isProviderDisabled: Boolean = false,
): DeclarativeInlayHintsPass {
  return ActionUtil.underModalProgress(file.project, "") {
    val pass = DeclarativeInlayHintsPass(file, editor, providerInfos.toList(), isProviderDisabled, isProviderDisabled)
    pass.setContext(file.codeInsightContext)
    pass
  }
}

fun InlayHintsProvider.toHighlightingPass(file: PsiFile, editor: Editor): DeclarativeInlayHintsPass =
  listOf("test.provider.id" to this).toHighlightingPass(file, editor)

fun Iterable<Pair<String, InlayHintsProvider>>.toHighlightingPass(file: PsiFile, editor: Editor): DeclarativeInlayHintsPass {
  val providerInfos = map { InlayProviderPassInfo(it.second, it.first, emptyMap()) }
  return createPass(file, editor, *providerInfos.toTypedArray(), isProviderDisabled = false)
}

fun Iterable<Pair<String, InlayHintsProvider>>.runPass(file: PsiFile, editor: Editor) =
  toHighlightingPass(file, editor).collectAndApply()

fun Inlay<out DeclarativeInlayRendererBase<*>>.toText(): String =
  renderer.presentationLists
    .joinToString(separator = "|") { presentationList ->
      presentationList.getEntries().joinToString(separator = "") { entry ->
        require(entry is TextInlayPresentationEntry)
        entry.text
      }
    }

val Editor.inlineInlays: List<Inlay<out DeclarativeInlayRenderer>> get() =
  inlayModel.getInlineElementsInRange(0, document.textLength - 1, DeclarativeInlayRenderer::class.java)

val Editor.blockInlays: List<Inlay<out DeclarativeIndentedBlockInlayRenderer>> get() =
  inlayModel.getBlockElementsInRange(0, document.textLength - 1, DeclarativeIndentedBlockInlayRenderer::class.java)

class StoredHintsProvider(var hintAdder: (InlayTreeSink.() -> Unit)? = null) : InlayHintsProvider {
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

open class OwnBypassStoredHintsProvider(val hintAdder: (InlayTreeSink.() -> Unit)) : InlayHintsProvider {
  final override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector {
    return object : OwnBypassCollector {
      override fun collectHintsForFile(file: PsiFile, sink: InlayTreeSink) {
        hintAdder(sink)
      }
    }
  }
}

class DumbAwareOwnBypassStoredHintsProvider(hintAdder: (InlayTreeSink.() -> Unit)) : OwnBypassStoredHintsProvider(hintAdder), DumbAware