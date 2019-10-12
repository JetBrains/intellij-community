/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.util.FileEditorBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW
import javax.swing.KeyStroke

class DiffRequestProcessorEditor(
  private val file: DiffVirtualFile,
  private val processor: DiffRequestProcessor
) : FileEditorBase() {
  init {
    Disposer.register(processor, Disposable {
      propertyChangeSupport.firePropertyChange(FileEditor.PROP_VALID, true, false)
    })

    processor.component.registerKeyboardAction({ Disposer.dispose(this) },
                                               KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), WHEN_IN_FOCUSED_WINDOW)
  }

  override fun getComponent(): JComponent = processor.component
  override fun getPreferredFocusedComponent(): JComponent? = processor.preferredFocusedComponent

  override fun dispose() {}
  override fun isValid(): Boolean = !processor.isDisposed
  override fun getFile(): VirtualFile = file
  override fun getName(): String = "Diff"

  override fun selectNotify() {
    processor.updateRequest()
  }
}
