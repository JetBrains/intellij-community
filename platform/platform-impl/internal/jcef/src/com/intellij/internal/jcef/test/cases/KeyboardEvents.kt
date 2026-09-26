// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.utils.JBCefLocalRequestHandler
import com.intellij.ui.jcef.utils.JBCefStreamResourceHandler
import java.awt.BorderLayout
import javax.swing.JPanel

internal class KeyboardEvents : JBCefTestAppFrame.TestCase() {
  override fun getComponent(): JPanel = myComponent

  override fun getDisplayName(): String = "Keyboard JS Events"

  override fun initializeImpl() {
    myComponent.removeAll()
    val browser = JBCefBrowserBuilder().build()
    Disposer.register(this, browser)

    val localRequestHandler = JBCefLocalRequestHandler("https", "localhost")
    val indexUrl = localRequestHandler.createResource("index.html") {
      javaClass.getResourceAsStream("resources/keyboard_events.html")?.let { JBCefStreamResourceHandler(it, "text/html", this) }
    }
    browser.jbCefClient.addRequestHandler(localRequestHandler, browser.cefBrowser)

    myComponent.add(browser.component, BorderLayout.CENTER)
    browser.loadURL(indexUrl.toString())
  }

  private val myComponent = JPanel(BorderLayout())
}