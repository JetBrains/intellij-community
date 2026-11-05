// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.utils.JBCefLocalRequestHandler
import com.intellij.ui.jcef.utils.JBCefStreamResourceHandler
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Image
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextArea

internal class ClipboardTest : JBCefTestAppFrame.TestCase() {
  private val myComponent = JPanel(BorderLayout())
  private val bridgeMode = ComboBox(BridgeMode.entries.toTypedArray())
  private val recreateButton = JButton("Recreate browser")
  private val swingTextArea = JTextArea("Text in a Swing component.")
  private val clipboardLog = JTextArea().apply {
    isEditable = false
    lineWrap = true
    wrapStyleWord = true
  }

  private lateinit var browserSplitPane: JSplitPane
  private var browser: JBCefBrowser? = null

  override fun getComponent(): Component = myComponent

  override fun getDisplayName(): String = "Clipboard"

  override fun initializeImpl() {
    myComponent.add(createControls(), BorderLayout.NORTH)
    browserSplitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, JPanel(), createSwingPanel()).apply {
      resizeWeight = 0.6
    }
    myComponent.add(browserSplitPane, BorderLayout.CENTER)

    bridgeMode.addActionListener { if (browser != null) recreateBrowser() }
    recreateButton.addActionListener { recreateBrowser() }

    recreateBrowser()
  }

  private fun createControls(): JPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    add(JLabel("Bridge mode:"))
    add(bridgeMode)
    add(recreateButton)
  }

  private fun createSwingPanel(): JSplitPane {
    val textAreaPanel = JPanel(BorderLayout()).apply {
      add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(JButton("Copy").apply {
          isFocusable = false
          addActionListener {
            val text = swingTextArea.selectedText ?: return@addActionListener
            CopyPasteManager.getInstance().setContents(StringSelection(text))
          }
        })
        add(JButton("Cut").apply {
          isFocusable = false
          addActionListener {
            val text = swingTextArea.selectedText ?: return@addActionListener
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            swingTextArea.replaceSelection("")
          }
        })
        add(JButton("Paste").apply {
          isFocusable = false
          addActionListener {
            val text = CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor)
              ?: return@addActionListener
            swingTextArea.replaceSelection(text)
          }
        })
      }, BorderLayout.NORTH)
      add(JBScrollPane(swingTextArea).apply {
        border = BorderFactory.createTitledBorder("Swing text area")
      }, BorderLayout.CENTER)
    }

    val clipboardPanel = JPanel(BorderLayout()).apply {
      add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
        add(JButton("Show clipboard").apply {
          isFocusable = false
          addActionListener { showClipboardContents() }
        })
      }, BorderLayout.NORTH)
      add(JBScrollPane(clipboardLog).apply {
        border = BorderFactory.createTitledBorder("Clipboard content and metadata")
      }, BorderLayout.CENTER)
    }

    return JSplitPane(JSplitPane.VERTICAL_SPLIT, textAreaPanel, clipboardPanel).apply {
      resizeWeight = 0.5
    }
  }

  private fun recreateBrowser() {
    browser?.let(Disposer::dispose)

    val builder = JBCefBrowserBuilder()
    when (bridgeMode.selectedItem as BridgeMode) {
      BridgeMode.DEFAULT -> Unit
      BridgeMode.ENABLED -> builder.setUseClipboardBridge(true)
      BridgeMode.DISABLED -> builder.setUseClipboardBridge(false)
    }

    val newBrowser = builder.build()
    browser = newBrowser
    Disposer.register(this, newBrowser)

    val requestHandler = JBCefLocalRequestHandler("https", "localhost")
    val url = requestHandler.createResource("index.html") {
      javaClass.getResourceAsStream("resources/clipboard_bridge.html")?.let {
        JBCefStreamResourceHandler(it, "text/html", newBrowser)
      }
    }
    newBrowser.jbCefClient.addRequestHandler(requestHandler, newBrowser.cefBrowser)

    browserSplitPane.leftComponent = newBrowser.component
    browserSplitPane.revalidate()
    browserSplitPane.repaint()
    newBrowser.loadURL(url)
  }

  private fun showClipboardContents() {
    val transferable = CopyPasteManager.getInstance().getContents()
    if (transferable == null) {
      clipboardLog.text = "The clipboard is empty."
      return
    }

    val result = StringBuilder()
    result.appendLine("Transferable: ${transferable.javaClass.name}")
    result.appendLine("Flavors:")
    transferable.transferDataFlavors.forEach { flavor ->
      result.appendLine("- ${flavor.mimeType}; representation: ${flavor.representationClass.name}")
    }
    result.appendLine()
    appendFlavorContent(result, transferable, DataFlavor.stringFlavor, "Text") { it.toString() }
    appendFlavorContent(result, transferable, DataFlavor.allHtmlFlavor, "HTML") { truncate(it.toString()) }
    appendFlavorContent(result, transferable, DataFlavor.imageFlavor, "Image") {
      val image = it as? Image
      if (image == null) "Unexpected value: ${it.javaClass.name}"
      else "${image.getWidth(null)} × ${image.getHeight(null)}"
    }
    appendFlavorContent(result, transferable, DataFlavor.javaFileListFlavor, "Files") {
      (it as? List<*>)?.joinToString() ?: "Unexpected value: ${it.javaClass.name}"
    }

    clipboardLog.text = result.toString()
    clipboardLog.caretPosition = 0
  }

  private fun appendFlavorContent(
    result: StringBuilder,
    transferable: Transferable,
    flavor: DataFlavor,
    label: String,
    format: (Any) -> String,
  ) {
    result.append("$label: ")
    if (!transferable.isDataFlavorSupported(flavor)) {
      result.appendLine("not available")
      return
    }

    try {
      result.appendLine(format(transferable.getTransferData(flavor)))
    }
    catch (e: Exception) {
      rethrowControlFlowException(e)
      result.appendLine("failed: ${e.message ?: e.javaClass.simpleName}")
    }
  }

  private fun truncate(value: String): String = if (value.length > MAX_CLIPBOARD_TEXT_LENGTH) {
    value.take(MAX_CLIPBOARD_TEXT_LENGTH) + "…"
  }
  else {
    value
  }

  private enum class BridgeMode {
    DEFAULT,
    ENABLED,
    DISABLED;

    override fun toString(): String = when (this) {
      DEFAULT -> "Default"
      ENABLED -> "Enabled"
      DISABLED -> "Disabled"
    }
  }

  private companion object {
    const val MAX_CLIPBOARD_TEXT_LENGTH = 2000
  }
}