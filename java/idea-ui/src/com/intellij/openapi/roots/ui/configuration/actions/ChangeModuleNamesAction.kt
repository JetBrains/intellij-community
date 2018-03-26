// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.configuration.actions

import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.isQualifiedModuleNamesEnabled
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.util.PathUtilRt

class ChangeModuleNamesAction : DumbAwareAction(ProjectBundle.message("action.text.change.module.names"),
                                                ProjectBundle.message("action.description.change.module.names"), null) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isVisible = project != null && isQualifiedModuleNamesEnabled(project) && e.getData(LangDataKeys.MODIFIABLE_MODULE_MODEL) != null
    val modules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY)
    e.presentation.isEnabled = modules != null && modules.size > 1
  }

  override fun actionPerformed(e: AnActionEvent) {
    val modules = e.getData(LangDataKeys.MODULE_CONTEXT_ARRAY) ?: return
    val model = e.getData(LangDataKeys.MODIFIABLE_MODULE_MODEL) ?: return

    fun getGroupName(module: Module) = model.getActualName(module).substringBeforeLast('.') + "."

    val commonPrefix = modules.fold(getGroupName(modules[0])) { prefix, m -> prefix.commonPrefixWith(getGroupName(m)) }
                              .substringBeforeLast('.', "")
    val isPrefixEqualToModuleName = modules.any { model.getActualName(it) == commonPrefix }
    val validator = object : InputValidatorEx {
      override fun getErrorText(inputString: String): String? {
        if (inputString.isNotEmpty() && inputString.split('.').any { it.isEmpty() }) {
          return ProjectBundle.message("error.message.module.name.prefix.contains.empty.string")
        }
        if (isPrefixEqualToModuleName && inputString.isEmpty()) {
          return ProjectBundle.message("error.message.module.name.cannot.be.empty")
        }
        if (!PathUtilRt.isValidFileName(inputString + ModuleFileType.DOT_DEFAULT_EXTENSION, true)) {
          return ProjectBundle.message("error.message.module.name.prefix.contains.invalid.chars")
        }
        return null
      }

      override fun checkInput(inputString: String) = getErrorText(inputString) == null

      override fun canClose(inputString: String) = getErrorText(inputString) == null
    }

    val newPrefix = Messages.showInputDialog(e.project, ProjectBundle.message("dialog.text.enter.common.prefix", modules.size),
                                             ProjectBundle.message("dialog.title.change.module.names"), null, commonPrefix, validator,
                                             TextRange.allOf(commonPrefix), ProjectBundle.message("dialog.text.enter.common.prefix.comment"))
    if (newPrefix == null) return

    val prefixToRemove = if (commonPrefix.isEmpty()) "" else "$commonPrefix."
    val prefixToPrepend = if (newPrefix.isEmpty() || newPrefix.endsWith('.')) newPrefix else "$newPrefix."

    modules.forEach {
      val oldName = model.getActualName(it)
      val newName = if (oldName == commonPrefix) newPrefix else prefixToPrepend + oldName.removePrefix(prefixToRemove)
      model.renameModule(it, newName)
    }
    ProjectSettingsService.getInstance(e.project).processModulesMoved(modules, null)
  }
}