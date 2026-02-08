// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.navbar.monolith

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.navbar.NavBarVmItem
import com.intellij.platform.navbar.backend.NavBarItem
import com.intellij.platform.navbar.backend.impl.IdeNavBarVmItem
import com.intellij.platform.navbar.backend.impl.ProjectNavBarItem
import com.intellij.platform.navbar.backend.impl.fireOnFileChanges
import com.intellij.platform.navbar.backend.impl.pathToItem
import com.intellij.platform.navbar.backend.impl.toVmItems
import com.intellij.platform.navbar.frontend.NavBarServiceDelegate
import com.intellij.platform.navbar.frontend.fireOnIdeActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

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
                          ?.dereference()
                        ?: return@readAction emptyList()
      contextItem
        .pathToItem()
        .toVmItems()
    }
  }

  override suspend fun navigate(item: NavBarVmItem) {
    val pointer = (item as? IdeNavBarVmItem)?.pointer ?: return
    val navigationRequest = readAction {
      pointer.dereference()?.navigationRequest()
    } ?: return

    project.serviceAsync<NavigationService>().navigate(navigationRequest)
  }
}
