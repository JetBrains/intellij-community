// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.application.pooledThreadContext
import com.intellij.configurationStore.saveDocumentsAndProjectsAndApp
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.TrailingSpacesStripper
import com.intellij.openapi.project.DumbAware
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// class is "open" due to backward compatibility - do not extend it.
open class SaveAllAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    CommonDataKeys.EDITOR.getData(e.dataContext)?.let(::stripSpacesFromCaretLines)

    GlobalScope.launch(pooledThreadContext) {
      saveDocumentsAndProjectsAndApp(onlyProject = CommonDataKeys.PROJECT.getData(e.dataContext),
                                     isForceSavingAllSettings = true)
    }
  }
}

private fun stripSpacesFromCaretLines(editor: Editor) {
  val editorSettings = EditorSettingsExternalizable.getInstance()
  if (EditorSettingsExternalizable.STRIP_TRAILING_SPACES_NONE != editorSettings.stripTrailingSpaces && !editorSettings.isKeepTrailingSpacesOnCaretLine) {
    val document = editor.document
    val inChangedLinesOnly = EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED == editorSettings.stripTrailingSpaces
    TrailingSpacesStripper.strip(document, inChangedLinesOnly, false)
  }
}
