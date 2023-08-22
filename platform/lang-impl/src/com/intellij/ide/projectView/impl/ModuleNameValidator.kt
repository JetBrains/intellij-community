// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.util.NlsContexts

class ModuleNameValidator(project: Project) : InputValidatorEx {
  private val moduleModel = ModuleManager.getInstance(project)

  override fun getErrorText(inputString: String?): @NlsContexts.DetailedDescription String? {
    if (inputString.isNullOrEmpty()) return IdeBundle.message("error.name.cannot.be.empty")
    if (moduleModel.findModuleByName(inputString) != null) return IdeBundle.message("error.module.already.exists", inputString)
    return null
  }
}