// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.util.ui.EDT
import org.cef.browser.CefDevToolsClient
import java.awt.BorderLayout
import java.awt.Component
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField

/**
 * Demonstrates usage of [CefDevToolsClient]:
 *  - [CefDevToolsClient.executeDevToolsMethod] with a method name only;
 *  - [CefDevToolsClient.executeDevToolsMethod] with a method name and JSON parameters;
 *  - [CefDevToolsClient.addEventListener] to subscribe to DevTools protocol events.
 */
internal class DevToolsTest : JBCefTestAppFrame.TestCase() {
  private val myComponent = JPanel(BorderLayout())

  private val urlField = JTextField("https://www.jetbrains.com")
  private val goButton = JButton("Go")

  private val methodField = JTextField("Overlay.setShowFPSCounter")
  private val parametersField = JTextField("")
  private val executeButton = JButton("Execute method")
  private val addListenerButton = JButton("Add event listener")

  private val logArea = JTextArea().apply {
    isEditable = false
    lineWrap = true
    wrapStyleWord = true
  }

  private var browser: JBCefBrowser = JBCefBrowserBuilder().build().apply { Disposer.register(this@DevToolsTest, this) }
  private var eventListener: CefDevToolsClient.EventListener? = null

  override fun getComponent(): Component = myComponent

  override fun getDisplayName(): String = "DevTools Client"

  override fun initializeImpl() {
    myComponent.add(createUrlPanel(), BorderLayout.NORTH)

    val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, browser.component, createControlPanel()).apply {
      resizeWeight = 0.6
    }
    myComponent.add(splitPane, BorderLayout.CENTER)

    goButton.addActionListener { loadUrl() }
    urlField.addActionListener { loadUrl() }
    executeButton.addActionListener { executeMethod() }
    addListenerButton.addActionListener { toggleEventListener() }

    loadUrl()
  }

  private fun createUrlPanel(): JPanel {
    return JPanel(BorderLayout()).apply {
      add(JLabel("URL:"), BorderLayout.WEST)
      add(urlField, BorderLayout.CENTER)
      add(goButton, BorderLayout.EAST)
    }
  }

  private fun createControlPanel(): JPanel {
    val form = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      add(labeledRow("Method:", methodField))
      add(labeledRow("Parameters (JSON):", parametersField))
      add(JPanel(BorderLayout()).apply {
        add(executeButton, BorderLayout.WEST)
        add(addListenerButton, BorderLayout.EAST)
      })
    }

    val logScroll = JBScrollPane(logArea).apply {
      border = BorderFactory.createTitledBorder("Logged events")
    }

    return JPanel(BorderLayout()).apply {
      add(form, BorderLayout.NORTH)
      add(logScroll, BorderLayout.CENTER)
    }
  }

  private fun labeledRow(label: String, field: JComponent): JPanel {
    return JPanel(BorderLayout()).apply {
      add(JLabel(label), BorderLayout.WEST)
      add(field, BorderLayout.CENTER)
    }
  }

  private fun loadUrl() {
    val url = urlField.text?.trim().orEmpty()
    if (url.isEmpty()) return
    browser.loadURL(url)
    log("Loading URL: $url")
  }

  private fun executeMethod() {
    val client = browser.cefBrowser.devToolsClient ?: return
    val method = methodField.text?.trim().orEmpty()
    if (method.isEmpty()) {
      log("Method name is empty")
      return
    }

    val parameters = parametersField.text?.trim().orEmpty()
    val future = if (parameters.isEmpty()) {
      log("executeDevToolsMethod(\"$method\")")
      client.executeDevToolsMethod(method)
    }
    else {
      log("executeDevToolsMethod(\"$method\", \"$parameters\")")
      client.executeDevToolsMethod(method, parameters)
    }

    future.whenComplete { result, throwable ->
      if (throwable != null) log("  error: ${throwable.message}")
      else log("  result: $result")
    }
  }

  private fun toggleEventListener() {
    val client = browser.cefBrowser.devToolsClient ?: return

    val existing = eventListener
    if (existing != null) {
      client.removeEventListener(existing)
      eventListener = null
      addListenerButton.text = "Add event listener"
      log("Event listener removed")
      return
    }

    val listener = CefDevToolsClient.EventListener { eventName, messageAsJson ->
      log("event: $eventName -> $messageAsJson")
    }
    client.addEventListener(listener)
    eventListener = listener
    addListenerButton.text = "Remove event listener"
    log("Event listener added")
  }

  private fun log(message: String) {
    val timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
    val line = "[$timestamp] $message\n"
    if (EDT.isCurrentThreadEdt()) {
      appendLog(line)
    }
    else {
      ApplicationManager.getApplication().invokeLater { appendLog(line) }
    }
  }

  private fun appendLog(line: String) {
    logArea.append(line)
    logArea.caretPosition = logArea.document.length
  }

  override fun dispose() {
    val client = browser.cefBrowser.devToolsClient
    eventListener?.let { client?.removeEventListener(it) }
    super.dispose()
  }
}
