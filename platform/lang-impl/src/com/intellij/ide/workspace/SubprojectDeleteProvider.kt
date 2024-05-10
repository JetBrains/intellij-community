// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.workspace

import com.intellij.CommonBundle
import com.intellij.ide.DeleteProvider
import com.intellij.ide.TitledHandler
import com.intellij.idea.ActionsBundle
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil

internal class SubprojectDeleteProvider(val selected: Collection<Subproject>) : DeleteProvider, TitledHandler {
  override fun deleteElement(dataContext: DataContext) {
    val names: String = StringUtil.join(selected.map { "'${it.name}'" }, ", ")
    val message = LangBundle.message("project.remove.confirmation.prompt", names, selected.size)
    val ret = Messages.showOkCancelDialog(message,
                                          LangBundle.message("dialog.title.remove.projects"),
                                          CommonBundle.message("button.remove"),
                                          CommonBundle.getCancelButtonText(),
                                          Messages.getQuestionIcon())
    if (ret == Messages.OK) {
      removeSubprojects(selected)
    }
  }

  override fun canDeleteElement(dataContext: DataContext) = selected.isNotEmpty()

  override fun getActionTitle(): String {
    val subproject = selected.singleOrNull()
    if (subproject != null) {
      return ActionsBundle.message("action.remove.workspace.subproject.x.text", subproject.name)
    }
    else {
      return ActionsBundle.message("action.remove.workspace.subprojects.text")
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
