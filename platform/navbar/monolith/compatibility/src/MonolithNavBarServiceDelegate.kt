// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.monolith.compatibility

import com.intellij.codeInsight.navigation.actions.navigateRequest
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.platform.navbar.NavBarVmItem
import com.intellij.platform.navbar.backend.NavBarItem
import com.intellij.platform.navbar.backend.compatibility.IdeNavBarVmItem
import com.intellij.platform.navbar.backend.compatibility.ProjectNavBarItem
import com.intellij.platform.navbar.backend.compatibility.fireOnFileChanges
import com.intellij.platform.navbar.backend.compatibility.toVmItems
import com.intellij.platform.navbar.backend.impl.pathToItem
import com.intellij.platform.navbar.frontend.NavBarServiceDelegate
import com.intellij.platform.navbar.frontend.fireOnIdeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

internal class MonolithNavbarServiceDelegate(private val project: Project) : NavBarServiceDelegate {

  override fun activityFlow(): Flow<Unit> = channelFlow {
    fireOnIdeActivity(project)
    fireOnFileChanges(project)
    awaitClose()
  }

  override suspend fun defaultModel(): NavBarVmItem {
    return readAction {
      IdeNavBarVmItem(ProjectNavBarItem(project))
    }
  }

  override suspend fun contextModel(ctx: DataContext): List<NavBarVmItem> {
    return readAction {
      val contextItem = NavBarItem.NAVBAR_ITEM_KEY.getData(ctx)
                        ?: return@readAction emptyList()
      contextItem
        .pathToItem()
        .toVmItems()
    }
  }

  override suspend fun navigate(item: NavBarVmItem) {
    val pointer = (item as? IdeNavBarVmItem)?.pointer
                  ?: return
    val navigationRequest = readAction {
      pointer.dereference()?.navigationRequest()
    } ?: return
    withContext(Dispatchers.EDT) {
      navigateRequest(project, navigationRequest)
    }
  }
}
