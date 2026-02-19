// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.rhtest;

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefBrowserBuilder
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.SwingConstants

internal class RequestHandlingRESTApiTest : JBCefTestAppFrame.TestCase() {
  override fun getComponent(): Component = createContent()

  override fun getDisplayName(): String = "Request handling REST api test"

  override fun initializeImpl() {}

  fun createContent() = JBPanel<JBPanel<*>>().apply {
    layout = BorderLayout()

    if (!JBCefApp.isSupported()) {
      val label = JBLabel("JCef not supported")
      add(label, BorderLayout.CENTER)
      return@apply
    }

    val jcefRemoteEnabled = System.getProperty("jcef.remote.enabled")
    val jcefRemoteLabel = JBLabel("JCEf remote enable : $jcefRemoteEnabled")
    jcefRemoteLabel.isOpaque = true
    jcefRemoteLabel.background = JBColor.BLUE
    jcefRemoteLabel.horizontalAlignment = SwingConstants.CENTER
    add(jcefRemoteLabel, BorderLayout.NORTH)

    val jbCefBrowser = JBCefBrowserBuilder()
      .setEnableOpenDevToolsMenuItem(true)
      .build()

    Disposer.register(myDisposable, jbCefBrowser!!)

    jbCefBrowser.setErrorPage(JBCefBrowserBase.ErrorPage.DEFAULT)

    val lifespanHandler = MyLifespanHandle()
    jbCefBrowser.jbCefClient.addLifeSpanHandler(lifespanHandler, jbCefBrowser.cefBrowser)

    jbCefBrowser.jbCefClient.addRequestHandler(object : CefRequestHandlerAdapter() {
      override fun getResourceRequestHandler(
        browser: CefBrowser,
        frame: CefFrame,
        request: CefRequest,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: BoolRef?,
      ): CefResourceRequestHandler? {
        if (request.url.contains("main/index.html")) {
          return object : CefResourceRequestHandlerAdapter() {
            override fun getResourceHandler(browser: CefBrowser?, frame: CefFrame?, request: CefRequest): CefResourceHandler {
              return MyResourceHandler()
            }
          }
        }

        return null
      }
    }, jbCefBrowser.cefBrowser)

    add(jbCefBrowser.component, BorderLayout.CENTER)
  }

  private val myDisposable: Disposable = this
}
