/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.SoftWrapChangeListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.JBUI
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.notebooks.visualization.r.VisualizationBundle
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.progress.InlayProgressStatus
import org.jetbrains.plugins.notebooks.visualization.r.inlays.dataframe.DataFrameCSVAdapter
import org.jetbrains.plugins.notebooks.visualization.r.inlays.runAsyncInlay
import org.jetbrains.plugins.notebooks.visualization.r.ui.ToolbarUtil
import org.jetbrains.plugins.notebooks.visualization.r.ui.UiCustomizer
import java.awt.Dimension
import java.awt.Graphics
import java.io.File
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min

abstract class InlayOutput(
  parent: Disposable,
  val editor: Editor,
  val actions: List<AnAction>,
) {
  // Transferring `this` from the constructor to another class violates JMM and leads to undefined behaviour
  // when accessing `toolbarPane` inside constructor and when `toolbarPane` accesses `this`. So, be careful.
  // Since this is an abstract class with many inheritors, the only way to get rid of this issue is to convert
  // the class to the interface (or make the constructor private) and initialize `toolbarPane` inside some
  // factory method.
  @Suppress("LeakingThis")
  protected val toolbarPane = ToolbarPane(this)

  protected val project: Project = editor.project ?: error("Editor should have a project")

  /** If the output should occupy as much editor width as possible. */
  open val isFullWidth = true

  fun getComponent() = toolbarPane

  /** Clears view, removes text/html. */
  abstract fun clear()

  abstract fun addData(data: String, type: String)
  abstract fun scrollToTop()
  abstract fun getCollapsedDescription(): String

  abstract fun acceptType(type: String): Boolean

  fun updateProgressStatus(editor: Editor, progressStatus: InlayProgressStatus) {
    toolbarPane.progressComponent = UiCustomizer.instance.buildInlayProgressStatusComponent(progressStatus, editor)
  }

  private fun getProgressStatusHeight(): Int {
    return toolbarPane.progressComponent?.height ?: 0
  }

  /**
   * HTML output returns the height delayed from it's Platform.runLater.
   */
  var onHeightCalculated: ((height: Int) -> Unit)? = null
    set(value) {
      field = { height: Int ->
        value?.invoke(height + getProgressStatusHeight())
      }
    }

  private val disposable: Disposable = Disposer.newDisposable()

  init {
    Disposer.register(parent, disposable)
  }

  open fun onViewportChange(isInViewport: Boolean) {
    // Do nothing by default
  }

  open fun addToolbar() {
    toolbarPane.toolbarComponent = createToolbar()
  }

  private fun createToolbar(): JComponent {
    val toolbar = ToolbarUtil.createEllipsisToolbar("NotebooksInlayOutput", actions)

    toolbar.targetComponent = toolbarPane // ToolbarPane will be in context.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)
    toolbar.component.isOpaque = true
    toolbar.component.background = UiCustomizer.instance.getTextOutputBackground(editor)

    return toolbar.component
  }

  protected fun saveWithFileChooser(@Nls title: String,
                                    @Nls description: String,
                                    extension: Array<String>,
                                    defaultName: String,
                                    onChoose: (File) -> Unit) {
    InlayOutputUtil.saveWithFileChooser(project, title, description, extension, defaultName, true, onChoose)
  }

  /** marker interface for [SaveOutputAction] */
  interface WithSaveAs {
    fun saveAs()
  }

  /** marker interface for [CopyImageToClipboardAction] */
  interface WithCopyImageToClipboard {
    fun copyImageToClipboard()
  }

  companion object {
    fun getToolbarPaneOrNull(e: AnActionEvent): ToolbarPane? =
      e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? ToolbarPane

    inline fun <reified T> getInlayOutput(e: AnActionEvent): T? =
      getToolbarPaneOrNull(e)?.inlayOutput as? T

    fun loadActions(vararg ids: String): List<AnAction> {
      val actionManager = ActionManager.getInstance()
      return ids.mapNotNull { actionManager.getAction(it) }
    }
  }
}

class InlayOutputText(parent: Disposable, editor: Editor)
  : InlayOutput(parent, editor, loadActions(SaveOutputAction.ID)), InlayOutput.WithSaveAs {

  private val console = ColoredTextConsole(project, viewer = true)

  private val scrollPaneTopBorderHeight = 5

  init {
    Disposer.register(parent, console)
    toolbarPane.dataComponent = console.component

    val consoleEditor = console.editor as EditorEx
    initOutputTextConsole(editor, consoleEditor, scrollPaneTopBorderHeight)
    ApplicationManager.getApplication().messageBus.connect(console)
      .subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
        updateOutputTextConsoleUI(consoleEditor, editor)
        consoleEditor.component.repaint()
      })
  }

  override fun clear() {
    console.clear()
  }

  override fun addData(data: String, type: String) {
    runAsyncInlay {
      File(data).takeIf { it.exists() && it.extension == "json" }?.let { file ->
        Gson().fromJson<List<ProcessOutput>>(file.readText(), object : TypeToken<List<ProcessOutput>>() {}.type)
      }.let { outputs ->
        SwingUtilities.invokeLater {
          if (outputs == null) {
            // DS-763 "\r\n" patterns would trim the whole last line.
            console.addData(data.replace("\r\n", "\n").trimEnd('\n'), ProcessOutputType.STDOUT)
          }
          else {
            outputs.forEach { console.addData(it.text, it.kind) }
          }
          console.flushDeferredText()

          (console.editor as? EditorImpl)?.apply {
            updateSize(this)

            softWrapModel.setSoftWrapPainter(EmptySoftWrapPainter)
            softWrapModel.addSoftWrapChangeListener(
              object : SoftWrapChangeListener {
                override fun recalculationEnds() = updateSize(this@apply)

                override fun softWrapsChanged() {}
              }
            )
          }
        }
      }
    }
  }

  private fun updateSize(editor: EditorImpl) {
    with(editor) {
      val textHeight = offsetToXY(document.textLength).y + lineHeight + scrollPaneTopBorderHeight
      component.preferredSize = Dimension(preferredSize.width, textHeight)
      onHeightCalculated?.invoke(max(textHeight, toolbarPane.preferredSize.height))
    }
  }

  fun addData(message: String, outputType: Key<*>) {
    console.addData(message, outputType)
  }

  override fun scrollToTop() {
    console.scrollTo(0)
  }

  override fun getCollapsedDescription(): String {
    return console.text.substring(0, min(console.text.length, 60)) + " ....."
  }

  override fun acceptType(type: String): Boolean {
    return type == "TEXT"
  }

  override fun saveAs() {
    val title = VisualizationBundle.message("inlay.action.export.as.txt.title")
    val description = VisualizationBundle.message("inlay.action.export.as.txt.description")
    saveWithFileChooser(title, description, arrayOf("txt"), "output") { destination ->
      destination.bufferedWriter().use { out ->
        out.write(console.text)
      }
    }
  }
}

object EmptySoftWrapPainter : SoftWrapPainter {
  override fun paint(g: Graphics, drawingType: SoftWrapDrawingType, x: Int, y: Int, lineHeight: Int) = 0

  override fun getDrawingHorizontalOffset(g: Graphics, drawingType: SoftWrapDrawingType, x: Int, y: Int, lineHeight: Int) = 0

  override fun getMinDrawingWidth(drawingType: SoftWrapDrawingType) = 0

  override fun canUse() = true

  override fun reinit() {}
}

internal class NotebookOutputSelectAllAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)

    // for some reason, EDITOR and HOST_EDITOR are always null for console output components. this is a workaround.
    val editor = e.getData(PlatformDataKeys.EDITOR_EVEN_IF_INACTIVE)

    e.presentation.isEnabled = editor?.contentComponent?.hasFocus() == true && editor.getUserData(NOTEBOOKS_CONSOLE_OUTPUT_KEY) == true
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(PlatformDataKeys.EDITOR_EVEN_IF_INACTIVE) ?: return
    editor.selectionModel.setSelection(0, editor.document.text.length)
  }
}

private val NOTEBOOKS_CONSOLE_OUTPUT_KEY = Key.create<Boolean>("NOTEBOOKS_CONSOLE_OUTPUT")

fun initOutputTextConsole(editor: Editor,
                          consoleEditor: EditorEx,
                          scrollPaneTopBorderHeight: Int) {
  updateOutputTextConsoleUI(consoleEditor, editor)
  consoleEditor.apply {
    scrollPane.border = IdeBorderFactory.createEmptyBorder(JBUI.insetsTop(scrollPaneTopBorderHeight))
    putUserData(NOTEBOOKS_CONSOLE_OUTPUT_KEY, true)
  }

  consoleEditor.settings.isUseSoftWraps = true
}

/**
 * Changes the color scheme of consoleEditor to the color scheme of the main editor, if required.
 * [editor] is a main notebook editor, [consoleEditor] editor of particular console output.
 */
fun updateOutputTextConsoleUI(consoleEditor: EditorEx, editor: Editor) {
  if (consoleEditor.colorsScheme != editor.colorsScheme) {
    consoleEditor.colorsScheme = editor.colorsScheme
  }
}

class InlayOutputHtml(parent: Disposable, editor: Editor)
  : InlayOutput(parent, editor, loadActions(SaveOutputAction.ID)), InlayOutput.WithSaveAs {

  private val jbBrowser: JBCefBrowser = JBCefBrowser().also { Disposer.register(parent, it) }
  private val heightJsCallback = JBCefJSQuery.create(jbBrowser as JBCefBrowserBase)
  private val saveJsCallback = JBCefJSQuery.create(jbBrowser as JBCefBrowserBase)
  private var height: Int = 0

  init {
    heightJsCallback.addHandler {
      val height = it.toInt()
      if (this.height != height) {
        this.height = height
        invokeLater {
          SwingUtilities.invokeLater {
            onHeightCalculated?.invoke(height)
          }
        }
      }
      JBCefJSQuery.Response("OK")
    }
    Disposer.register(jbBrowser, heightJsCallback)
    toolbarPane.dataComponent = jbBrowser.component
  }

  override fun acceptType(type: String): Boolean {
    return type == "HTML" || type == "URL"
  }

  override fun clear() {}

  private fun notifySize() {
    jbBrowser.cefBrowser.executeJavaScript(
      "var body = document.body,"
      + "html = document.documentElement;"
      + "var height = Math.max( body.scrollHeight, body.offsetHeight, html.clientHeight, html.scrollHeight , html.offsetHeight );"
      + "window.${heightJsCallback.funcName}({request: String(height)});",
      jbBrowser.cefBrowser.url, 0
    )
  }

  override fun addData(data: String, type: String) {
    val isUrl = data.startsWith("file://") || data.startsWith("http://") || data.startsWith("https://")
    if (isUrl) {
      jbBrowser.loadURL(data)
    }
    else {
      jbBrowser.loadHTML("<head><style>" + GithubMarkdownCss.css + " </style></head><body>" + data + "</body>")
    }
    jbBrowser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
      override fun onLoadingStateChange(browser: CefBrowser?, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
        notifySize()
      }
    }, jbBrowser.cefBrowser)
  }

  // For HTML component no need to scroll to top, because it is not scrolling to end.
  override fun scrollToTop() {}

  override fun getCollapsedDescription(): String {
    return "html output"
  }

  override fun saveAs() {
    val title = VisualizationBundle.message("inlay.action.export.as.txt.title")
    val description = VisualizationBundle.message("inlay.action.exports.range.csv.description")
    saveWithFileChooser(title, description, arrayOf("txt"), "output") { destination ->
      saveJsCallback.addHandler(object : java.util.function.Function<String, JBCefJSQuery.Response> {
        override fun apply(selection: String): JBCefJSQuery.Response {
          destination.bufferedWriter().use { out ->
            out.write(selection)
          }
          saveJsCallback.removeHandler(this)
          return JBCefJSQuery.Response("OK")
        }
      })
      jbBrowser.cefBrowser.executeJavaScript("window.${saveJsCallback.funcName}({request: window.getSelection().toString()})",
                                             jbBrowser.cefBrowser.url, 0)
    }
  }
}

class InlayOutputTable(val parent: Disposable, editor: Editor)
  : InlayOutput(parent, editor, loadActions()) {

  private val inlayTablePage: InlayTablePage = InlayTablePage()

  init {
    toolbarPane.dataComponent = inlayTablePage
  }

  override fun clear() {}

  override fun addData(data: String, type: String) {
    val dataFrame = DataFrameCSVAdapter.fromCsvString(data)
    inlayTablePage.setDataFrame(dataFrame)
  }

  override fun scrollToTop() {}

  override fun getCollapsedDescription(): String = "Table output"

  override fun acceptType(type: String): Boolean = type == "TABLE"
}
