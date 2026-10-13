// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.fileTypes.INativeFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isTooLargeForIntellijSense
import com.intellij.util.Alarm
import com.intellij.util.SingleAlarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.lang.ref.WeakReference
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.SwingUtilities

abstract class AutoScrollToSourceHandler {
  private var scheduledNavigationData: WeakReference<Component>? = null

  // access only from EDT
  private var autoScrollAlarm: SingleAlarm? = null 
    
  private fun getOrCreateAutoScrollAlarm(): SingleAlarm {
    var alarm = autoScrollAlarm
    if (alarm == null) {
      alarm = SingleAlarm(
        task = {
          val component = scheduledNavigationData?.get() ?: return@SingleAlarm
          scheduledNavigationData = null
          // for tests
          if (component.isShowing && (!needToCheckFocus() || UIUtil.hasFocus(component))) {
            scrollToSource(component)
          }
        },
        delay = Registry.intValue("ide.autoscroll.to.source.delay", 100),
        parentDisposable = null,
        threadToUse = Alarm.ThreadToUse.SWING_THREAD,
        modalityState = ModalityState.defaultModalityState(),
      )
      autoScrollAlarm = alarm
    }
    return alarm
  }

  fun install(tree: JTree) {
    object : ClickListener() {
      override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
        if (clickCount > 1) {
          return false
        }

        val location = tree.getPathForLocation(e.point.x, e.point.y)
        if (location != null) {
          onMouseClicked(tree)
          // return isAutoScrollMode(); // do not consume event to allow processing by a tree
        }

        return false
      }
    }.installOn(tree)

    tree.addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseDragged(e: MouseEvent) {
        onSelectionChanged(tree)
      }
    })
    tree.addTreeSelectionListener { onSelectionChanged(tree) }
  }

  fun install(table: JTable) {
    object : ClickListener() {
      override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
        if (clickCount >= 2) return false

        val location = table.getComponentAt(e.point)
        if (location != null) {
          onMouseClicked(table)
          return isAutoScrollMode()
        }
        return false
      }
    }.installOn(table)

    table.addMouseMotionListener(object : MouseMotionAdapter() {
      override fun mouseDragged(e: MouseEvent) {
        onSelectionChanged(table)
      }
    })
    table.selectionModel.addListSelectionListener { onSelectionChanged(table) }
  }

  fun install(jList: JList<*>) {
    object : ClickListener() {
      override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
        if (clickCount >= 2) return false
        val source = e.source
        val index = jList.locationToIndex(SwingUtilities.convertPoint(if (source is Component) source else null, e.point, jList))
        if (index >= 0 && index < jList.model.size) {
          onMouseClicked(jList)
          return true
        }
        return false
      }
    }.installOn(jList)

    jList.addListSelectionListener { onSelectionChanged(jList) }
  }

  fun cancelAllRequests() {
    autoScrollAlarm?.cancel()
  }

  /**
   * Resets the internal instance to ensure that it'll be recreated properly if the component is reused in a different modal dialog.
   * This is a temporary solution specifically for the Project Structure dialog, it's better to recreate components instead of reusing them.
   */
  @ApiStatus.Internal
  @ApiStatus.Obsolete
  fun resetAlarm() {
    autoScrollAlarm = null
  }

  fun onMouseClicked(component: Component) {
    cancelAllRequests()
    if (isAutoScrollMode()) {
      scrollToSource(component)
    }
  }

  private fun onSelectionChanged(component: Component?) {
    if (component != null && component.isShowing && isAutoScrollMode()) {
      scheduledNavigationData = WeakReference(component)
      getOrCreateAutoScrollAlarm().cancelAndRequest()
    }
  }

  protected open val actionName: @NlsActions.ActionText String?
    get() = UIBundle.message("autoscroll.to.source.action.name")

  protected open val actionDescription: @NlsActions.ActionDescription String?
    get() = UIBundle.message("autoscroll.to.source.action.description")

  protected open fun needToCheckFocus(): Boolean = true

  protected abstract fun isAutoScrollMode(): Boolean

  protected abstract fun setAutoScrollMode(state: Boolean)

  /**
   * @param file a file selected in a tree
   * @return `false` if navigation to the file is prohibited
   */
  @ApiStatus.Internal
  open fun isAutoScrollEnabledFor(file: VirtualFile): Boolean {
    // Attempt to navigate to the virtual file with an unknown file type will show a modal dialog
    // asking to register some file type for this file.
    // This behavior is undesirable when auto scrolling.
    val type = file.fileType
    if (type === FileTypes.UNKNOWN || type is INativeFileType) {
      return false
    }

    //IDEA-84881 Don't autoscroll to very large files
    return !file.isTooLargeForIntellijSense()
  }

  @RequiresEdt
  protected open fun scrollToSource(tree: Component) {
    AutoScrollToSourceTaskManager.getInstance()
      .scheduleScrollToSource(handler = this, dataContext = DataManager.getInstance().getDataContext(tree))
  }

  fun createToggleAction(): ToggleAction = AutoscrollToSourceAction(actionName, actionDescription)

  private inner class AutoscrollToSourceAction(
    actionName: @NlsActions.ActionText String?,
    actionDescription: @NlsActions.ActionDescription String?
  ) : ToggleAction(actionName, actionDescription, AllIcons.General.AutoscrollToSource), DumbAware {
    override fun isSelected(event: AnActionEvent): Boolean = isAutoScrollMode()

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun setSelected(event: AnActionEvent, flag: Boolean) {
      setAutoScrollMode(flag)
    }
  }
}

