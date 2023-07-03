// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.menu

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.rootTask
import com.intellij.diagnostic.subtask
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionManagerEx
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.MenuItemPresentationFactory
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.IdeFrameDecorator
import com.intellij.openapi.wm.impl.IdeMenuBarState
import com.intellij.openapi.wm.impl.IdeRootPane
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.concurrency.await
import java.awt.Component
import java.awt.Dialog
import java.awt.Dimension
import java.awt.KeyboardFocusManager
import java.util.concurrent.atomic.AtomicLong
import javax.swing.JComponent
import javax.swing.JFrame
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.seconds

internal interface ActionAwareIdeMenuBar {
  fun updateMenuActions(forceRebuild: Boolean = false)
}

internal interface IdeMenuFlavor {
  val state: IdeMenuBarState
    get() = IdeMenuBarState.EXPANDED

  fun jMenuSelectionChanged(isIncluded: Boolean) {
  }

  fun getPreferredSize(size: Dimension): Dimension = size

  fun updateAppMenu()

  fun layoutClockPanelAndButton() {
  }

  fun correctMenuCount(menuCount: Int): Int = menuCount

  fun suspendAnimator() {}
}

internal sealed class IdeMenuBarHelper(@JvmField val flavor: IdeMenuFlavor,
                                       @JvmField internal val menuBar: MenuBarImpl) : ActionAwareIdeMenuBar {
  protected abstract fun isUpdateForbidden(): Boolean

  @JvmField
  protected var visibleActions = emptyList<ActionGroup>()

  @JvmField
  protected val presentationFactory: PresentationFactory = MenuItemPresentationFactory()

  private val updateRequests = MutableSharedFlow<Boolean>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  interface MenuBarImpl {
    val frame: JFrame

    val coroutineScope: CoroutineScope
    val isDarkMenu: Boolean
    val component: JComponent

    fun updateGlobalMenuRoots()

    suspend fun getMainMenuActionGroup(): ActionGroup?
  }

  init {
    val app = ApplicationManager.getApplication()
    val coroutineScope = menuBar.coroutineScope
    @Suppress("IfThenToSafeAccess")
    if (app != null) {
      app.messageBus.connect(coroutineScope).subscribe(UISettingsListener.TOPIC, UISettingsListener {
        check(updateRequests.tryEmit(true))
      })
    }

    coroutineScope.launch(ModalityState.any().asContextElement()) {
      withContext(if (StartUpMeasurer.isEnabled()) (rootTask() + CoroutineName("ide menu bar actions init")) else EmptyCoroutineContext) {
        val actions = updateMenuActions(mainActionGroup = menuBar.getMainMenuActionGroup(), forceRebuild = false, isFirstUpdate = true)
        postInitActions(actions)
      }

      val actionManager = serviceAsync<ActionManager>()
      if (actionManager is ActionManagerEx) {
        coroutineScope.launch {
          actionManager.timerEvents.collect {
            updateOnTimer()
          }
        }
      }

      val lastUpdate = AtomicLong(System.currentTimeMillis())
      updateRequests
        .collect { forceRebuild ->
          // as ActionManagerImpl TIMER_DELAY
          if ((System.currentTimeMillis() - lastUpdate.get()) < 500) {
            return@collect
          }

          if (!withContext(Dispatchers.EDT) { filterUpdate() }) {
            return@collect
          }

          presentationFactory.reset()
          updateMenuActions(mainActionGroup = menuBar.getMainMenuActionGroup(), forceRebuild = forceRebuild, isFirstUpdate = false)
          lastUpdate.set(System.currentTimeMillis())
        }
    }
  }

  private fun filterUpdate(): Boolean {
    if (!menuBar.frame.isShowing || !menuBar.frame.isActive) {
      return false
    }

    // do not update when a popup menu is shown
    // (if a popup menu contains action which is also in the menu bar, it should not be enabled/disabled)
    if (isUpdateForbidden()) {
      return false
    }

    // don't update the toolbar if there is currently active modal dialog
    val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
    return window !is Dialog || !window.isModal
  }

  private suspend fun updateOnTimer() {
    withContext(Dispatchers.EDT) {
      // don't update the toolbar if there is currently active modal dialog
      val window = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
      if (window !is Dialog || !window.isModal) {
        updateRequests.emit(false)
      }
    }
  }

  final override fun updateMenuActions(forceRebuild: Boolean) {
    check(updateRequests.tryEmit(forceRebuild))
  }

  protected open suspend fun postInitActions(actions: List<ActionGroup>) {
  }

  abstract suspend fun updateMenuActions(mainActionGroup: ActionGroup?, forceRebuild: Boolean, isFirstUpdate: Boolean): List<ActionGroup>

  protected fun createActionMenuList(newVisibleActions: List<ActionGroup>, consumer: (ActionMenu) -> Unit) {
    if (newVisibleActions.isEmpty()) {
      return
    }

    val enableMnemonics = !UISettings.getInstance().disableMnemonics
    val isCustomDecorationActive = IdeFrameDecorator.isCustomDecorationActive()
    for (action in newVisibleActions) {
      val actionMenu = ActionMenu(null, ActionPlaces.MAIN_MENU, action, presentationFactory, enableMnemonics, menuBar.isDarkMenu, true)
      if (isCustomDecorationActive) {
        actionMenu.isOpaque = false
        actionMenu.isFocusable = false
      }
      consumer(actionMenu)
    }
  }
}

@Suppress("unused")
private val firstUpdateFastTrackUpdateTimeout = 30.seconds.inWholeMilliseconds
private val useAsyncExpand = System.getProperty("idea.app.menu.async.expand", "false").toBoolean()

internal suspend fun expandMainActionGroup(mainActionGroup: ActionGroup,
                                           menuBar: Component,
                                           frame: JFrame,
                                           presentationFactory: PresentationFactory,
                                           isFirstUpdate: Boolean): List<ActionGroup> {
  if (!useAsyncExpand) {
    return syncExpandMainActionGroup(mainActionGroup, serviceAsync<ActionManager>(), frame, menuBar, presentationFactory)
  }

  try {
    return withContext(CoroutineName("expandMainActionGroup") + Dispatchers.EDT) {
      val targetComponent = serviceAsync<WindowManager>().getFocusedComponent(frame) ?: menuBar
      val dataContext = Utils.wrapToAsyncDataContext(DataManager.getInstance().getDataContext(targetComponent))
      val fastTrackTimeout = if (isFirstUpdate) firstUpdateFastTrackUpdateTimeout else Utils.getFastTrackTimeout()
      Utils.expandActionGroupAsync(/* group = */ mainActionGroup,
                                   /* presentationFactory = */ presentationFactory,
                                   /* context = */ dataContext,
                                   /* place = */ ActionPlaces.MAIN_MENU,
                                   /* isToolbarAction = */ false,
                                   /* fastTrackTimeout = */ fastTrackTimeout)
    }.await().filterIsInstance<ActionGroup>()
  }
  catch (e: ProcessCanceledException) {
    if (isFirstUpdate) {
      logger<IdeMenuBarHelper>().warn("Cannot expand action group", e)
    }

    // don't repeat - will do on next timer event
    return emptyList()
  }
}

private suspend fun syncExpandMainActionGroup(mainActionGroup: ActionGroup,
                                              actionManager: ActionManager,
                                              frame: JFrame,
                                              menuBar: Component,
                                              presentationFactory: PresentationFactory): List<ActionGroup> {
  return subtask("expandMainActionGroup", Dispatchers.EDT) {
    val children = mainActionGroup.getChildren(null, actionManager)
    if (children.isEmpty()) {
      return@subtask emptyList()
    }

    val targetComponent = WindowManager.getInstance().getFocusedComponent(frame) ?: menuBar
    val dataContext = DataManager.getInstance().getDataContext(targetComponent)
    val list = mutableListOf<ActionGroup>()
    for (action in children) {
      if (action !is ActionGroup) {
        continue
      }

      val presentation = presentationFactory.getPresentation(action)
      val e = AnActionEvent(null, dataContext, ActionPlaces.MAIN_MENU, presentation, actionManager, 0)
      ActionUtil.performDumbAwareUpdate(action, e, false)
      if (presentation.isVisible) {
        list.add(action)
      }
    }
    list
  }
}

internal suspend fun getMainMenuActionGroup(frame: JFrame): ActionGroup? {
  val group = (frame.rootPane as? IdeRootPane)?.mainMenuActionGroup
  return group ?: CustomActionsSchema.getInstanceAsync().getCorrectedActionAsync(IdeActions.GROUP_MAIN_MENU)
}