// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindModel
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext

/**
 * Seeds the fixed welcome-screen Find in Files scope when the popup opens.
 *
 * @see WelcomeScreenFindScope
 */
internal class WelcomeScreenFindInProjectExtension : FindInProjectExtension {
  override fun initModelFromContext(model: FindModel, dataContext: DataContext): Boolean {
    val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return false
    if (!WelcomeScreenFindScope.isApplicable(project)) return false

    WelcomeScreenFindScope.applyTo(project, model)
    return true
  }
}
