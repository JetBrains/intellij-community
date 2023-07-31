// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services

import com.intellij.execution.services.ServiceViewNavBarExtension.ServiceViewNavBarItem
import com.intellij.execution.services.ServiceViewNavBarService.ServiceViewNavBarSelector
import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.ide.NavBarVmImpl
import com.intellij.ide.navbar.ide.NavBarVmItem
import com.intellij.ide.navbar.ide.toVmItems
import com.intellij.ide.navbar.impl.DefaultNavBarItem
import com.intellij.ide.navbar.ui.StaticNavBarPanel
import com.intellij.ide.navbar.vm.NavBarVm
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import java.awt.BorderLayout
import java.awt.Window
import java.util.function.Supplier
import javax.swing.JComponent

internal class ServiceViewNavBarPanel(project: Project,
                                      private val cs: CoroutineScope,
                                      private val viewModel: ServiceViewModel,
                                      private val selector: ServiceViewNavBarSelector) :
  JBPanel<ServiceViewNavBarPanel>(BorderLayout()), Activatable {

  private val delegate: StaticNavBarPanel
  private val updateRequests: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val root = ServiceViewNavBarExtension.ServiceViewNavBarRoot(viewModel)
  @Volatile
  private var visible = true
  private val listeners = ArrayList<Supplier<Unit>>()

  init {
    val provider: suspend (CoroutineScope, Window, JComponent) -> NavBarVm = { scope, _, _ ->
      NavBarVmImpl(scope, getDefaultModel(), contextItems(), ::requestNavigation)
    }
    delegate = StaticNavBarPanel(project, cs, provider)
    add(delegate, BorderLayout.CENTER)

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

  fun getModel(): NavBarVm? = delegate.model

  private fun notifyListeners() {
    for (listener in listeners) {
      listener.get()
    }
  }

  private fun requestNavigation(item: NavBarVmItem) {
    cs.launch {
      val serviceItem = ((item.pointer.dereference() as? DefaultNavBarItem<*>)?.data as? ServiceViewNavBarItem)?.item ?: return@launch
      withContext(Dispatchers.EDT) {
        selector.select(serviceItem)
      }
    }
  }

  private suspend fun getDefaultModel(): List<NavBarVmItem> = readAction { getItems().toVmItems() }

  private fun contextItems(): Flow<List<NavBarVmItem>> {
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