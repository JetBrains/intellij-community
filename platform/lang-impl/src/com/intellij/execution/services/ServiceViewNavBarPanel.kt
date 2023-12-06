// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.services

import com.intellij.execution.services.ServiceModel.ServiceViewItem
import com.intellij.execution.services.ServiceViewNavBarService.ServiceViewNavBarSelector
import com.intellij.icons.AllIcons
import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.NavBarItemPresentation
import com.intellij.ide.navbar.ide.NavBarVmImpl
import com.intellij.ide.navbar.ide.NavBarVmItem
import com.intellij.ide.navbar.ui.StaticNavBarPanel
import com.intellij.ide.navbar.vm.NavBarVm
import com.intellij.model.Pointer
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
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
  private val viewModel: ServiceViewModel,
  private val selector: ServiceViewNavBarSelector,
) : JBPanel<ServiceViewNavBarPanel>(BorderLayout()) {

  private val visible: StateFlow<Boolean> = trackVisibility(this)
  private val updateRequests: MutableSharedFlow<Unit> = MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private val root = ServiceViewRootNavBarItem(viewModel)
  private val _vm: MutableStateFlow<NavBarVm?> = MutableStateFlow(null)
  val model: NavBarVm? get() = _vm.value

  init {
    add(StaticNavBarPanel(project, cs, _vm), BorderLayout.CENTER)
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
      val roots = viewModel.visibleRoots
      do {
        path.add(ServiceViewNavBarItem(viewModel, item!!))
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

  private fun requestNavigation(pointer: Pointer<out NavBarItem>) {
    cs.launch(Dispatchers.EDT) {
      (pointer as? ServiceViewNavBarItem)?.item?.let {
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

private class ServiceViewRootNavBarItem(
  private val viewModel: ServiceViewModel,
) : NavBarVmItem,
    NavBarItem,
    Pointer<ServiceViewRootNavBarItem> {

  override fun equals(other: Any?): Boolean {
    return this === other || other is ServiceViewRootNavBarItem && viewModel == other.viewModel
  }

  override fun hashCode(): Int {
    return viewModel.hashCode()
  }

  override fun createPointer(): Pointer<out ServiceViewRootNavBarItem> = this
  override val pointer: Pointer<out ServiceViewRootNavBarItem> get() = this
  override fun dereference(): ServiceViewRootNavBarItem = this // hard pointer

  override fun presentation(): NavBarItemPresentation = presentation
  override val presentation: NavBarItemPresentation = NavBarItemPresentation(
    AllIcons.Nodes.Services, "", null,
    REGULAR_ATTRIBUTES,
    REGULAR_ATTRIBUTES, false
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
) : NavBarItem,
    NavBarVmItem,
    Pointer<ServiceViewNavBarItem> {

  override fun equals(other: Any?): Boolean {
    return this === other || other is ServiceViewNavBarItem && viewModel == other.viewModel && item == other.item
  }

  override fun hashCode(): Int {
    return Objects.hash(viewModel, item)
  }

  override fun createPointer(): Pointer<out NavBarItem> = this
  override val pointer: Pointer<out NavBarItem> get() = this
  override fun dereference(): ServiceViewNavBarItem = this // hard pointer

  override fun presentation(): NavBarItemPresentation = presentation
  override val presentation: NavBarItemPresentation = run {
    val icon = item.getViewDescriptor().getPresentation().getIcon(false)
    val text = ServiceViewDragHelper.getDisplayName(item.getViewDescriptor().getPresentation())
    NavBarItemPresentation(icon, text, null, REGULAR_ATTRIBUTES, REGULAR_ATTRIBUTES, false)
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
