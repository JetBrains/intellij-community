// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.editor.DiffRequestProcessorEditorBase
import javax.swing.JComponent

internal class CombinedDiffEditor(file: CombinedDiffVirtualFile, private val factory: CombinedDiffComponentFactory) :
  DiffRequestProcessorEditorBase(file, factory.getMainComponent(), factory.ourDisposable, factory.model.context) {

  override fun getPreferredFocusedComponent(): JComponent? = factory.getPreferredFocusedComponent()
}
