// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.application.options.RegistryManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.codeStyle.CodeStyleSchemes


private const val REGISTRY_KEY = "editor.visual.formatting.layer.enabled"

abstract class VisualFormattingLayerService : EditorFactoryListener, DocumentListener, Disposable {

  override fun dispose() = Unit

  val editorFactory: EditorFactory by lazy { EditorFactory.getInstance() }

  val enabledByRegistry: Boolean
    get() = RegistryManager.getInstance().`is`(REGISTRY_KEY)

  val enabledBySettings: Boolean
    get() = UISettings.instance.showVisualFormattingLayer

  var enabledGlobally: Boolean = false

  var scheme: CodeStyleScheme
    get() = with(CodeStyleSchemes.getInstance()) {
      allSchemes.find { it.isUsedForVisualFormatting } ?: defaultScheme
    }
    set(scheme) = with(CodeStyleSchemes.getInstance()) {
      allSchemes.forEach { it.isUsedForVisualFormatting = false }
      scheme.isUsedForVisualFormatting = true
    }

  fun getSchemes(): List<CodeStyleScheme> = CodeStyleSchemes.getInstance().allSchemes

  val disabledGlobally: Boolean
    get() = !enabledGlobally

  fun enabledForEditor(editor: Editor) =
    editor.settings.isShowVisualFormattingLayer ?: enabledGlobally

  fun disabledForEditor(editor: Editor) =
    !enabledForEditor(editor)

  fun enableForEditor(editor: Editor) {
    if (disabledForEditor(editor)) {
      if (enabledGlobally) {
        editor.settings.isShowVisualFormattingLayer = null
      }
      else {
        editor.settings.isShowVisualFormattingLayer = true
      }
      editor.document.addDocumentListener(this)
      editor.addVisualElements()
    }
  }

  fun disableForEditor(editor: Editor) {
    if (enabledForEditor(editor)) {
      if (disabledGlobally)
        editor.settings.isShowVisualFormattingLayer = null
      else {
        editor.settings.isShowVisualFormattingLayer = false
      }
      editor.document.removeDocumentListener(this)
      editor.removeVisualElements()
    }
  }


  //------------------------------
  // Global stuff
  fun enableGlobally() {
    editorFactory.allEditors.forEach { editor ->
      enableForEditor(editor)
      editor.settings.isShowVisualFormattingLayer = null
    }
    enabledGlobally = true
  }

  fun disableGlobally() {
    editorFactory.allEditors.forEach { editor ->
      disableForEditor(editor)
      editor.settings.isShowVisualFormattingLayer = null
    }
    enabledGlobally = false
  }
  //------------------------------


  //------------------------------
  // Editor Factory listener stuff
  fun addEditorFactoryListener() {
    editorFactory.addEditorFactoryListener(this, this)
  }

  override fun editorCreated(event: EditorFactoryEvent) {
    if (enabledGlobally) {
      event.editor.document.addDocumentListener(this)
      event.editor.addVisualElements()
    }
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    if (enabledForEditor(event.editor)) {
      event.editor.document.removeDocumentListener(this)
      event.editor.removeVisualElements()
    }
  }
  //------------------------------


  //------------------------------
  // Document listener stuff
  override fun documentChanged(event: DocumentEvent) {
    EditorFactory.getInstance()
      .getEditors(event.document)
      .forEach { editor ->
        disableForEditor(editor)
      }
  }
  //------------------------------

  abstract fun Editor.addVisualElements()
  abstract fun Editor.removeVisualElements()

  fun Editor.refreshVisualElements() {
    removeVisualElements()
    if (enabledForEditor(this)) {
      addVisualElements()
    }
  }

  fun refreshGlobally() {
    if (enabledBySettings != enabledGlobally) {
      if (enabledBySettings) {
        enableGlobally()
      }
      else {
        disableGlobally()
      }
    }
    EditorFactory.getInstance().allEditors.forEach { editor ->
      editor.refreshVisualElements()
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): VisualFormattingLayerService =
      ApplicationManager.getApplication().getService(VisualFormattingLayerService::class.java)
  }

}
