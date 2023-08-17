// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.editor.DiffEditorBase
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

internal class CombinedDiffEditor(file: CombinedDiffVirtualFile, private val factory: CombinedDiffComponentFactory) :
  DiffEditorBase(file, factory.getMainComponent(), factory.ourDisposable, factory.model.context) {

  override fun dispose() {
    Disposer.dispose(factory.ourDisposable)
    super.dispose()
  }

  override fun getPreferredFocusedComponent(): JComponent? = factory.getPreferredFocusedComponent()
}
