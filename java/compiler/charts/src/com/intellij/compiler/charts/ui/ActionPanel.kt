// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.charts.ui

import com.intellij.compiler.charts.CompilationChartsBundle
import com.intellij.compiler.charts.CompilationChartsViewModel
import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.JBUI
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
import javax.swing.JPanel


class ActionPanel(private val vm: CompilationChartsViewModel) : BorderLayoutPanel() {
  private val searchField: JBTextField = object : ExtendableTextField() {
    val reset = Runnable {
      vm.filter.set(vm.filter.value.setText(listOf()))
      text = ""
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

      addActionListener { _ ->
        val words = this.text.split(" ").filter { it.isNotBlank() }.map { it.trim() }
        if (words.isEmpty()) {
          vm.filter.set(vm.filter.value.setText(listOf()))
        }
        else {
          vm.filter.set(vm.filter.value.setText(words))
        }
      }
      addKeyListener(object : KeyAdapter() {
        override fun keyPressed(e: KeyEvent) {
          if (e.keyCode == KeyEvent.VK_ESCAPE) reset.run()
        }
      })
      border = JBUI.Borders.empty(1)
      BoxLayout(this, BoxLayout.LINE_AXIS)
    }
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
    })

    // legend
    addToRight(JPanel().apply {
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
            when(vm.cpuMemory.value) {
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
    })
  }
}