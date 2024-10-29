// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.runToolbar.data.RWActiveListener
import com.intellij.execution.runToolbar.data.RWSlotListener
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.ToolbarSettings
import com.intellij.lang.LangBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.segmentedActionBar.SegmentedActionToolbarComponent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.ComponentUtil
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

@ApiStatus.Internal
class RunToolbarExtraSlotPane(val project: Project, val baseWidth: () -> Int?): RWActiveListener {
  private val manager = RunToolbarSlotManager.getInstance(project)
  private val slotPane = JPanel(VerticalLayout(JBUI.scale(3))).apply {
    isOpaque = false
    border = JBUI.Borders.empty()
  }

  private val components = mutableListOf<SlotComponent>()

  private val managerListener = object : RWSlotListener {
    override fun slotAdded() {
      addSingleSlot()
    }

    override fun slotRemoved(index: Int) {
      if (index >= 0 && index < components.size) {
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

  init {
    manager.activeListener.addListener(this)
  }

  fun clear() {
    manager.activeListener.removeListener(this)
  }

  override fun enabled() {
    manager.slotListeners.addListener(managerListener)
  }

  override fun disabled() {
    manager.slotListeners.removeListener(managerListener)
  }

  private var added = false
  private val newSlotDetails = object : JLabel(LangBundle.message("run.toolbar.add.slot.details")){
    override fun getFont(): Font {
      return JBUI.Fonts.toolbarFont()
    }
  }.apply {
    border = JBUI.Borders.empty()
    isEnabled = false
  }

  private val pane = object : JPanel(VerticalLayout(JBUI.scale(2))) {
    override fun addNotify() {
      build()
      added = true
      super.addNotify()
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
    background = JBColor.namedColor("Panel.background", Gray.xCD)

    add(slotPane)

    val bottomPane = object : JPanel(MigLayout("fillx, ins 0, novisualpadding, gap 0, hidemode 2, wrap 3", "[][]push[]")) {
      override fun getPreferredSize(): Dimension {
        val preferredSize = super.getPreferredSize()
        baseWidth()?.let {
          preferredSize.width = it
        }
        return preferredSize
      }
    }.apply {
      isOpaque = false
      border = JBUI.Borders.empty(5, 0, 7, 5)

      add(JLabel(AllIcons.Toolbar.AddSlot).apply {
        val d = preferredSize
        d.width = RunWidgetWidthHelper.getInstance(project).arrow
        preferredSize = d

        addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            manager.addAndSaveSlot()
          }
        })
      })
      add(LinkLabel<Unit>(LangBundle.message("run.toolbar.add.slot"), null).apply {
        setListener(
          {_, _ ->
            manager.addAndSaveSlot()
          }, null)
      })

      add(JLabel(AllIcons.General.GearPlain).apply {
        addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, RunToolbarSettingsConfigurable::class.java)
          }
        })

        isVisible = ToolbarSettings.getInstance().isAvailable && RunToolbarProcess.isSettingsAvailable
      })

      add(newSlotDetails, "skip")
    }
    add(bottomPane)
  }

  internal fun getView(): JComponent = pane

  private fun rebuild() {
    build()
    pack()
  }

  private fun build() {
    val count = manager.slotsCount()
    slotPane.removeAll()
    components.clear()
    repeat(count) { addNewSlot() }
  }

  private fun addSingleSlot() {
    addNewSlot()
    pack()
  }

  private fun removeSingleComponent(component: SlotComponent) {
    removeComponent(component)
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

    components.add(slot)
    slotPane.add(slot.view)

  }

  fun pack() {
    newSlotDetails.isVisible = manager.slotsCount() == 0

    slotPane.revalidate()
    pane.revalidate()

    slotPane.repaint()
    pane.repaint()
    ComponentUtil.getWindow(pane)?.let {
      if (it.isShowing) {
        it.pack()
      }
    }
  }

  private fun removeComponent(component: SlotComponent) {
    slotPane.remove(component.view)
    components.remove(component)
  }

  private fun getData(component: SlotComponent): SlotDate? {
    val index = components.indexOf(component)
    if(index < 0) return null
    return manager.getData(index)
  }

  private fun createComponent(): SlotComponent {
    val group = DefaultActionGroup()
    val bar = FixWidthSegmentedActionToolbarComponent(ActionPlaces.MAIN_TOOLBAR, group)

    val component = SlotComponent(bar, JLabel(AllIcons.Toolbar.RemoveSlot).apply {
      val d = preferredSize
      d.width = RunWidgetWidthHelper.getInstance(project).arrow
      preferredSize = d
     })

    bar.targetComponent = bar
    DataManager.registerDataProvider(bar, DataProvider {
      key ->
      if(RunToolbarData.RUN_TOOLBAR_DATA_KEY.`is`(key)) {
        getData(component)
      }
      else if(RunToolbarProcessData.RW_SLOT.`is`(key)) {
        getData(component)?.id
      } else
        null
    })

    val actionManager = ActionManager.getInstance()
    val runToolbarActionsGroup = actionManager.getAction(
      "RunToolbarActionsGroup") as DefaultActionGroup
    group.addAll(runToolbarActionsGroup)

    return component
  }

  internal data class SlotComponent(val bar: SegmentedActionToolbarComponent, val minus: JComponent) {
    val view = JPanel(MigLayout("ins 0, gap 0, novisualpadding")).apply {
      add(minus)
      add(bar)
    }
  }
}