// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.ide.DataManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.usageView.UsageInfo
import com.intellij.usages.Usage
import com.intellij.usages.impl.UsageViewStatisticsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
internal class UsageNavigation(private val project: Project, private val cs: CoroutineScope) {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): UsageNavigation = project.getService(UsageNavigation::class.java)
  }

  fun navigateAndHint(
    project: Project,
    usage: Usage,
    onReady: Runnable,
    editor: Editor?,
  ) {
    cs.launch {
      val dataContext = withContext(Dispatchers.EDT) {
        editor?.let {
          DataManager.getInstance().getDataContext(it.component)
        }
      }
      NavigationService.getInstance(project).navigate(usage, NavigationOptions.requestFocus(), dataContext)
      withContext(Dispatchers.EDT) {
        writeIntentReadAction {
          onReady.run()
        }
      }
    }
  }

  fun navigate(infos: List<UsageInfo>, requestFocus: Boolean) {
    cs.launch {
      NavigationService.getInstance(project).navigateRequests(NavigationOptions.defaultOptions().requestFocus(requestFocus)) {
        readAction {
          infos.mapNotNull { info ->
            val offset = info.navigationOffset
            val project = info.project
            val file = info.virtualFile
                       ?: return@mapNotNull null
            UsageViewStatisticsCollector.logUsageNavigate(project, info)
            NavigationRequest.sourceNavigationRequest(project, file, offset)
          }
        }
      }
    }
  }
}
