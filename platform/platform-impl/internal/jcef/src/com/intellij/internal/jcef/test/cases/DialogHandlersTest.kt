// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.utils.JBCefLocalRequestHandler
import com.intellij.ui.jcef.utils.JBCefStreamResourceHandler
import org.cef.browser.CefBrowser
import org.cef.callback.CefFileDialogCallback
import org.cef.callback.CefJSDialogCallback
import org.cef.handler.CefDialogHandler
import org.cef.handler.CefJSDialogHandler
import org.cef.misc.BoolRef
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.util.Vector
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

internal class DialogHandlersTest : JBCefTestAppFrame.TestCase() {
  override fun getComponent(): Component = myComponent

  override fun getDisplayName(): String = "Dialog Handlers"

  override fun initializeImpl() {
    val browser = JBCefBrowserBuilder().build()
    myBrowser = browser
    Disposer.register(this, browser)

    val localRequestHandler = JBCefLocalRequestHandler("https", "localhost")
    val indexUrl = localRequestHandler.createResource("dialog_handlers.html") {
      javaClass.getResourceAsStream("resources/dialog_handlers.html")?.let { JBCefStreamResourceHandler(it, "text/html", this) }
    }
    browser.jbCefClient.addRequestHandler(localRequestHandler, browser.cefBrowser)

    myComponent.removeAll()
    myComponent.add(myControlPanel, BorderLayout.NORTH)
    myComponent.add(browser.component, BorderLayout.CENTER)
    myComponent.add(JScrollPane(myLog), BorderLayout.SOUTH)
    myLog.preferredSize = Dimension(100, 110)
    setMode(DialogMode.DEFAULT)
    browser.loadURL(indexUrl)
  }

  private fun setMode(mode: DialogMode) {
    myMode = mode
    val browser = myBrowser ?: return
    val client = browser.jbCefClient
    client.removeJSDialogHandler(myJSDialogHandler, browser.cefBrowser)
    client.removeDialogHandler(myDialogHandler, browser.cefBrowser)
    if (mode != DialogMode.DEFAULT) {
      client.addJSDialogHandler(myJSDialogHandler, browser.cefBrowser)
      client.addDialogHandler(myDialogHandler, browser.cefBrowser)
    }
    log("Mode: ${mode.displayName}")
  }

  private fun log(message: String) {
    SwingUtilities.invokeLater {
      myLog.append("$message\n")
      myLog.caretPosition = myLog.document.length
    }
  }

  private fun showFileChooser(
    mode: CefDialogHandler.FileDialogMode,
    title: String,
    defaultFilePath: String,
    acceptFilters: Vector<String>,
    acceptExtensions: Vector<String>,
    callback: CefFileDialogCallback,
  ) {
    val extensions = (acceptFilters + acceptExtensions).flatMap { filter ->
      filter.substringAfterLast('|').split(';').map(String::trim)
        .filter { it.startsWith(".") }.map { it.removePrefix(".") }
    }.distinct().toTypedArray()
    val paths: List<String> = when (mode) {
      CefDialogHandler.FileDialogMode.FILE_DIALOG_SAVE -> {
        val descriptor = FileSaverDescriptor(title.ifBlank { "Save File" }, "")
        if (extensions.isNotEmpty()) descriptor.withExtensionFilter("Accepted files", *extensions)
        listOfNotNull(FileChooserFactory.getInstance().createSaveFileDialog(descriptor, myComponent).save(defaultFilePath)?.file?.path)
      }
      else -> {
        val descriptor = when (mode) {
          CefDialogHandler.FileDialogMode.FILE_DIALOG_OPEN_MULTIPLE -> FileChooserDescriptorFactory.multiFiles()
          CefDialogHandler.FileDialogMode.FILE_DIALOG_OPEN_FOLDER -> FileChooserDescriptorFactory.singleDir()
          else -> FileChooserDescriptorFactory.singleFile()
        }
        if (extensions.isNotEmpty()) descriptor.withExtensionFilter("Accepted files", *extensions)
        FileChooser.chooseFiles(descriptor, myComponent, null, null).map { it.path }
      }
    }
    if (paths.isEmpty()) {
      log("File dialog cancelled")
      callback.Cancel()
    }
    else {
      log("File dialog selected: ${paths.joinToString()}")
      callback.Continue(Vector(paths))
    }
  }

  private enum class DialogMode(val displayName: String) {
    DEFAULT("Default (native dialogs)"),
    SUPPRESS("Suppress dialogs"),
    HANDLE("Handle dialogs (IJ dialogs)"),
  }

  private val myComponent = JPanel(BorderLayout())
  private val myLog = JBTextArea().apply {
    isEditable = false
    lineWrap = true
    wrapStyleWord = true
  }
  @Volatile private var myMode = DialogMode.DEFAULT
  private var myBrowser: JBCefBrowserBase? = null

  private val myDefaultModeButton = JRadioButton(DialogMode.DEFAULT.displayName, true).apply {
    addActionListener { setMode(DialogMode.DEFAULT) }
  }
  private val mySuppressModeButton = JRadioButton(DialogMode.SUPPRESS.displayName).apply {
    addActionListener { setMode(DialogMode.SUPPRESS) }
  }
  private val myHandleModeButton = JRadioButton(DialogMode.HANDLE.displayName).apply {
    addActionListener { setMode(DialogMode.HANDLE) }
  }
  private val myControlPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
    ButtonGroup().apply {
      add(myDefaultModeButton)
      add(mySuppressModeButton)
      add(myHandleModeButton)
    }
    add(myDefaultModeButton)
    add(mySuppressModeButton)
    add(myHandleModeButton)
    add(JButton("Reload").apply {
      addActionListener { myBrowser?.cefBrowser?.reload() }
    })
  }

  private val myJSDialogHandler = object : CefJSDialogHandler {
    override fun onJSDialog(
      browser: CefBrowser,
      origin_url: String,
      dialog_type: CefJSDialogHandler.JSDialogType,
      message_text: String,
      default_prompt_text: String,
      callback: CefJSDialogCallback,
      suppress_message: BoolRef,
    ): Boolean {
      val mode = myMode
      log("JS dialog ${dialog_type.name} from $origin_url: $message_text")
      if (mode == DialogMode.SUPPRESS) {
        suppress_message.set(true)
        log("Suppressed ${dialog_type.name}")
        return false
      }
      if (mode != DialogMode.HANDLE) return false

      ApplicationManager.getApplication().invokeLater(Runnable {
        when (dialog_type) {
          CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_ALERT -> {
            Messages.showMessageDialog(myComponent, message_text, "JavaScript Alert", Messages.getInformationIcon())
            callback.Continue(true, "")
          }
          CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_CONFIRM -> {
            val confirmed = Messages.showYesNoDialog(myComponent, message_text, "JavaScript Confirm", Messages.getQuestionIcon()) == Messages.YES
            callback.Continue(confirmed, "")
          }
          CefJSDialogHandler.JSDialogType.JSDIALOGTYPE_PROMPT -> {
            val input = Messages.showInputDialog(myComponent, message_text, "JavaScript Prompt", Messages.getQuestionIcon(), default_prompt_text, null)
            callback.Continue(input != null, input.orEmpty())
          }
        }
      }, ModalityState.any())
      return true
    }

    override fun onBeforeUnloadDialog(browser: CefBrowser, message_text: String, is_reload: Boolean, callback: CefJSDialogCallback): Boolean {
      val mode = myMode
      log("Before-unload dialog (reload=$is_reload): $message_text")
      if (mode == DialogMode.SUPPRESS) {
        callback.Continue(true, "")
        log("Suppressed before-unload dialog")
        return true
      }
      if (mode != DialogMode.HANDLE) return false

      ApplicationManager.getApplication().invokeLater(Runnable {
        val leave = Messages.showYesNoDialog(myComponent, message_text, "Leave Page?", Messages.getQuestionIcon()) == Messages.YES
        callback.Continue(leave, "")
      }, ModalityState.any())
      return true
    }

    override fun onResetDialogState(browser: CefBrowser) {
      log("JS dialog state reset")
    }

    override fun onDialogClosed(browser: CefBrowser) {
      log("Native JS dialog closed")
    }
  }

  private val myDialogHandler = CefDialogHandler { _, mode, title, defaultFilePath, acceptFilters, acceptExtensions, _, callback ->
    val currentMode = myMode
    log("File dialog ${mode.name}: $title")
    when (currentMode) {
        DialogMode.SUPPRESS -> {
          callback.Cancel()
          log("Suppressed file dialog")
          true
        }
        DialogMode.HANDLE -> {
          ApplicationManager.getApplication().invokeLater(Runnable {
            showFileChooser(mode, title, defaultFilePath, acceptFilters, acceptExtensions, callback)
          }, ModalityState.any())
          true
        }
        else -> false
    }
  }
}