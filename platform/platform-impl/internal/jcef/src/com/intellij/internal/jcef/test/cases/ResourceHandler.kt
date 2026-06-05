// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.utils.JBCefLocalRequestHandler
import com.intellij.ui.jcef.utils.JBCefStreamResourceHandler
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel

internal class ResourceHandler : JBCefTestAppFrame.TestCase() {
  override fun getComponent(): Component {
    return myComponent
  }

  override fun getDisplayName(): String {
    return "ResourceHandler"
  }

  override fun initializeImpl() {
    myComponent.removeAll()
    val browser = JBCefBrowserBuilder().build()
    Disposer.register(this, browser)

    val localRequestHandler = JBCefLocalRequestHandler("https", "localhost")
    val indexUrl = localRequestHandler.createResource("index.html") {
      javaClass.getResourceAsStream("resources/resource_handler/index.html")?.let { JBCefStreamResourceHandler(it, "text/html", this) }
    }
    localRequestHandler.createResource("style.css") {
      javaClass.getResourceAsStream("resources/resource_handler/style.css")?.let { JBCefStreamResourceHandler(it, "text/css", this) }
    }
    localRequestHandler.createResource("folder/a.html") {
      javaClass.getResourceAsStream("resources/resource_handler/folder/a.html")?.let { JBCefStreamResourceHandler(it, "text/html", this) }
    }
    localRequestHandler.createResource("folder/b.html") {
      javaClass.getResourceAsStream("resources/resource_handler/folder/b.html")?.let { JBCefStreamResourceHandler(it, "text/html", this) }
    }
    browser.jbCefClient.addRequestHandler(localRequestHandler, browser.cefBrowser)

    myComponent.add(browser.component, BorderLayout.CENTER)
    browser.loadURL(indexUrl)
  }

  private val myComponent = JPanel(BorderLayout())
}