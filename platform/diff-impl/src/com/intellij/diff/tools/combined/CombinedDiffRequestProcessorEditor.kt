// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.editor.DiffRequestProcessorEditorBase
import javax.swing.JComponent

class CombinedDiffRequestProcessorEditor(file: CombinedDiffVirtualFile<*>, private val processor: CombinedDiffRequestProcessor) :
  DiffRequestProcessorEditorBase(file, processor.component, processor.ourDisposable, processor.context) {

  override fun getPreferredFocusedComponent(): JComponent? = processor.preferedFocusedComponent
}
