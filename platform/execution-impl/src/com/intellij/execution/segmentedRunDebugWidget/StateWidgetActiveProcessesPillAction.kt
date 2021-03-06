// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.segmentedRunDebugWidget

import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.stateExecutionWidget.StateWidgetProcess
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

class StateWidgetActiveProcessesPillAction : AnAction(), CustomComponentAction, DumbAware {
  companion object {
    private val defaultFont
      get() = JBUI.Fonts.toolbarFont()
    private val hoveredFont
      get() = createUnderlinedFont()

    private fun createUnderlinedFont(): Font {
      val font = JBUI.Fonts.toolbarFont()

      val attributes = mutableMapOf<TextAttribute, Any?>()
      attributes[TextAttribute.UNDERLINE] = TextAttribute.UNDERLINE_ON
      return font.deriveFont(attributes)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {

  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return Processes()
  }

  private class Processes : JPanel(MigLayout("novisualpadding, hidemode 3, ins 0, gap 0")) {
    private val labels = mutableMapOf<String, JLabel>()
    private val separators = mutableMapOf<String, JLabel>()

    init {
      isOpaque = false
    }

    private val executorRegistry = ExecutorRegistry.getInstance()
    private var project: Project? = null
      set(value) {
        if (field == value) return
        field = value
        if (field == null) {
          clear()
          updateVisible()
        }
      }

    init {
      this@Processes.add(createLabel().apply { text = " " })

      StateWidgetProcess.getProcesses().forEach {
        executorRegistry.getExecutorById(it.executorId)?.let { executor ->
          if (labels.isNotEmpty()) {
            separators[it.ID] = createLabel().apply {
              text = " | "
              this@Processes.add(this)
            }
          }
          labels[it.ID] = ProcessLabel(it.name, it.executorId).apply {
            this@Processes.add(this)
          }
        }
      }

      this@Processes.add(createLabel().apply { text = " " })
    }

    override fun addNotify() {
      super.addNotify()
      clear()
      val frame = SwingUtilities.getWindowAncestor(this)
      project = (frame as? IdeFrame)?.project

      val disp = Disposer.newDisposable()
      Disposer.register(ApplicationManager.getApplication(), disp)
      disposable = disp

      ApplicationManager.getApplication().messageBus.connect(disp)
        .subscribe(StateWidgetManager.TOPIC,
                   object : StateWidgetManager.StateWidgetManagerListener {
                     override fun configurationChanged() {
                       updateVisible()
                     }
                   })

      updateVisible()
    }

    override fun removeNotify() {
      project = null
      super.removeNotify()
    }

    private var disposable: Disposable? = null

    private fun clear() {
      disposable?.let {
        if (!Disposer.isDisposed(it))
          Disposer.dispose(it)
        disposable = null
      }
    }

    private fun updateVisible() {
      labels.keys.forEach {
        labels[it]?.isVisible = false
        separators[it]?.isVisible = false
      }

      project?.let {
        val activeProcessesId = StateWidgetManager.getInstance(it).getActiveProcessesIDs()
        var shown = false
        if (activeProcessesId.isNotEmpty()) {
          labels.keys.forEach {
            if (activeProcessesId.contains(it)) {
              if (shown) {
                separators[it]?.isVisible = true
              }
              shown = true
              labels[it]?.isVisible = true
            }
          }
        }

        this@Processes.isVisible = shown
      }
    }

    private fun createLabel(): JLabel {
      return object : JLabel() {
        override fun getFont(): Font {
          return JBUI.Fonts.toolbarFont()
        }
      }
    }

    inner class ProcessLabel(@NlsContexts.Label text: String, val toolWindowId: String) : JLabel() {
      var hovered = false

      init {
        this.text = text

        addMouseListener(object : MouseAdapter() {
          override fun mouseClicked(e: MouseEvent?) {
            if (e != null) {
              project?.let { project ->
                ToolWindowManager.getInstance(project).getToolWindow(toolWindowId)?.show()
              }
            }
          }

          override fun mouseEntered(e: MouseEvent?) {
            super.mouseEntered(e)
            hovered = true
            repaint()
          }

          override fun mouseExited(e: MouseEvent?) {
            super.mouseExited(e)
            hovered = false
            repaint()
          }
        })
      }

      override fun getFont(): Font {
        return if (hovered) hoveredFont else defaultFont
      }
    }
  }
}