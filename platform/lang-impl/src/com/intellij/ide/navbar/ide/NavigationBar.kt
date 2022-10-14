// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.codeInsight.navigation.actions.navigateRequest
import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.NavBarItemProvider
import com.intellij.ide.navbar.impl.ModuleNavBarItem
import com.intellij.ide.navbar.impl.ProjectNavBarItem
import com.intellij.ide.navbar.impl.PsiNavBarItem
import com.intellij.ide.navbar.impl.pathToItem
import com.intellij.ide.navbar.ui.FloatingModeHelper
import com.intellij.ide.navbar.ui.NavigationBarPopup
import com.intellij.ide.navbar.ui.NewNavBarPanel
import com.intellij.ide.navbar.vm.NavBarVmItem
import com.intellij.ide.navbar.vm.PopupResult
import com.intellij.ide.navbar.vm.PopupResult.*
import com.intellij.lang.documentation.ide.ui.DEFAULT_UI_RESPONSE_TIMEOUT
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.HintHint
import com.intellij.util.flow.throttle
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

internal class NavigationBar(
  private val myProject: Project,
  private val cs: CoroutineScope,
  dataContext: DataContext? = null
) : Disposable {

  // Flag to block external model changes while user click is being processed
  private val modelChangesAllowed = AtomicBoolean(true)

  private lateinit var myComponent: NewNavBarPanel

  private val myItems: MutableStateFlow<List<NavBarVmItem>>

  private val myItemEvents = MutableSharedFlow<ItemEvent>(replay = 1, onBufferOverflow = DROP_OLDEST)

  init {
    val initialContext = dataContext ?: runBlocking { focusDataContext() }

    val initialModel = runBlocking {
      val items = readAction {
        buildModel(initialContext)
      }
      items.ifEmpty {
        readAction {
          val projectItem = ProjectNavBarItem(myProject)
          val uiProjectItem = NavBarVmItem(projectItem.createPointer(), projectItem.presentation(), projectItem.javaClass)
          listOf(uiProjectItem)
        }
      }
    }

    myItems = MutableStateFlow(initialModel)

    if (dataContext == null) {

      cs.launch(Dispatchers.Default) {
        activityFlow()
          .throttle(DEFAULT_UI_RESPONSE_TIMEOUT)
          .collectLatest {
            try {
              if (modelChangesAllowed.get()) {
                val focusedData = focusDataContext()
                val focusedProject = CommonDataKeys.PROJECT.getData(focusedData)
                if (focusedProject == myProject) {
                  val model = readAction {
                    buildModel(focusedData)
                  }
                  if (model.isNotEmpty()) {
                    myItems.emit(model)
                  }
                }
              }
            }
            catch (ce: CancellationException) {
              throw ce
            }
            catch (pce: ProcessCanceledException) {
              // To throw or not to throw that is a question...
              // ignore // TODO find out why it is being actually thrown
            }
            catch (t: Throwable) {
              LOG.error(t)
            }
          }
      }
    }

    // handle clicks on navigation bar
    cs.launch(Dispatchers.EDT + ModalityState.NON_MODAL.asContextElement()) {
      myItemEvents.collectLatest { e ->
        when (e) {
          is ItemEvent.Select -> freezeModelAndInvoke {
            handleItemSelected(e.item)
            FloatingModeHelper.hideHint(false)
          }
          is ItemEvent.Activate -> navigateTo(myProject, e.item)
        }
      }
    }

  }

  override fun dispose() {
    cs.coroutineContext.cancel()
  }

  fun focusTail() {
    cs.launch(Dispatchers.Default) {
      val items = myItems.value
      val i = (items.size - 2).coerceAtLeast(0)
      val item = items[i]
      myItemEvents.emit(ItemEvent.Select(item))
    }
  }


  fun getPanel(): NewNavBarPanel {
    EDT.assertIsEdt()
    myComponent = NewNavBarPanel(myItemEvents, myItems.asStateFlow(), cs)
    return myComponent
  }

  private suspend fun childrenPopupPrompt(selectedItemIndex: Int, children: List<NavBarVmItem>): PopupResult =
    suspendCancellableCoroutine {
      SwingUtilities.invokeLater {
        myComponent.scrollTo(selectedItemIndex)
        val nextItem = myItems.value.getOrNull(selectedItemIndex + 1)
        val popupHint = NavigationBarPopup(NavBarPopupVmImpl(children, nextItem, it))
        val absolutePoint = myComponent.getItemPopupLocation(selectedItemIndex, popupHint)
        popupHint.show(myComponent, absolutePoint.x, absolutePoint.y, myComponent, HintHint(myComponent, absolutePoint))
      }
    }

  // Run body with no external model changes allowed
  private suspend fun freezeModelAndInvoke(body: suspend () -> Unit) {
    modelChangesAllowed.set(false)
    try {
      body()
    }
    finally {
      modelChangesAllowed.set(true)
    }
  }

  private suspend fun handleItemSelected(item: NavBarVmItem) {
    var items = myItems.value
    var selectedIndex = items.indexOf(item).takeUnless { it < 0 } ?: return
    var children = item.children() ?: return

    while (true) {
      // Popup with [children] should be displayed for user at [selectedItem] item
      // Empty [children] is an illegal case, popup navigation ends
      if (children.isEmpty()) {
        return
      }

      // Force suspend to render new navbar for proper popup positioning
      yield()

      val popupResult = childrenPopupPrompt(selectedIndex, children)

      when (popupResult) {
        PopupResultCancel -> {
          FloatingModeHelper.hideHint(true)
          return
        }
        PopupResultLeft -> {
          if (selectedIndex > 0) {
            selectedIndex--
            children = items[selectedIndex].children() ?: return
          }
        }
        PopupResultRight -> {
          if (selectedIndex < items.size - 1) {
            selectedIndex++
          }
          val localChildren = items[selectedIndex].children() ?: return
          if (localChildren.isEmpty()) {
            selectedIndex--
          }
          else {
            children = localChildren
          }
        }
        is PopupResultSelect -> {
          val selectedChild = popupResult.item
          val expandResult = autoExpand(selectedChild) ?: return
          when (expandResult) {
            is ExpandResult.NavigateTo -> {
              val navigationRequest = expandResult.target.fetch(NavBarItem::navigationRequest)
              if (navigationRequest != null) {
                withContext(Dispatchers.EDT) {
                  modelChangesAllowed.set(true)
                  navigateRequest(myProject, navigationRequest)
                }
              }
              return
            }
            is ExpandResult.NextPopup -> {
              val newModel = items.slice(0..selectedIndex) + expandResult.expanded
              myItems.value = newModel
              items = newModel
              selectedIndex = newModel.indices.last
              children = expandResult.children
            }
          }
        }
      }
    }
  }
}

private sealed interface ItemEvent {
  class Select(val item: NavBarVmItem) : ItemEvent
  class Activate(val item: NavBarVmItem) : ItemEvent
}

private sealed interface ExpandResult {
  class NavigateTo(val target: NavBarVmItem) : ExpandResult
  class NextPopup(val expanded: List<NavBarVmItem>, val children: List<NavBarVmItem>) : ExpandResult
}

private suspend fun autoExpand(child: NavBarVmItem): ExpandResult? {
  var expanded = emptyList<NavBarVmItem>()
  var currentItem = child
  var (children, navigateOnClick) = currentItem.fetch(childrenSelector, NavBarItem::navigateOnClick) ?: return null

  if (children.isEmpty() || navigateOnClick) {
    return ExpandResult.NavigateTo(currentItem)
  }

  while (true) {
    // [currentItem] -- is being evaluated
    // [expanded] -- list of the elements starting from [child] argument and up to [currentItem]. Both exclusively
    // [children] -- children of the [currentItem]
    // [showPopup] -- if [currentItem]'s children should be shown as a popup

    // No automatic navigation in this cycle!
    // It is only allowed as reaction to a users click
    // at the popup item, i.e. at first iteration before while-cycle

    when (children.size) {
      0 -> {
        // No children, [currentItem] is an only leaf on its branch, but no auto navigation allowed
        // So returning the previous state
        return ExpandResult.NextPopup(expanded, listOf(currentItem))
      }
      1 -> {
        if (navigateOnClick) {
          // [currentItem] is navigation target regardless of its children count, but no auto navigation allowed
          // So returning the previous state
          return ExpandResult.NextPopup(expanded, listOf(currentItem))
        }
        else {
          // Performing auto-expand, keeping invariant
          expanded = expanded + currentItem
          currentItem = children.single()
          val fetch = currentItem.fetch(childrenSelector, NavBarItem::navigateOnClick) ?: return null
          children = fetch.first
          navigateOnClick = fetch.second
        }
      }
      else -> {
        // [currentItem] has several children, so return it with current [expanded] trace.
        return ExpandResult.NextPopup(expanded + currentItem, children)
      }
    }
  }
}

private suspend fun navigateTo(project: Project, item: NavBarVmItem) {
  val navigationRequest = readAction {
    item.pointer.dereference()?.navigationRequest()
  }
  if (navigationRequest != null) {
    CoroutineScope(currentCoroutineContext())
      .launch(ModalityState.NON_MODAL.asContextElement()) {
        navigateRequest(project, navigationRequest)
      }
  }
}

private suspend fun NavBarVmItem.children(): List<NavBarVmItem>? {
  return fetch(childrenSelector)
}

private suspend fun <T> NavBarVmItem.fetch(selector: NavBarItem.() -> T): T? {
  return readAction {
    pointer.dereference()?.selector()
  }
}

private suspend fun <T1, T2> NavBarVmItem.fetch(
  selector1: NavBarItem.() -> T1,
  selector2: NavBarItem.() -> T2
): Pair<T1, T2>? = fetch { Pair(selector1(), selector2()) }


private val childrenSelector: NavBarItem.() -> List<NavBarVmItem> = {
  iterateAllChildren()
    .sortedWith(siblingsComparator)
    .map {
      NavBarVmItem(it.createPointer(), it.presentation(), it.javaClass)
    }
}

private fun NavBarItem.weight() = when (this) {
  is ModuleNavBarItem -> 5
  is PsiNavBarItem -> when (data) {
    is PsiDirectoryContainer -> 4
    is PsiDirectory -> 4
    is PsiFile -> 2
    is PsiNamedElement -> 3
    else -> Int.MAX_VALUE
  }
  else -> Int.MAX_VALUE
}

private val weightComparator = compareBy<NavBarItem> { -it.weight() }
private val nameComparator = compareBy<NavBarItem, String>(NaturalComparator.INSTANCE) { it.presentation().text }
private val siblingsComparator = weightComparator.then(nameComparator)

private fun NavBarItem.iterateAllChildren(): Iterable<NavBarItem> =
  NavBarItemProvider.EP_NAME
    .extensionList
    .flatMap { ext -> ext.iterateChildren(this) }

private fun buildModel(ctx: DataContext): List<NavBarVmItem> {
  val contextItem = NavBarItem.NAVBAR_ITEM_KEY.getData(ctx)
                    ?: return emptyList()
  return contextItem.pathToItem().map {
    NavBarVmItem(it.createPointer(), it.presentation(), it.javaClass)
  }
}
