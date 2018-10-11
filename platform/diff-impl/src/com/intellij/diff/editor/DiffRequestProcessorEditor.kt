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

import com.intellij.codeHighlighting.BackgroundEditorHighlighter
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import javax.swing.JComponent

class DiffRequestProcessorEditor(
  private val project: Project,
  private val file: DiffVirtualFile,
  private val processor: DiffRequestProcessor
) : UserDataHolderBase(), FileEditor {

  init {
    Disposer.register(this, Disposable {
      Disposer.dispose(processor)
    })
    Disposer.register(processor, Disposable {
      FileEditorManager.getInstance(project).closeFile(file)
    })
  }

  override fun getComponent(): JComponent = processor.component
  override fun getPreferredFocusedComponent(): JComponent? = processor.preferredFocusedComponent
  override fun dispose() {}

  override fun isValid(): Boolean = !processor.isDisposed
  override fun getFile(): VirtualFile = file
  //
  // Unused
  //

  override fun getName(): String = "Diff"
  override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
  override fun setState(state: FileEditorState) {}
  override fun isModified(): Boolean = false
  override fun selectNotify() {}
  override fun deselectNotify() {}

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getBackgroundHighlighter(): BackgroundEditorHighlighter? = null
  override fun getCurrentLocation(): FileEditorLocation? = null
}
