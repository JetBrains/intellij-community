// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.preview

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.application.ModalityState.stateForComponent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ui.Splitter
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.ui.JBUI
import kotlin.properties.Delegates.observable

class DescriptorPreview(val splitter: Splitter, val editable: Boolean, val id: ClientId?) {

  fun editor() = editor
  fun close() = open(null)
  fun open(descriptor: OpenFileDescriptor?) {
    this.descriptor = descriptor
  }

  private var editor: Editor? by observable(null) { _, oldEditor, newEditor ->
    if (oldEditor !== newEditor) {
      oldEditor?.let { EditorFactory.getInstance().releaseEditor(it) }
      splitter.secondComponent = newEditor?.component
    }
  }

  private var descriptor: OpenFileDescriptor? by observable(null) { _, oldDescriptor, newDescriptor ->
    if (oldDescriptor !== newDescriptor) {
      val newEditor = when (newDescriptor?.file) {
        oldDescriptor?.file -> editor
        else -> newDescriptor?.preparePreviewEditor(editor).also {
          editor = it
          // validate splitter immediately to resize added editor
          // otherwise descriptor.navigateIn(editor) will not scroll properly
          splitter.validate()
        }
      }
      if (newEditor != null && newDescriptor?.rangeMarker != null) {
        getApplication().invokeLater({
                                       if (newDescriptor === descriptor && newEditor === editor) {
                                         ClientId.withClientId(id) { newDescriptor.navigateIn(newEditor) }
                                       }
                                     }, stateForComponent(splitter))
      }
    }
  }

  private fun OpenFileDescriptor.preparePreviewEditor(old: Editor?): Editor? {
    if (!file.isValid || file.isDirectory || project.isDisposed) return null
    val psi = PsiManager.getInstance(project).findFile(file) ?: return null
    val document = PsiDocumentManager.getInstance(project).getDocument(psi) ?: return null
    val reuse = old?.let { !it.isDisposed && it.document === document } ?: false
    if (reuse) return old

    val editor = when (editable) {
      true -> EditorFactory.getInstance().createEditor(document, project, EditorKind.PREVIEW)
      else -> EditorFactory.getInstance().createViewer(document, project, EditorKind.PREVIEW)
    }
    if (editor is EditorEx) {
      val scheme = EditorColorsUtil.getColorSchemeForBackground(editor.colorsScheme.defaultBackground)
      editor.colorsScheme = editor.createBoundColorSchemeDelegate(scheme)
      editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(file, scheme, project)
    }
    with(editor.settings) {
      isAnimatedScrolling = false
      isRefrainFromScrolling = false
      isLineNumbersShown = true
      isFoldingOutlineShown = false
    }
    editor.setBorder(JBUI.Borders.empty())
    return editor
  }
}
