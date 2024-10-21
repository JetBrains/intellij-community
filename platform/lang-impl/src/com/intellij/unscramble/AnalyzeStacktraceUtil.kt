// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.unscramble

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.impl.ConsoleViewUtil
import com.intellij.execution.ui.*
import com.intellij.lang.LangBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ClipboardUtil
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

class AnalyzeStacktraceUtil private constructor(){
  @ApiStatus.Internal
  companion object {

    @JvmField
    val EP_NAME: ProjectExtensionPointName<Filter> = ProjectExtensionPointName<Filter>("com.intellij.analyzeStacktraceFilter")

    @ApiStatus.Experimental
    @JvmField
    val EP_CONTENT_PROVIDER: ProjectExtensionPointName<StacktraceTabContentProvider> = ProjectExtensionPointName<StacktraceTabContentProvider>(
      "com.intellij.analyzeStacktraceRunContentProvider")

    @JvmStatic
    fun printStacktrace(consoleView: ConsoleView, unscrambledTrace: String) {
      ThreadingAssertions.assertEventDispatchThread()
      consoleView.apply {
        clear()
        print(unscrambledTrace + "\n", ConsoleViewContentType.ERROR_OUTPUT)
        scrollTo(0)
      }
    }

    @JvmStatic
    fun addConsole(
      project: Project,
      consoleFactory: ConsoleFactory?,
      tabTitle: @NlsContexts.TabTitle String?,
      text: String,
    ) {
      addConsole(project, consoleFactory, tabTitle, text, null)
    }

    @JvmOverloads
    @JvmStatic
    fun addConsole(
      project: Project,
      consoleFactory: ConsoleFactory?,
      tabTitle: @NlsContexts.TabTitle String?,
      text: String,
      icon: Icon?,
      withExecutor: Boolean = true,
    ): RunContentDescriptor {
      val builder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
      builder.filters(EP_NAME.getExtensions(project))
      val consoleView = builder.getConsole()

      val toolbarActions = DefaultActionGroup()
      val consoleComponent = consoleFactory?.createConsoleComponent(consoleView, toolbarActions)
                             ?: MyConsolePanel(consoleView, toolbarActions)

      val descriptor: RunContentDescriptor =
        object : RunContentDescriptor(consoleView, null, consoleComponent, tabTitle, icon) {
          override fun isContentReuseProhibited(): Boolean {
            return true
          }
        }

      for (action in consoleView.createConsoleActions()) {
        toolbarActions.add(action)
      }
      val console = consoleView as ConsoleViewImpl
      ConsoleViewUtil.enableReplaceActionForConsoleViewEditor(console.editor!!)
      console.editor!!.getSettings().setCaretRowShown(true)
      toolbarActions.add(ActionManager.getInstance().getAction("AnalyzeStacktraceToolbar"))

      if (withExecutor) {
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val runContentManager = RunContentManager.getInstance(project)
        runContentManager.showRunContent(executor, descriptor)

        EP_CONTENT_PROVIDER.getExtensions(project).forEach { provider ->
          runWithModalProgressBlocking(project, LangBundle.message("unscramble.progress.title.analyzing.stacktrace")) {
            provider.createRunTabDescriptor(project, text)?.let { contentDescriptor ->
              withContext(Dispatchers.EDT) {
                runContentManager.showRunContent(executor, contentDescriptor)
              }
            }
          }
        }
      }
      consoleView.allowHeavyFilters()
      if (consoleFactory == null) {
        printStacktrace(consoleView, text)
      }
      return descriptor
    }

    @JvmStatic
    fun createEditorPanel(project: Project?, parentDisposable: Disposable): StacktraceEditorPanel {
      val editorFactory = EditorFactory.getInstance()
      val document = editorFactory.createDocument("")
      val editor = editorFactory.createEditor(document, project)
      editor.getSettings().apply {
        setFoldingOutlineShown(false)
        setLineMarkerAreaShown(false)
        setIndentGuidesShown(false)
        setLineNumbersShown(false)
        setRightMarginShown(false)
      }

      val editorPanel = StacktraceEditorPanel(project, editor).apply {
        preferredSize = JBUI.size(600, 400)
      }
      Disposer.register(parentDisposable, editorPanel)
      return editorPanel
    }
  }


  interface ConsoleFactory {
    fun createConsoleComponent(consoleView: ConsoleView?, toolbarActions: DefaultActionGroup?): JComponent?
  }

  private class MyConsolePanel(consoleView: ExecutionConsole, toolbarActions: ActionGroup) : JPanel(BorderLayout()) {
    init {
      val toolbarPanel = JPanel(BorderLayout())
      val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.ANALYZE_STACKTRACE_PANEL_TOOLBAR, toolbarActions, false)
      toolbar.setTargetComponent(consoleView.getComponent())
      toolbarPanel.add(toolbar.getComponent())
      add(toolbarPanel, BorderLayout.WEST)
      add(consoleView.getComponent(), BorderLayout.CENTER)
    }
  }

  class StacktraceEditorPanel(private val myProject: Project?, val editor: Editor) : JPanel(BorderLayout()), UiDataProvider, Disposable {
    init {
      add(editor.getComponent())
    }

    override fun uiDataSnapshot(sink: DataSink) {
      sink.set<Editor>(CommonDataKeys.EDITOR, this.editor)
    }

    fun pasteTextFromClipboard() {
      ClipboardUtil.getTextInClipboard()?.let { text -> this.text = text }
    }

    override fun dispose() {
      EditorFactory.getInstance().releaseEditor(this.editor)
    }

    var text: String
      get() = editor.getDocument().text
      set(text) {
        CommandProcessor.getInstance().executeCommand(myProject, Runnable {
          ApplicationManager.getApplication().runWriteAction(Runnable {
            val document = editor.getDocument()
            document.replaceString(0, document.textLength, StringUtil.convertLineSeparators(text))
          })
        }, "", this)
      }

    val editorComponent: JComponent
      get() = editor.getContentComponent()
  }
}
