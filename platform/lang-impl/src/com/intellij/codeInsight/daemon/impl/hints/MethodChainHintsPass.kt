// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.hints

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.hints.MethodChainHintsExtension
import com.intellij.codeInsight.hints.MethodChainHintsProvider
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

class MethodChainHintsPass(
  modificationStampHolder: ModificationStampHolder,
  rootElement: PsiElement,
  editor: Editor
) : ElementProcessingHintPass(rootElement, editor, modificationStampHolder) {

  private var myProvider: MethodChainHintsProvider? = null

  override fun isAvailable(virtualFile: VirtualFile): Boolean = CodeInsightSettings.getInstance().SHOW_METHOD_CHAIN_TYPES_INLINE

  override fun collectElementHints(element: PsiElement, collector: (offset: Int, hint: String) -> Unit) {
    for (hint in myProvider!!.getMethodChainHints(element, myEditor)) {
      collector.invoke(hint.offset, hint.text)
    }
  }

  override fun doCollectInformation(progress: ProgressIndicator) {
    val language = myFile.language
    myProvider = MethodChainHintsExtension.forLanguage(language)
    if (myProvider == null ||DiffUtil.isDiffEditor(myEditor)) return
    super.doCollectInformation(progress)
  }

  override fun getHintKey(): Key<Boolean> = METHOD_CHAIN_INLAY_KEY
  override fun createRenderer(text: String): HintRenderer = MethodChainHintRenderer(text)

  private class MethodChainHintRenderer(text: String) : HintRenderer(text) {
    override fun getContextMenuGroupId(inlay: Inlay<*>) = "MethodChainHintsContextMenu"
  }

  companion object {
    private val METHOD_CHAIN_INLAY_KEY = Key.create<Boolean>("METHOD_CHAIN_INLAY_KEY")
  }
}