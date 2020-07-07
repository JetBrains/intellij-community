// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.ide.lightEdit.LightEditService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.TrailingSpacesStripper
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware

// class is "open" due to backward compatibility - do not extend it.
open class SaveAllAction : AnAction(), DumbAware, LightEditCompatible {
  override fun actionPerformed(e: AnActionEvent) {
    CommonDataKeys.EDITOR.getData(e.dataContext)?.let(::stripSpacesFromCaretLines)

    val project = CommonDataKeys.PROJECT.getData(e.dataContext)
    FileDocumentManager.getInstance().saveAllDocuments()
    if (LightEdit.owns(project)) {
      LightEditService.getInstance().saveNewDocuments();
    }
    (SaveAndSyncHandler.getInstance()).scheduleSave(SaveAndSyncHandler.SaveTask(onlyProject = project, forceSavingAllSettings = true, saveDocuments = false), forceExecuteImmediately = true)
  }
}

private fun stripSpacesFromCaretLines(editor: Editor) {
  val document = editor.document
  val options = TrailingSpacesStripper.getOptions(editor.document)
  if (options != null) {
    if (options.isStripTrailingSpaces && !options.isKeepTrailingSpacesOnCaretLine) {
      TrailingSpacesStripper.strip(document, options.isChangedLinesOnly, false)
    }
  }
}
