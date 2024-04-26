// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit.menuBar

import com.intellij.ide.lightEdit.actions.*
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.util.NlsActions

internal fun getLightEditMainMenuActionGroup(): ActionGroup {
  val actionManager = ActionManager.getInstance()

  fun standardAction(id: String): AnAction = actionManager.getAction(id)

  val topGroup = DefaultActionGroup()
  topGroup.add(
    createActionGroup(ActionsBundle.message("group.FileMenu.text"),
                      LightEditOpenFileInProjectAction(),
                      Separator.create(),
                      LightEditNewFileAction(),
                      Separator.create(),
                      standardAction("OpenFile"),
                      LightEditRecentFileActionGroup(),
                      Separator.create(),
                      LightEditSaveAsAction(),
                      standardAction("SaveAll"),
                      Separator.create(),
                      LightEditReloadFileAction(),
                      Separator.create(),
                      LightEditExitAction()
    )
  )
  topGroup.add(
    createActionGroup(ActionsBundle.message("group.EditMenu.text"),
                      standardAction(IdeActions.ACTION_UNDO),
                      standardAction(IdeActions.ACTION_REDO),
                      Separator.create(),
                      standardAction(IdeActions.ACTION_CUT),
                      standardAction(IdeActions.ACTION_COPY),
                      standardAction(IdeActions.ACTION_PASTE),
                      standardAction(IdeActions.ACTION_DELETE),
                      Separator.create(),
                      standardAction("EditorSelectWord"),
                      standardAction("EditorUnSelectWord"),
                      standardAction(IdeActions.ACTION_SELECT_ALL)
    )
  )
  topGroup.add(
    createActionGroup(ActionsBundle.message("group.ViewMenu.text"),
                      standardAction(IdeActions.ACTION_EDITOR_USE_SOFT_WRAPS),
                      Separator.create(),
                      standardAction("EditorToggleShowWhitespaces"),
                      standardAction("EditorToggleShowLineNumbers")
    )
  )
  topGroup.add(
    createActionGroup(ActionsBundle.message("group.CodeMenu.text"),
                      standardAction(IdeActions.ACTION_EDITOR_REFORMAT))
  )
  topGroup.add(
    createActionGroup(ActionsBundle.message("group.WindowMenu.text"),
                      standardAction("NextProjectWindow"),
                      standardAction("PreviousProjectWindow"))
  )
  topGroup.add(
    createActionGroup(ActionsBundle.message("group.HelpMenu.text"),
                      standardAction("GotoAction"),
                      Separator.create(),
                      standardAction("HelpTopics"),
                      standardAction("About"))
  )
  return topGroup
}

private fun createActionGroup(title: @NlsActions.ActionText String, vararg actions: AnAction): ActionGroup {
  return DefaultActionGroup(title, actions.asList()).apply { isPopup = true }
}