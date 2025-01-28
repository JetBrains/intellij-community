package com.intellij.database.run.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.editor.impl.EditorHeaderComponent
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * @author Liudmila Kornilova
 **/
abstract class TabInfoProvider(title: @NlsContexts.TabTitle String, private val actionGroup: ActionGroup?) : Disposable {
  private val panel = JPanel(BorderLayout())
  protected val toolbar = createToolbar()
  val tabInfo = TabInfo(JBPanelWithEmptyText()).setText(title).setComponent(panel).setSideComponent(toolbar?.let {
    EditorHeaderComponent().also { header ->
      header.add(toolbar.component, BorderLayout.EAST)
      header.border = JBUI.Borders.empty()
    }
  })

  protected var isOnTab: Boolean = false
  protected var isUpdated: Boolean = true

  abstract fun getViewer(): CellViewer

  open fun onTabLeave() {
    isOnTab = false
  }

  open fun onTabEnter() {
    isOnTab = true
    if (!isUpdated) {
      update(UpdateEvent.ContentChanged)
      updateTabInfo()
    }
  }

  open fun update(event: UpdateEvent? = null) {
    isUpdated = false
    val viewer = getViewer()
    if (!isUpdated) {
      viewer.update(event)
      isUpdated = true
    }
  }

  protected fun updateTabInfo() {
    panel.removeAll()
    panel.add(getViewer().component, BorderLayout.CENTER)
    tabInfo.setPreferredFocusableComponent(getViewer().preferedFocusComponent)
    toolbar?.targetComponent = getViewer().toolbarTargetComponent
  }

  private fun createToolbar(): ActionToolbar? {
    return actionGroup?.let {
      ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, it, true).apply {
        setReservePlaceAutoPopupIcon(false)
      }
    }
  }
}