// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.icons.AllIcons
import com.intellij.java.compiler.charts.CompilationChartsBundle
import com.intellij.java.compiler.charts.CompilationChartsViewModel
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


class ActionPanel(private val project: Project, private val vm: CompilationChartsViewModel, private val component: JComponent) : BorderLayoutPanel() {
  private val searchField: JBTextField = object : ExtendableTextField() {
    val reset = Runnable {
      vm.filter.set(vm.filter.value.setText(listOf()))
      text = ""
      updateLabel(null, null)
    }
    init {
      setExtensions(object : ExtendableTextComponent.Extension {
        override fun getIcon(hovered: Boolean) = AllIcons.Actions.Search
        override fun isIconBeforeText() = true
        override fun getIconGap() = scale(6)
      }, object : ExtendableTextComponent.Extension {
        override fun getIcon(hovered: Boolean) = IconButton(
          CompilationChartsBundle.message("charts.reset"),
          AllIcons.Actions.Close,
          AllIcons.Actions.CloseHovered
        )

        override fun isIconBeforeText() = false
        override fun getIconGap() = scale(6)
        override fun getActionOnClick() = reset
      })

      document.addDocumentListener(object : DocumentListener {
        override fun insertUpdate(e: DocumentEvent) = updateFilter()
        override fun removeUpdate(e: DocumentEvent) = updateFilter()
        override fun changedUpdate(e: DocumentEvent) = updateFilter()

        private fun updateFilter() {
          val words = text.split(" ").filter { it.isNotBlank() }.map { it.trim() }
          if (words.isEmpty()) {
            vm.filter.set(vm.filter.value.setText(listOf()))
            updateLabel(null, null)
          } else {
            vm.filter.set(vm.filter.value.setText(words))
            updateLabel(vm.modules.get().keys, vm.filter.value)
          }
        }
      })

      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          if (e.keyCode == KeyEvent.VK_ESCAPE) reset.run()
        }
      })
      border = JBUI.Borders.empty(1)
      BoxLayout(this, BoxLayout.LINE_AXIS)
    }
  }

  private val countLabel = JBLabel("").apply {
    border = JBUI.Borders.emptyLeft(5)
    fontColor = UIUtil.FontColor.BRIGHTER
  }

  init {
    border = JBUI.Borders.emptyBottom(2)
    layout = BorderLayout()

    // module name
    addToLeft(JPanel().apply {
      layout = BoxLayout(this, BoxLayout.LINE_AXIS)
      border = JBUI.Borders.empty(2)
      add(JBLabel(CompilationChartsBundle.message("charts.module")))
      add(searchField)
      add(countLabel)
    })

    // legend
    val actionGroup = DefaultActionGroup(listOf(CompilationChartsStatsActionHolder(vm), Separator(), ScrollToEndAction(vm)))
    val toolbar = ActionManager.getInstance().createActionToolbar(COMPILATION_CHARTS_TOOLBAR_NAME, actionGroup, true).apply {
      targetComponent = this@ActionPanel
    }
    addToRight(toolbar.component)

    DumbAwareAction.create {
      val focusManager = IdeFocusManager.getInstance(project)
      if (focusManager.getFocusedDescendantFor(this@ActionPanel.component) != null) {
        focusManager.requestFocus(searchField, true)
      }
    }.registerCustomShortcutSet(ActionManager.getInstance().getAction(IdeActions.ACTION_FIND).shortcutSet, this@ActionPanel.component)
  }

  fun updateLabel(set: Set<CompilationChartsViewModel.Modules.EventKey>?, filter: CompilationChartsViewModel.Filter?) {
    if (set == null || filter == null) {
      countLabel.text = ""
    } else {
      val count = set.count { filter.test(it) }
      if (count == set.count()) {
        countLabel.text = ""
      } else {
        countLabel.text = CompilationChartsBundle.message("charts.search.results", count)
      }
    }
  }

  private class ScrollToEndAction(private val vm : CompilationChartsViewModel): DumbAwareAction(
    CompilationChartsBundle.message("charts.scroll.to.end.action.title"),
    CompilationChartsBundle.message("charts.scroll.to.end.action.description"),
    AllIcons.Actions.Forward) {
    override fun actionPerformed(e: AnActionEvent) = vm.requestScrollToEnd()
  }

  private class CompilationChartsStatsActionHolder(private val vm: CompilationChartsViewModel) : DumbAwareAction(), CustomComponentAction {

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.LINE_AXIS)
      border = JBUI.Borders.empty(2)
      add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT)).apply {
        val block = JBLabel().apply {
          preferredSize = Dimension(10, 10)
          isOpaque = true
          background = COLOR_PRODUCTION_BLOCK
          border = BorderFactory.createLineBorder(COLOR_PRODUCTION_BORDER, 1)
        }
        add(block)
        add(JBLabel(CompilationChartsBundle.message("charts.production.type")))

        addMouseListener(object : MouseAdapter() {
          override fun mouseEntered(e: MouseEvent) {
            block.border = BorderFactory.createLineBorder(COLOR_PRODUCTION_BORDER_SELECTED, 1)
          }

          override fun mouseExited(e: MouseEvent) {
            block.border = BorderFactory.createLineBorder(COLOR_PRODUCTION_BORDER, 1)
          }

          override fun mouseClicked(e: MouseEvent) {
            vm.filter.set(vm.filter.value.setProduction(!vm.filter.value.production))
            if (vm.filter.value.production)
              block.background = COLOR_PRODUCTION_BLOCK
            else
              block.background = COLOR_PRODUCTION_BLOCK_DISABLED
          }
        })

      })

      add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT)).apply {
        val block = JBLabel().apply {
          preferredSize = Dimension(10, 10)
          isOpaque = true
          background = COLOR_TEST_BLOCK
          border = BorderFactory.createLineBorder(COLOR_TEST_BORDER)
        }
        add(block)
        add(JBLabel(CompilationChartsBundle.message("charts.test.type")))
        addMouseListener(object : MouseAdapter() {
          override fun mouseEntered(e: MouseEvent) {
            block.border = BorderFactory.createLineBorder(COLOR_TEST_BORDER_SELECTED, 1)
          }

          override fun mouseExited(e: MouseEvent) {
            block.border = BorderFactory.createLineBorder(COLOR_TEST_BORDER, 1)
          }

          override fun mouseClicked(e: MouseEvent) {
            vm.filter.set(vm.filter.value.setTest(!vm.filter.value.test))
            if (vm.filter.value.test)
              block.background = COLOR_TEST_BLOCK
            else
              block.background = COLOR_TEST_BLOCK_DISABLED
          }
        })
      })

      add(JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT)).apply {
        val block = JBLabel().apply {
          preferredSize = Dimension(10, 2)
          isOpaque = true
          background = COLOR_MEMORY_BORDER
        }
        val label = JBLabel(CompilationChartsBundle.message("charts.memory.type"))
        add(block)
        add(label)

        addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent) {
            when (vm.cpuMemory.value) {
              CompilationChartsViewModel.CpuMemoryStatisticsType.MEMORY -> {
                label.text = CompilationChartsBundle.message("charts.cpu.type")
                block.background = COLOR_CPU_BORDER
                vm.cpuMemory.set(CompilationChartsViewModel.CpuMemoryStatisticsType.CPU)
              }
              CompilationChartsViewModel.CpuMemoryStatisticsType.CPU -> {
                label.text = CompilationChartsBundle.message("charts.memory.type")
                block.background = COLOR_MEMORY_BORDER
                vm.cpuMemory.set(CompilationChartsViewModel.CpuMemoryStatisticsType.MEMORY)
              }
            }
          }
        })
      })
    }
    override fun actionPerformed(e: AnActionEvent) = Unit
  }

  companion object {
    private const val COMPILATION_CHARTS_TOOLBAR_NAME = "Compilation charts toolbar"
  }
}