// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.project.Project
import com.intellij.ui.HyperlinkLabel
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

class RunToolbarExtraSlotPane(val project: Project, val baseWidth: () -> Int?, val cancel: () -> Unit): ActiveListener {
  private val manager = RunToolbarSlotManager.getInstance(project)
  val slotPane = JPanel(VerticalLayout(JBUI.scale(3))).apply {
    isOpaque = false
    border = JBUI.Borders.empty()
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

  override fun enabled() {
    manager.addListener(managerListener)

  }

  override fun disabled() {
    manager.removeListener(managerListener)
  }

  private fun updateImmediately() {
  //  components.map { it.bar }.forEach { it.updateActionsImmediately(true) }
  }

  private var added = false
  val details = JLabel(LangBundle.message("run.toolbar.add.slot.details"))
  private val pane = object : JPanel(VerticalLayout(JBUI.scale(2))) {
    override fun addNotify() {
      build()
      super.addNotify()
      added = true
      SwingUtilities.invokeLater {
        pack()
      }
    }

    override fun removeNotify() {
      added = false
      super.removeNotify()
    }
  }.apply {
    border = JBUI.Borders.empty(3, 0, 0, 3)
    add(slotPane)

    val bottomPane = JPanel(MigLayout("ins 0, novisualpadding, gap 0, wrap 2, hidemode 3")).apply {
      isOpaque = false
      border = JBUI.Borders.empty(5, 0, 7, 5)

      add(JLabel(AllIcons.Toolbar.AddSlot).apply {
        val d = preferredSize
        d.width = FixWidthSegmentedActionToolbarComponent.ARROW_WIDTH
        preferredSize = d

        addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            manager.addAndSaveSlot()
          }
        })
      })
      add(HyperlinkLabel(LangBundle.message("run.toolbar.add.slot")).apply {
        addHyperlinkListener {
          manager.addAndSaveSlot()
        }
        border = JBUI.Borders.empty()
      })

      //add(details, "skip")
    }
    add(bottomPane)
  }

  internal fun getView(): JComponent = pane

  private fun rebuild() {
    build()
    if(manager.slotsCount() > 0 && added) {
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
    updateImmediately()
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

    val component = SlotComponent(bar, JLabel(AllIcons.Toolbar.RemoveSlot).apply {
      val d = preferredSize
      d.width = FixWidthSegmentedActionToolbarComponent.ARROW_WIDTH
      preferredSize = d
     })

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
    val view = JPanel(MigLayout("ins 0, gap 0, novisualpadding")).apply {
      add(minus)
      add(bar)
    }
  }
}