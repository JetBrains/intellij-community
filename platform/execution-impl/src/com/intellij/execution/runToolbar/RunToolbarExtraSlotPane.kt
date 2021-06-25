// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.project.Project
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class RunToolbarExtraSlotPane(val project: Project) {
  private val manager = RunToolbarSlotManager.getInstance(project)
  private val slotPane = JPanel(MigLayout("gapx 2, gapy 2, ins 0, novisualpadding, wrap 2"))
  private val components = mutableListOf<SlotComponent>()

  private val managerListener = object : SlotListener {
    override fun slotAdded() {
      addSingleSlot()
    }

    override fun slotRemoved(index: Int) {
      if(index >= 0 && index < components.size) {
        removeComponent(components[index])
      } else {
        rebuild()
      }
    }

    override fun rebuildPopup() {
      rebuild()
    }
  }

  private val pane = object : JPanel(MigLayout("flowy, ins 10, novisualpadding", "", "[grow][min!]")){
    override fun addNotify() {
      super.addNotify()
      manager.addListener(managerListener)
      rebuild()
    }

    override fun removeNotify() {
      manager.removeListener(managerListener)
      super.removeNotify()
    }
  }.apply {
      add(slotPane, "grow")

      add(JPanel(MigLayout("ins 0, novisualpadding", "[min!]push[min!][min!]")).apply {
        this.add(JLabel(AllIcons.Toolbar.AddSlot).apply {
          this.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
              manager.addNewSlot()
            }
          })
        })
        this.add(JLabel(AllIcons.Toolbar.Pin).apply { text = "Dock" })
        this.add(JLabel(AllIcons.General.GearPlain))
      }, "growx")
    }

  internal fun getView(): JComponent = pane

  private fun rebuild() {
    slotPane.removeAll()
    components.clear()

    val count = manager.slotsCount()
    repeat(count) { addNewSlot() }

    pack()
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

    slotPane.add(slot.minus)
    slotPane.add(slot.bar)

    components.add(slot)
  }

  private fun pack() {
    UIUtil.getWindow(pane)?.let {
      if (it.isShowing) {
        it.pack()
      }
    }
  }

  private fun removeComponent(component: SlotComponent) {
    slotPane.remove(component.minus)
    slotPane.remove(component.bar)

    components.remove(component)
    pack()
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

  internal data class SlotComponent(val bar: SegmentedActionToolbarComponent, val minus: JComponent)
}