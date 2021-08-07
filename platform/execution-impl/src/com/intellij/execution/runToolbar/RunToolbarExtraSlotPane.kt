// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.absoluteValue

class RunToolbarExtraSlotPane(val project: Project, val cancel: () -> Unit) {
  private val manager = RunToolbarSlotManager.getInstance(project)
  val slotPane = JPanel(VerticalLayout(JBUI.scale(2))).apply {
    isOpaque = false
  }

  private val components = mutableListOf<SlotComponent>()

  private val managerListener = object : SlotListener {
    override fun slotAdded() {
      addSingleSlot()
    }

    override fun slotRemoved(index: Int) {
      if(index >= 0 && index < components.size) {
        removeSingleComponent(components[index])
      }
      else {
        rebuild()
      }
    }

    override fun rebuildPopup() {
      rebuild()
    }
  }

  private val pane = object : JPanel(VerticalLayout(JBUI.scale(2))) {
    override fun addNotify() {
      manager.addListener(managerListener)
      if (manager.slotsCount() == 0) {
        manager.addNewSlot()
      } else {
        build()
      }
      super.addNotify()
      SwingUtilities.invokeLater {
        pack()
      }
    }

    override fun removeNotify() {
      manager.removeListener(managerListener)
      super.removeNotify()
    }
  }.apply {
    border = JBUI.Borders.empty(3)
    add(slotPane)

    add(JPanel(MigLayout("ins 0, novisualpadding", "[min!]push[min!]")).apply {
      isOpaque = false
      border = JBUI.Borders.empty(2, 1, 0, 5)
      this.add(JLabel(AllIcons.Toolbar.AddSlot).apply {
        this.addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            manager.addNewSlot()
          }
        })
      })
      this.add(JLabel(AllIcons.General.GearPlain))
    })
  }

  internal fun getView(): JComponent = pane

  private fun rebuild() {
    build()
    if(manager.slotsCount() > 0) {
      pack()
    }
  }

  private fun cancelPopup() {
    SwingUtilities.invokeLater{
      cancel()
    }
  }

  private fun build() {
    val count = manager.slotsCount()
    if(count == 0) {
      slotPane.removeAll()
      components.clear()
      cancelPopup()
      return
    } else {
      val diff = count - components.size
      repeat(diff.absoluteValue) { if(diff > 0) addNewSlot() else removeComponent(components[it])}
    }
  }

  private fun addSingleSlot() {
    addNewSlot()
    pack()
  }

  private fun addNewSlot() {
    val slot = createComponent()
    slot.minus.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(e: MouseEvent) {
        getData(slot)?.let {
          manager.removeSlot(it.id)
        }
      }
    })

    slotPane.add(slot.view)
    components.add(slot)
  }

  private fun pack() {
    slotPane.revalidate()
    pane.revalidate()

    slotPane.repaint()
    pane.repaint()
    UIUtil.getWindow(pane)?.let {
      if (it.isShowing) {
        it.pack()
      }
    }
  }

  private fun removeSingleComponent(component: SlotComponent) {
    removeComponent(component)
    if (components.isNotEmpty()) {
      pack()
    }
  }

  private fun removeComponent(component: SlotComponent) {
    slotPane.remove(component.view)
    components.remove(component)
    if (components.isEmpty()) {
      cancelPopup()
    }
  }

  private fun getData(component: SlotComponent): SlotDate? {
    val index = components.indexOf(component)
    if(index < 0) return null
    return manager.getData(index)
  }

  private fun createComponent(): SlotComponent {
    val group = DefaultActionGroup()
    val bar = FixWidthSegmentedActionToolbarComponent(ActionPlaces.RUN_TOOLBAR, group)
    val component = SlotComponent(bar, JLabel(AllIcons.Toolbar.RemoveSlot))

    bar.targetComponent = bar
    DataManager.registerDataProvider(bar, DataProvider {
      key ->
      if(RunToolbarData.RUN_TOOLBAR_DATA_KEY.`is`(key)) {
        getData(component)
      }
      else
        null
    })

    val runToolbarActionsGroup = ActionManager.getInstance().getAction(
      "RunToolbarActionsGroup") as DefaultActionGroup

    val dataContext = DataManager.getInstance().getDataContext(bar)
    val event = AnActionEvent.createFromDataContext("RunToolbarActionsGroup", null, dataContext)

    for (action in runToolbarActionsGroup.getChildren(event)) {
      if (action is ActionGroup && !action.isPopup) {
        group.addAll(*action.getChildren(event))
      }
      else {
        group.addAction(action)
      }
    }

    return component
  }

  internal data class SlotComponent(val bar: SegmentedActionToolbarComponent, val minus: JComponent) {
    val view = JPanel(MigLayout("ins 0, gapx 2, novisualpadding")).apply {
      add(minus)
      add(bar)
    }
  }
}