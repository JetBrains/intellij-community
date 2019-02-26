// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.daemon.impl.hints.ModificationStampHolder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement

@Deprecated("API compatibility only, use com.intellij.codeInsight.daemon.impl.hints.ElementProcessingHintPass instead")
abstract class ElementProcessingHintPass(
  rootElement: PsiElement,
  editor: Editor,
  modificationStampHolder: ModificationStampHolder
) : com.intellij.codeInsight.daemon.impl.hints.ElementProcessingHintPass(rootElement, editor, modificationStampHolder) {
  override fun isAvailable(virtualFile: VirtualFile): Boolean {
    throw NotImplementedError()
  }

  override fun collectElementHints(element: PsiElement, collector: (offset: Int, hint: String) -> Unit) {
    throw NotImplementedError()
  }

  override fun getHintKey(): Key<Boolean> {
    throw NotImplementedError()
  }

  override fun createRenderer(text: String): HintRenderer {
    throw NotImplementedError()
  }
}

@Deprecated("API compatibility only, use com.intellij.codeInsight.daemon.impl.hints.ModificationStampHolder")
class ModificationStampHolder(key: Key<Long>): com.intellij.codeInsight.daemon.impl.hints.ModificationStampHolder(key)