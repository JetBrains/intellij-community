// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services

import com.intellij.execution.services.ServiceViewNavBarExtension.ServiceViewNavBarItem
import com.intellij.execution.services.ServiceViewNavBarService.ServiceViewNavBarSelector
import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.ide.NavBarVmItem
import com.intellij.ide.navbar.ide.toVmItems
import com.intellij.ide.navbar.impl.DefaultNavBarItem
import com.intellij.ide.navbar.ui.StaticNavBarPanel
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.awt.Window
import java.util.function.Supplier

internal class ServiceViewNavBarPanel(project: Project,
                                      cs: CoroutineScope,
                                      private val updateRequests: MutableSharedFlow<Unit>,
                                      private val viewModel: ServiceViewModel,
                                      private val selector: ServiceViewNavBarSelector,
                                      requestNavigation: (NavBarVmItem) -> Unit) :
  StaticNavBarPanel(project, cs, updateRequests, requestNavigation) {
  private val root = ServiceViewNavBarExtension.ServiceViewNavBarRoot(viewModel)
  @Volatile
  private var visible = true
  private val listeners = ArrayList<Supplier<Unit>>()

  init {
    UiNotifyConnector.installOn(this, object : Activatable {
      override fun showNotify() {
        visible = true
      }

      override fun hideNotify() {
        visible = false
        notifyListeners()
      }
    }, false)

    cs.launch {
      channelFlow {
        listeners.add {
          trySend(Unit)
        }
        awaitClose()
      }
        .buffer(Channel.CONFLATED)
        .collect(updateRequests)
    }
  }

  fun updateModel() {
    if (visible) {
      notifyListeners()
    }
  }

  private fun notifyListeners() {
    for (listener in listeners) {
      listener.get()
    }
  }

  override suspend fun getDefaultModel(project: Project): List<NavBarVmItem> {
    return readAction { getItems().toVmItems() }
  }

  override fun contextItems(window: Window): Flow<List<NavBarVmItem>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    return updateRequests.transformLatest {
      val path = getItems()
      emit(readAction { path.toVmItems() })
    }
  }

  private fun getItems(): List<NavBarItem> {
    val path = ArrayList<NavBarItem>()
    var item = if (visible) selector.getSelectedItem() else null
    if (item != null) {
      val roots = viewModel.visibleRoots
      do {
        path.add(DefaultNavBarItem(ServiceViewNavBarItem(item!!, viewModel, 0)))
        item = if (roots.contains(item)) null else item.parent
      }
      while (item != null)
    }
    path.add(DefaultNavBarItem(root))
    path.reverse()
    return path
  }
}