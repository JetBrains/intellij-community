// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.BrowseFolderRunnable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.ComponentValidator
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.DocumentAdapter
import com.intellij.util.UriUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

@ApiStatus.Internal
class ProjectFileStoragePathEditor(
  project: Project,
  descriptor: FileChooserDescriptor,
  pathToErrorMessage: (String) -> @NlsContexts.DialogMessage String?,
  uiDisposable: Disposable,
) {
  val component: ComboBox<String> = ComboBox<String>(JBUI.scale(500)).apply {
    isEditable = true
    val selectPathAction = BrowseFolderRunnable(project, descriptor, this, TextComponentAccessor.STRING_COMBOBOX_WHOLE_TEXT)
    initBrowsableEditor(selectPathAction, uiDisposable)
  }
  private val editorComponent = component.editor.editorComponent as JTextComponent
  private val validator = ComponentValidator(uiDisposable).withValidator {
    pathToErrorMessage(getPath())?.let { ValidationInfo(it, component) }
  }
    .andRegisterOnDocumentListener(editorComponent)
    .installOn(editorComponent)

  fun reset(path: String, pathsToSuggest: Collection<String>) {
    component.removeAllItems()
    component.selectedItem = FileUtil.toSystemDependentName(path)
    for (suggestion in pathsToSuggest) {
      component.addItem(FileUtil.toSystemDependentName(suggestion))
    }
  }

  fun getPath(): String =
    UriUtil.trimTrailingSlashes(FileUtil.toSystemIndependentName(component.editor.item.toString().trim { it <= ' ' }))

  fun addPathListener(listener: (String) -> Unit) {
    editorComponent.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        listener(getPath())
      }
    })
  }

  fun validatePath() {
    validator.revalidate()
  }

  fun clearValidation() {
    validator.updateInfo(null)
  }
}
