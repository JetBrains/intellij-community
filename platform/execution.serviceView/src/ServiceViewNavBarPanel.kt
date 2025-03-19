// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.serviceView

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.execution.serviceView.ServiceModel.ServiceViewItem
import com.intellij.platform.execution.serviceView.ServiceViewNavBarService.ServiceViewNavBarSelector
import com.intellij.platform.navbar.NavBarItemPresentation
import com.intellij.platform.navbar.NavBarVmItem
import com.intellij.platform.navbar.frontend.ui.StaticNavBarPanel
import com.intellij.platform.navbar.frontend.vm.NavBarVm
import com.intellij.platform.navbar.frontend.vm.impl.NavBarVmImpl
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.update.Activatable
import com.intellij.util.ui.update.UiNotifyConnector
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.awt.BorderLayout
import java.awt.Component
import java.util.*

internal class ServiceViewNavBarPanel(
  project: Project,
  private val cs: CoroutineScope,
  val view: ServiceView,
  private val selector: ServiceViewNavBarSelector,
) : JBPanel<ServiceViewNavBarPanel>(BorderLayout()) {

  private val visible: StateFlow<Boolean> = trackVisibility(this)
  private val updateRequests: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val root = ServiceViewRootNavBarItem(view.model)
  private val _vm: MutableStateFlow<NavBarVm?> = MutableStateFlow(null)
  val model: NavBarVm? get() = _vm.value

  init {
    add(StaticNavBarPanel(project, _vm), BorderLayout.CENTER)
    cs.launch {
      visible.collectLatest {
        if (it) {
          handleVisible()
        }
      }
    }
  }

  private suspend fun handleVisible(): Nothing = supervisorScope {
    val vm = NavBarVmImpl(
      this@supervisorScope,
      initialItems = getItems(),
      contextItems = contextItems(),
    )
    vm.activationRequests.onEach(::requestNavigation).launchIn(this)
    _vm.value = vm
    try {
      awaitCancellation()
    }
    finally {
      _vm.value = null
    }
  }

  private fun contextItems(): Flow<List<NavBarVmItem>> {
    @OptIn(ExperimentalCoroutinesApi::class)
    return updateRequests.transformLatest {
      emit(getItems())
    }
  }

  private fun getItems(): List<NavBarVmItem> {
    val path = ArrayList<NavBarVmItem>()
    var item = selector.getSelectedItem()
    if (item != null) {
      val roots = view.model.visibleRoots
      do {
        path.add(ServiceViewNavBarItem(view.model, item!!))
        item = if (roots.contains(item)) null else item.parent
      }
      while (item != null)
    }
    path.add(root)
    path.reverse()
    return path
  }

  fun updateModel() {
    updateRequests.tryEmit(Unit)
  }

  private fun requestNavigation(item: NavBarVmItem) {
    cs.launch(Dispatchers.EDT) {
      (item as? ServiceViewNavBarItem)?.item?.let {
        selector.select(it)
      }
    }
  }
}

private fun trackVisibility(component: Component): StateFlow<Boolean> {
  val visible = MutableStateFlow(true)
  UiNotifyConnector.installOn(component, object : Activatable {

    override fun showNotify() {
      visible.value = true
    }

    override fun hideNotify() {
      visible.value = false
    }
  }, false)
  return visible
}

internal class ServiceViewRootNavBarItem(
  private val viewModel: ServiceViewModel,
) : NavBarVmItem {

  override fun equals(other: Any?): Boolean {
    return this === other || other is ServiceViewRootNavBarItem && viewModel == other.viewModel
  }

  override fun hashCode(): Int {
    return viewModel.hashCode()
  }

  override val presentation: NavBarItemPresentation = NavBarItemPresentation(
    icon = AllIcons.Nodes.Services, text = "",
  )

  override suspend fun children(): List<NavBarVmItem> {
    return viewModel.visibleRoots.map {
      ServiceViewNavBarItem(viewModel, it)
    }
  }
}

private class ServiceViewNavBarItem(
  private val viewModel: ServiceViewModel,
  val item: ServiceViewItem,
) : NavBarVmItem {

  override fun equals(other: Any?): Boolean {
    return this === other || other is ServiceViewNavBarItem && viewModel == other.viewModel && item == other.item
  }

  override fun hashCode(): Int {
    return Objects.hash(viewModel, item)
  }

  override val presentation: NavBarItemPresentation = run {
    val icon = item.getViewDescriptor().getPresentation().getIcon(false)
    val text = ServiceViewDragHelper.getDisplayName(item.getViewDescriptor().getPresentation())
    NavBarItemPresentation(icon, text)
  }

  override suspend fun children(): List<NavBarVmItem> {
    val serviceViewItem = item
    if (serviceViewItem is ServiceModel.ServiceNode) {
      if (serviceViewItem.providingContributor != null && !serviceViewItem.isChildrenInitialized) {
        viewModel.invoker.invoke(Runnable {
          serviceViewItem.getChildren() // initialize children on background thread
        })
        return emptyList()
      }
    }
    return viewModel.getChildren(serviceViewItem).map {
      ServiceViewNavBarItem(viewModel, it)
    }
  }
}
