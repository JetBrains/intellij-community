// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.hints

import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile

open class ModificationStampHolder(private val key: Key<Long>) {
  fun putCurrentModificationStamp(editor: Editor, file: PsiFile) {
    editor.putUserData<Long>(key, ParameterHintsPassFactory.getCurrentModificationStamp(file))
  }

  private fun forceHintsUpdateOnNextPass(editor: Editor) {
    editor.putUserData<Long>(key, null)
  }

  fun forceHintsUpdateOnNextPass() {
    EditorFactory.getInstance().allEditors.forEach { forceHintsUpdateOnNextPass(it) }
  }

  fun isNotChanged(editor: Editor, file: PsiFile): Boolean {
    return key.get(editor, 0) == ParameterHintsPassFactory.getCurrentModificationStamp(file)
  }
}