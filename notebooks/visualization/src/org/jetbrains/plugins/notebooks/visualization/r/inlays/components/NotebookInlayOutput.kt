/*
 * Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.execution.process.ProcessOutputType
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.ui.RelativeFont
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Font
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import kotlin.math.min

class ProcessOutput(val text: String, kind: Key<*>) {
  private val kindValue: Int = when(kind) {
    ProcessOutputTypes.STDOUT -> 1
    ProcessOutputTypes.STDERR -> 2
    else -> 3
  }

  val kind: Key<*>
    get() = when (kindValue) {
      1 -> ProcessOutputType.STDOUT
      2 -> ProcessOutputType.STDERR
      else -> ProcessOutputType.SYSTEM
    }
}


/** Notebook console logs, HTML, and table result view. */
class NotebookInlayOutput(private val editor: Editor, private val parent: Disposable) : NotebookInlayState(), ToolBarProvider {

  init {
    layout = BorderLayout()
  }

  companion object {
    private const val RESIZE_TASK_NAME = "Resize graphics"
    private const val RESIZE_TASK_IDENTITY = "Resizing graphics"
    private const val RESIZE_TIME_SPAN = 500

    private val monospacedFont = RelativeFont.NORMAL.family(Font.MONOSPACED)
    private val outputFont = monospacedFont.derive(StartupUiUtil.getLabelFont().deriveFont(UIUtil.getFontSize(UIUtil.FontSize.SMALL)))
  }

  private var output: InlayOutput? = null


  private fun addTableOutput() = createOutput { parent, editor, clearAction -> InlayOutputTable(parent, editor, clearAction) }

  private fun addTextOutput() = createOutput {  parent, editor, clearAction ->  InlayOutputText(parent, editor, clearAction) }

  private fun addHtmlOutput() = createOutput {  parent, editor, clearAction ->  InlayOutputHtml(parent, editor, clearAction) }

  private fun addImgOutput() = createOutput {  parent, editor, clearAction ->  InlayOutputImg(parent, editor, clearAction) }

  private inline fun <T: InlayOutput> createOutput(constructor: (Disposable, Editor, () -> Unit) -> T) =
    constructor(parent, editor, clearAction).apply { setupOutput(this) }

  private fun setupOutput(output: InlayOutput) {
    this.output?.let { remove(it.getComponent()) }
    this.output = output
    output.onHeightCalculated = { height -> onHeightCalculated?.invoke(height) }
    add(output.getComponent(), BorderLayout.CENTER)

    addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        if (output.isFullWidth) {
          output.getComponent().bounds = Rectangle(0, 0, e.component.bounds.width, e.component.bounds.height)
        } else {
          output.getComponent().bounds = Rectangle(0, 0, min(output.getComponent().preferredSize.width, e.component.bounds.width),
                                                   e.component.bounds.height)
        }
      }
    })
    if (addToolbar) {
      output.addToolbar()
    }
  }

  private var addToolbar = false

  fun addToolbar() {
    addToolbar = true
    output?.addToolbar()
  }

  private fun getOrAddTextOutput(): InlayOutputText {
    (output as? InlayOutputText)?.let { return it }
    return addTextOutput()
  }

  fun addData(type: String, data: String, progressStatus: InlayProgressStatus?): InlayOutputProvider? {
    val provider = InlayOutputProvider.EP.extensionList.asSequence().filter { it.acceptType(type) }.firstOrNull()
    val inlayOutput: InlayOutput
    if (provider != null) {
      inlayOutput = output.takeIf { it?.acceptType(type) == true } ?: createOutput { parent, editor, clearAction ->
        provider.create(parent, editor, clearAction)
      }
    }
    else {
      inlayOutput = when (type) {
        "TABLE" -> output?.takeIf { it is InlayOutputTable } ?: addTableOutput()
        "HTML", "URL" -> output?.takeIf { it is InlayOutputHtml } ?: addHtmlOutput()
        "IMG", "IMGBase64", "IMGSVG" -> output?.takeIf { it is InlayOutputImg } ?: addImgOutput()
        else -> getOrAddTextOutput()
      }
    }
    progressStatus?.let {
      inlayOutput.updateProgressStatus(editor, it)
    }
    inlayOutput.addData(data, type)
    return provider
  }

  fun addText(message: String, outputType: Key<*>) {
    getOrAddTextOutput().addData(message, outputType)
  }

  override fun updateProgressStatus(progressStatus: InlayProgressStatus) {
    output?.updateProgressStatus(editor, progressStatus)
  }

  override fun clear() {
    output?.clear()
  }

  override fun getCollapsedDescription(): String {
    return if (output == null) "" else output!!.getCollapsedDescription()
  }

  override fun onViewportChange(isInViewport: Boolean) {
    output?.onViewportChange(isInViewport)
  }

  override fun createActions(): List<AnAction> = output?.actions ?: emptyList()
}
