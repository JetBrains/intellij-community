// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.ComponentManagerEx
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
  // Code below need EDT
  (project as ComponentManagerEx).getCoroutineScope().launch(Dispatchers.EDT) {
    NavigationService.getInstance(project).navigate(usage, NavigationOptions.defaultOptions().requestFocus(true))
    writeIntentReadAction {
      val newEditor = getEditorFor(usage)
      if (newEditor == null) {
        onReady.run()
        return@writeIntentReadAction
      }

      ShowUsagesAction.hint(false, hint, parameters.withEditor(newEditor), actionHandler)
      onReady.run()
    }
  }
}

fun getEditorFor(usage: Usage): Editor? {
  val location = usage.location
  val newFileEditor = location?.editor
  return if (newFileEditor is TextEditor) newFileEditor.editor else null
}