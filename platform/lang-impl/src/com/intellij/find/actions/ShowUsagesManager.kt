// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.usages.Usage
import com.intellij.usages.impl.UsageViewImpl
import java.util.concurrent.Future

open class ShowUsagesManager {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<ShowUsagesManager>()
  }

  open fun showElementUsagesWithResult(parameters: ShowUsagesParameters,
                                       actionHandler: ShowUsagesActionHandler,
                                       usageView: UsageViewImpl): Future<Collection<Usage>> {
    return ShowUsagesAction.showElementUsagesWithResult(parameters, actionHandler, usageView)
  }
}