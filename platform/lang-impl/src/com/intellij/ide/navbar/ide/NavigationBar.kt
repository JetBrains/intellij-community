// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navbar.ide

import com.intellij.codeInsight.navigation.actions.navigateRequest
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.navbar.NavBarItem
import com.intellij.ide.navbar.NavBarItemPresentation
import com.intellij.ide.navbar.NavBarItemProvider
import com.intellij.ide.navbar.ide.ItemSelectType.NAVIGATE
import com.intellij.ide.navbar.ide.ItemSelectType.OPEN_POPUP
import com.intellij.ide.navbar.impl.ModuleNavBarItem
import com.intellij.ide.navbar.impl.ProjectNavBarItem
import com.intellij.ide.navbar.impl.PsiNavBarItem
import com.intellij.ide.navbar.ui.FloatingModeHelper
import com.intellij.ide.navbar.ui.NavBarPanel
import com.intellij.ide.navbar.ui.NavigationBarPopup
import com.intellij.ide.navbar.ui.PopupEvent
import com.intellij.ide.navbar.ui.PopupEvent.*
import com.intellij.lang.documentation.ide.ui.DEFAULT_UI_RESPONSE_TIMEOUT
import com.intellij.model.Pointer
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDirectoryContainer
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNamedElement
import com.intellij.ui.ExperimentalUI
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
import kotlin.coroutines.resume


internal val navbarV2Enabled: Boolean = Registry.`is`("ide.navBar.v2", false)


internal val LOG: Logger = Logger.getInstance("#com.intellij.ide.navbar.ide")

internal class UiNavBarItem(
  val pointer: Pointer<out NavBarItem>,
  val presentation: NavBarItemPresentation,
  itemClass: Class<NavBarItem>
) {

  // Synthetic string field for fast equality heuristics
  // Used to match element's direct child in the navbar with the same child in its popup
  private val texts = itemClass.canonicalName + "$" +
                      presentation.text.replace("$", "$$") + "$" +
                      presentation.popupText?.replace("$", "$$")

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as UiNavBarItem
    return texts == other.texts
  }

  override fun hashCode() = texts.hashCode()

}

internal enum class ItemSelectType { OPEN_POPUP, NAVIGATE }
internal class ItemClickEvent(val type: ItemSelectType, val index: Int, val item: UiNavBarItem)

private sealed class ExpandResult {
  class NavigateTo(val target: UiNavBarItem) : ExpandResult()
  class NextPopup(val expanded: List<UiNavBarItem>, val children: List<UiNavBarItem>) : ExpandResult()
}


internal class NavigationBar(
  private val myProject: Project,
  private val cs: CoroutineScope,
  dataContext: DataContext? = null
) : Disposable {

  // Flag to block external model changes while user click is being processed
  private val modelChangesAllowed = AtomicBoolean(true)

  private val myItemClickEvents = MutableSharedFlow<ItemClickEvent>(replay = 1, onBufferOverflow = DROP_OLDEST)
  private lateinit var myComponent: NavBarPanel

  private val myActivityEvents = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = DROP_OLDEST)
  private val myItems: MutableStateFlow<List<UiNavBarItem>>

  init {
    val initialContext = dataContext ?: runBlocking { getFocusedData() }

    val initialModel = runBlocking {
      val items = readAction {
        buildModel(initialContext)
      }
      items.ifEmpty {
        readAction {
          val projectItem = ProjectNavBarItem(myProject)
          val uiProjectItem = UiNavBarItem(projectItem.createPointer(), projectItem.presentation(), projectItem.javaClass)
          listOf(uiProjectItem)
        }
      }
    }

    myItems = MutableStateFlow(initialModel)

    if (dataContext == null) {

      // Watch the user activities
      IdeEventQueue.getInstance().addActivityListener(Runnable {
        myActivityEvents.tryEmit(Unit)
      }, this)

      cs.launch(Dispatchers.Default) {
        myActivityEvents
          .throttle(DEFAULT_UI_RESPONSE_TIMEOUT)
          .collectLatest {
            try {
              if (modelChangesAllowed.get()) {
                val focusedData = getFocusedData()
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
      myItemClickEvents.collectLatest { e ->
        when (e.type) {
          OPEN_POPUP -> freezeModelAndInvoke {
            handleItemSelected(e.index)
            FloatingModeHelper.hideHint(false)
          }
          NAVIGATE -> navigateTo(myProject, e.item)
        }
      }
    }

  }

  override fun dispose() {
    cs.coroutineContext.cancel()
  }

  fun focusTail() {
    cs.launch(Dispatchers.Default) {
      val i = (myItems.value.size - 2).coerceAtLeast(0)
      val item = myItems.value[i]
      myItemClickEvents.emit(ItemClickEvent(OPEN_POPUP, i, item))
    }
  }


  fun getPanel(): NavBarPanel {
    EDT.assertIsEdt()
    myComponent = NavBarPanel(myItemClickEvents, myItems.asStateFlow(), cs)
    return myComponent
  }

  private suspend fun childrenPopupPrompt(selectedItemIndex: Int, children: List<UiNavBarItem>): PopupEvent =
    suspendCancellableCoroutine {
      SwingUtilities.invokeLater {
        myComponent.scrollTo(selectedItemIndex)
        val nextItem = myItems.value.getOrNull(selectedItemIndex + 1)
        val popupHint = NavigationBarPopup(children, nextItem, it)
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

  private suspend fun autoExpand(child: UiNavBarItem): ExpandResult? {
    var expanded = emptyList<UiNavBarItem>()
    var currentItem = child
    var (children, navigateOnClick) = currentItem.fetch(childrenSelector, NavBarItem::navigateOnClick) ?: return null

    if (children.isEmpty() || navigateOnClick) {
      return ExpandResult.NavigateTo(currentItem)
    }

    while (true) {
      // *currentItem* -- is being evaluated
      // *expanded* -- list of the elements starting from *child* argument and up to *currentItem*. Both exclusively
      // *children* -- children of the *currentItem*
      // *showPopup* -- if *currentItem*'s children should be shown as a popup

      // No automatic navigation in this cycle!
      // It is only allowed as reaction to a users click
      // at the popup item, i.e. at first iteration before while-cycle

      when (children.size) {
        0 -> {
          // No children, *currentItem* is an only leaf on its branch, but no auto navigation allowed
          // So returning the previous state
          return ExpandResult.NextPopup(expanded, listOf(currentItem))
        }
        1 -> {
          if (navigateOnClick) {
            // *currentItem* is navigation target regardless of its children count, but no auto navigation allowed
            // So returning the previous state
            return ExpandResult.NextPopup(expanded, listOf(currentItem))
          }
          else {
            // Performing autoexpand, keeping invariant
            expanded = expanded + currentItem
            currentItem = children.single()
            val fetch = currentItem.fetch(childrenSelector, NavBarItem::navigateOnClick) ?: return null
            children = fetch.first
            navigateOnClick = fetch.second
          }
        }
        else -> {
          // *currentItem* has several children, so return it with current *expanded* trace.
          return ExpandResult.NextPopup(expanded + currentItem, children)
        }
      }
    }

  }

  private suspend fun handleItemSelected(index: Int) {
    var selectedIndex = index
    var children = myItems.value.getOrNull(selectedIndex)?.fetch(childrenSelector) ?: return

    while (true) {
      // Popup with *children* should be displayed for user at *selectedItem* item
      // Empty *children* is an illegal case, popup navigation ends
      if (children.isEmpty()) {
        return
      }

      // Force suspend to render new navbar for proper popup positioning
      yield()

      val popupResult = childrenPopupPrompt(selectedIndex, children)

      when (popupResult) {
        PopupEventCancel -> {
          FloatingModeHelper.hideHint(true)
          return
        }
        PopupEventLeft -> {
          if (selectedIndex > 0) {
            selectedIndex--
            children = myItems.value[selectedIndex].fetch(childrenSelector) ?: return
          }
        }
        PopupEventRight -> {
          if (selectedIndex < myItems.value.size - 1) {
            selectedIndex++
          }
          val localChildren = myItems.value[selectedIndex].fetch(childrenSelector) ?: return
          if (localChildren.isEmpty()) {
            selectedIndex--
          }
          else {
            children = localChildren
          }
        }
        is PopupEventSelect -> {
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
              val newModel = myItems.value.slice(0..selectedIndex) + expandResult.expanded
              selectedIndex = newModel.indices.last
              myItems.emit(newModel)
              children = expandResult.children
            }
          }
        }
      }
    }
  }
}


private suspend fun navigateTo(project: Project, item: UiNavBarItem) {
  val navigationRequest = withContext(Dispatchers.Default) {
    readAction {
      item.pointer.dereference()?.navigationRequest()
    }
  }
  if (navigationRequest != null) {
    CoroutineScope(currentCoroutineContext())
      .launch(ModalityState.NON_MODAL.asContextElement()) {
        navigateRequest(project, navigationRequest)
      }
  }
}


private suspend fun <T> UiNavBarItem.fetch(selector: NavBarItem.() -> T): T? {
  return withContext(Dispatchers.Default) {
    readAction {
      pointer.dereference()?.selector()
    }
  }
}

private suspend fun <T1, T2> UiNavBarItem.fetch(
  selector1: NavBarItem.() -> T1,
  selector2: NavBarItem.() -> T2
): Pair<T1, T2>? = fetch { Pair(selector1(), selector2()) }


private val childrenSelector: NavBarItem.() -> List<UiNavBarItem> = {
  iterateAllChildren()
    .sortedWith(siblingsComparator)
    .map {
      UiNavBarItem(it.createPointer(), it.presentation(), it.javaClass)
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


private suspend fun getFocusedData(): DataContext = suspendCancellableCoroutine {
  IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(Runnable {
    @Suppress("DEPRECATION")
    val dataContextFromFocusedComponent = DataManager.getInstance().dataContext
    val uiSnapshot = Utils.wrapToAsyncDataContext(dataContextFromFocusedComponent)
    val asyncDataContext = AnActionEvent.getInjectedDataContext(uiSnapshot)
    it.resume(asyncDataContext)
  }, ModalityState.any())
}

private fun buildModel(ctx: DataContext): List<UiNavBarItem> {
  val result = arrayListOf<UiNavBarItem>()
  var element = NavBarItem.NAVBAR_ITEM_KEY.getData(ctx)
  while (element != null) {
    val p = element.presentation()
    result.add(UiNavBarItem(element.createPointer(), p, element.javaClass))
    element = element.findParent()
  }
  return (result as List<UiNavBarItem>).asReversed()
}

private fun NavBarItem.findParent(): NavBarItem? =
  NavBarItemProvider.EP_NAME
    .extensionList
    .firstNotNullOfOrNull { ext -> ext.findParent(this) }
