// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test

import com.intellij.internal.jcef.test.aggrtest.AggressiveRouterTest
import com.intellij.internal.jcef.test.cases.ContextMenu
import com.intellij.internal.jcef.test.cases.DetailedFrame
import com.intellij.internal.jcef.test.cases.KeyboardEvents
import com.intellij.internal.jcef.test.cases.MessageRouterTests
import com.intellij.internal.jcef.test.cases.PerformanceTest
import com.intellij.internal.jcef.test.cases.ResourceHandler
import com.intellij.internal.jcef.test.detailed.handler.ClientSchemeHandler
import com.intellij.internal.jcef.test.detailed.handler.SearchSchemeHandler
import com.intellij.internal.jcef.test.rhtest.RequestHandlingRESTApiTest
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroup.Companion.create
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NotNullFactory
import com.intellij.openapi.util.NotNullLazyValue
import com.intellij.ui.components.JBList
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefApp.JBCefCustomSchemeHandlerFactory
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefSchemeRegistrar
import org.cef.handler.CefResourceHandler
import org.cef.network.CefRequest
import java.awt.CardLayout
import java.awt.Component
import java.awt.GridLayout
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane

internal class JBCefTestApp : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    JBCefTestAppFrame().isVisible = true
  }
}

internal class JBCefTestAppFrame : JFrame() {
  companion object {
    init {
      // Register test custom schemes if possible (and show notification otherwise)
      try {
        JBCefApp.addCefCustomSchemeHandlerFactory(object : JBCefCustomSchemeHandlerFactory {
          override fun registerCustomScheme(registrar: CefSchemeRegistrar) {
            registrar.addCustomScheme(
              SearchSchemeHandler.scheme, true, false, false, false, true, false, false)
          }

          override fun getSchemeName() = SearchSchemeHandler.scheme
          override fun getDomainName() = SearchSchemeHandler.domain
          override fun create(browser: CefBrowser?, frame: CefFrame?, schemeName: String?, request: CefRequest?): CefResourceHandler {
            return SearchSchemeHandler(browser)
          }
        })

        JBCefApp.addCefCustomSchemeHandlerFactory(object : JBCefCustomSchemeHandlerFactory {
          override fun registerCustomScheme(registrar: CefSchemeRegistrar) {
            registrar.addCustomScheme(
              ClientSchemeHandler.scheme, true, false, false, false, true, false, false)
          }

          override fun getSchemeName() = ClientSchemeHandler.scheme
          override fun getDomainName() = ClientSchemeHandler.domain
          override fun create(browser: CefBrowser?, frame: CefFrame?, schemeName: String?, request: CefRequest?): CefResourceHandler {
            return ClientSchemeHandler()
          }
        })
      }
      catch (e: IllegalStateException) {
        ApplicationManager.getApplication().executeOnPooledThread(Runnable {
          val NOTIFICATION_GROUP = NotNullLazyValue.createValue<NotificationGroup?>(NotNullFactory {
            create("JCEF test app",
                   NotificationDisplayType.BALLOON,
                   true,
                   null,
                   null,
                   null)
          })
          val notification = NOTIFICATION_GROUP.getValue().createNotification(
            "Can't register test custom schemes.",
            "JCEF has been initialized and new custom schemes can't be registered. Please run JCEF test app before any web-component initialization.",
            NotificationType.ERROR)
          notification.notify(null)
        })
      }
    }
  }

  private val cardLayout = CardLayout()
  private val contentPanel: JPanel = JPanel(cardLayout)

  private val testCases: List<TestCase> = listOf(
    KeyboardEvents(), ContextMenu(), ResourceHandler(), PerformanceTest(), DetailedFrame(), MessageRouterTests(), RequestHandlingRESTApiTest(), AggressiveRouterTest())

  private val tabsList = JBList(testCases.map { it.getDisplayName() })

  init {
    contentPanel.add(JPanel(GridLayout()).apply { add(JLabel("Select the test case")) })
    testCases.forEach { tabName -> contentPanel.add(tabName.getComponent(), tabName.getDisplayName()) }

    tabsList.addListSelectionListener {
      testCases.find { it.getDisplayName() == tabsList.selectedValue }?.initialize()
      cardLayout.show(contentPanel, tabsList.selectedValue)
    }

    val listWithHeader = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = BorderFactory.createTitledBorder("Test cases")
      add(JScrollPane(this@JBCefTestAppFrame.tabsList))
    }

    contentPanel.border = BorderFactory.createTitledBorder("Browser Panel")

    val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listWithHeader, contentPanel).apply {
      dividerSize = 1
      isOneTouchExpandable = false
      resizeWeight = 0.0
      setDividerLocation(200)
    }

    contentPane.add(splitPane)

    setSize(800, 600)
    setLocationRelativeTo(null)
    defaultCloseOperation = DISPOSE_ON_CLOSE

    addWindowListener(object : WindowAdapter() {
      override fun windowClosing(e: WindowEvent?) {
        testCases.forEach { Disposer.dispose(it) }
      }
    })
  }

  internal abstract class TestCase : Disposable.Default {
    abstract fun getComponent(): Component
    abstract fun getDisplayName(): String
    fun initialize() {
      if (ready) return
      initializeImpl()
      ready = true
    }

    protected abstract fun initializeImpl()

    private var ready: Boolean = false
  }
}