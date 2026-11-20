// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.utils.JBCefLocalRequestHandler
import com.intellij.ui.jcef.utils.JBCefStreamResourceHandler
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefFocusHandler
import org.cef.handler.CefFocusHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.KeyboardFocusManager
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.beans.PropertyChangeListener
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities

private val FOCUSED_COLOR = JBColor.GREEN
private val NOT_FOCUSED_COLOR = JBColor.RED

internal class FocusTest : JBCefTestAppFrame.TestCase() {
  private val myComponent = JPanel(BorderLayout())
  private val requestFocusBeforeCreate = JCheckBox("Request focus before native browser creation")
  private val addressBar = JTextField("https://localhost/focus.html").apply { name = "Address bar" }
  private val focusOwnerLabel = JLabel("Swing focus: none").apply {
    isOpaque = true
    foreground = JBColor.BLACK
    font = font.deriveFont(Font.BOLD)
    border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
  }
  private val eventLog = JTextArea().apply {
    isEditable = false
    lineWrap = true
    wrapStyleWord = true
  }
  private val focusOwnerListener = PropertyChangeListener { event ->
    val focusOwner = event.newValue as? Component
    SwingUtilities.invokeLater {
      showFocusOwner(focusOwner)
      appendLog("Swing focus owner: ${componentName(focusOwner)}")
    }
  }

  private lateinit var browserSplitPane: JSplitPane
  private var browser: JBCefBrowser? = null
  private var cefFocusHandler: CefFocusHandlerAdapter? = null
  private var cefLifeSpanHandler: CefLifeSpanHandlerAdapter? = null
  private var cefLoadHandler: CefLoadHandlerAdapter? = null

  override fun getComponent(): Component = myComponent

  override fun getDisplayName(): String = "Focus"

  override fun initializeImpl() {
    myComponent.add(createControls(), BorderLayout.NORTH)
    browserSplitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT, JPanel(), JBScrollPane(eventLog)).apply {
      resizeWeight = 0.8
    }
    myComponent.add(browserSplitPane, BorderLayout.CENTER)

    val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
    focusManager.addPropertyChangeListener("focusOwner", focusOwnerListener)
    appendLog("Initial Swing focus owner: ${componentName(focusManager.focusOwner)}")
    addressBar.addActionListener { loadAddress() }

    recreateBrowser()
  }

  override fun dispose() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("focusOwner", focusOwnerListener)
    disposeBrowser()
  }

  private fun createControls(): JPanel = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
      add(requestFocusBeforeCreate)
      add(JButton("Recreate browser").apply {
        isFocusable = false
        addActionListener { recreateBrowser() }
      })
    })
    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
      add(JButton("setFocus(true)").apply {
        isFocusable = false
        addActionListener {
          browser?.cefBrowser?.setFocus(true)
          appendLog("Called CefBrowser.setFocus(true)")
        }
      })
      add(JButton("setFocus(false)").apply {
        isFocusable = false
        addActionListener {
          browser?.cefBrowser?.setFocus(false)
          appendLog("Called CefBrowser.setFocus(false)")
        }
      })
      add(JButton("Clear log").apply {
        isFocusable = false
        addActionListener { eventLog.text = "" }
      })
    })
    add(JPanel(BorderLayout()).apply {
      add(JLabel("Address bar:"), BorderLayout.WEST)
      add(addressBar, BorderLayout.CENTER)
      add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
        add(JButton("Load").apply {
          isFocusable = false
          addActionListener { loadAddress() }
        })
        add(JButton("Reload").apply {
          isFocusable = false
          addActionListener {
            browser?.cefBrowser?.reload()
            appendLog("Called CefBrowser.reload()")
          }
        })
      }, BorderLayout.EAST)
    })
    add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(focusOwnerLabel) })
  }

  private fun loadAddress() {
    val url = addressBar.text.trim()
    if (url.isEmpty()) return
    browser?.loadURL(url)
    appendLog("Called loadURL: $url")
  }

  private fun recreateBrowser() {
    disposeBrowser()

    val url = addressBar.text.trim()
    val urlAtCreation = url.isNotEmpty()
    val newBrowser = JBCefBrowserBuilder()
      .setOffScreenRendering(true)
      .setCreateImmediately(false)
      .setUrl(if (urlAtCreation) url else null)
      .build()
    appendLog(if (urlAtCreation) "Browser created with URL: $url" else "Browser created without URL")
    browser = newBrowser
    Disposer.register(this, newBrowser)

    val focusHandler = object : CefFocusHandlerAdapter() {
      override fun onSetFocus(browser: CefBrowser, source: CefFocusHandler.FocusSource): Boolean {
        appendLog("CEF onSetFocus: $source")
        return false
      }

      override fun onGotFocus(browser: CefBrowser) {
        appendLog("CEF onGotFocus")
      }

      override fun onTakeFocus(browser: CefBrowser, next: Boolean) {
        appendLog("CEF onTakeFocus: next=$next")
      }
    }
    cefFocusHandler = focusHandler
    newBrowser.jbCefClient.addFocusHandler(focusHandler, newBrowser.cefBrowser)

    val focusBeforeCreate = requestFocusBeforeCreate.isSelected
    val browserComponent = newBrowser.cefBrowser.uiComponent
    val lifeSpanHandler = object : CefLifeSpanHandlerAdapter() {
      override fun onAfterCreated(browser: CefBrowser) {
        appendLog("CEF onAfterCreated")
        if (focusBeforeCreate) return
        SwingUtilities.invokeLater {
          if (this@FocusTest.browser === newBrowser) {
            appendLog("Focus request after native browser creation: ${browserComponent.requestFocusInWindow()}")
          }
        }
      }
    }
    cefLifeSpanHandler = lifeSpanHandler
    newBrowser.jbCefClient.addLifeSpanHandler(lifeSpanHandler, newBrowser.cefBrowser)

    val loadHandler = object : CefLoadHandlerAdapter() {
      override fun onLoadStart(browser: CefBrowser, frame: CefFrame, transitionType: CefRequest.TransitionType) {
        if (frame.isMain) appendLog("CEF onLoadStart: ${frame.url}")
      }

      override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        if (frame.isMain) appendLog("CEF onLoadEnd: ${frame.url}")
      }

      override fun onLoadError(
        browser: CefBrowser,
        frame: CefFrame,
        errorCode: CefLoadHandler.ErrorCode,
        errorText: String,
        failedUrl: String,
      ) {
        if (frame.isMain) appendLog("CEF onLoadError: $errorCode $failedUrl")
      }
    }
    cefLoadHandler = loadHandler
    newBrowser.jbCefClient.addLoadHandler(loadHandler, newBrowser.cefBrowser)

    val requestHandler = JBCefLocalRequestHandler("https", "localhost")
    requestHandler.createResource("focus.html") {
      javaClass.getResourceAsStream("resources/focus_test.html")?.let {
        JBCefStreamResourceHandler(it, "text/html", newBrowser)
      }
    }
    newBrowser.jbCefClient.addRequestHandler(requestHandler, newBrowser.cefBrowser)

    browserComponent.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent) {
        appendLog("Swing browser focusGained")
      }

      override fun focusLost(e: FocusEvent) {
        appendLog("Swing browser focusLost")
      }
    })

    browserSplitPane.topComponent = newBrowser.component
    browserSplitPane.revalidate()
    browserSplitPane.repaint()
    if (focusBeforeCreate) {
      appendLog("Focus request before native browser creation: ${browserComponent.requestFocusInWindow()}")
    }
    if (!urlAtCreation) loadAddress()
    showFocusOwner(KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner)
  }

  private fun showFocusOwner(focusOwner: Component?) {
    val browserComponent = browser?.cefBrowser?.uiComponent
    val inBrowser = focusOwner != null && browserComponent != null && SwingUtilities.isDescendingFrom(focusOwner, browserComponent)
    focusOwnerLabel.text = when {
      inBrowser -> "Swing focus: browser page (${componentName(focusOwner)})"
      focusOwner == null -> "Swing focus: none"
      else -> "Swing focus: not the browser page (${componentName(focusOwner)})"
    }
    focusOwnerLabel.background = if (inBrowser) FOCUSED_COLOR else NOT_FOCUSED_COLOR
  }

  private fun disposeBrowser() {
    val currentBrowser = browser ?: return
    cefFocusHandler?.let { currentBrowser.jbCefClient.removeFocusHandler(it, currentBrowser.cefBrowser) }
    cefFocusHandler = null
    cefLifeSpanHandler?.let { currentBrowser.jbCefClient.removeLifeSpanHandler(it, currentBrowser.cefBrowser) }
    cefLifeSpanHandler = null
    cefLoadHandler?.let { currentBrowser.jbCefClient.removeLoadHandler(it, currentBrowser.cefBrowser) }
    cefLoadHandler = null
    browser = null
    Disposer.dispose(currentBrowser)
  }

  private fun appendLog(message: String) {
    SwingUtilities.invokeLater {
      eventLog.append("$message\n")
      eventLog.caretPosition = eventLog.document.length
    }
  }

  private fun componentName(component: Component?): String {
    if (component == null) return "None"

    val name = component.name
    val className = component.javaClass.simpleName

    if (name != null) {
      return "$className ($name)"
    }

    return className ?: "None"
  }
}