package com.intellij.find.actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.usages.Usage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls

fun navigateAndHint(project: Project,
                    usage: Usage,
                    hint: @Nls(capitalization = Nls.Capitalization.Sentence) String,
                    parameters: ShowUsagesParameters,
                    actionHandler: ShowUsagesActionHandler,
                    onReady: Runnable) {
  project.coroutineScope.launch(Dispatchers.Main) {
    NavigationService.getInstance(project).navigate(usage, NavigationOptions.defaultOptions().requestFocus(true))
    val newEditor = getEditorFor(usage) ?: return@launch
    ShowUsagesAction.hint(false, hint, parameters.withEditor(newEditor), actionHandler)
    onReady.run()
  }
}

fun getEditorFor(usage: Usage): Editor? {
  val location = usage.location
  val newFileEditor = location?.editor
  return if (newFileEditor is TextEditor) newFileEditor.editor else null
}