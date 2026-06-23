// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.JBCefScrollbarsHelper
import com.intellij.ui.jcef.utils.JBCefLocalRequestHandler
import com.intellij.ui.jcef.utils.JBCefStreamResourceHandler
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.GridLayout
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane

internal class ScrollbarsDemo : JBCefTestAppFrame.TestCase() {
  private val myComponent = JPanel(GridLayout(1, 4, 4, 0))

  private val loremIpsum = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. " +
                           "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. " +
                           "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. " +
                           "Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. " +
                           "Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."

  override fun getComponent(): Component = myComponent

  override fun getDisplayName(): String = "Scrollbars Demo"

  override fun initializeImpl() {
    myComponent.removeAll()

    val scheme = EditorColorsManager.getInstance().schemeForCurrentUITheme
    val bgColor = colorToCss(scheme.defaultBackground)
    val fgColor = colorToCss(scheme.defaultForeground)

    myComponent.add(createSwingPanel(scheme.defaultBackground, scheme.defaultForeground))

    myComponent.add(createBrowserPanel("Default Scrollbars", bgColor, fgColor) { _, _ -> "" })

    myComponent.add(createBrowserPanel("CSS Styled Scrollbars", bgColor, fgColor) { _, _ ->
      "<style>${JBCefScrollbarsHelper.buildScrollbarsStyle()}</style>"
    })

    myComponent.add(createBrowserPanel("Overlay Scrollbars", bgColor, fgColor) { _, handler ->
      handler.addResource("/overlayscrollbars.css") {
        JBCefStreamResourceHandler(
          ByteArrayInputStream(JBCefScrollbarsHelper.getOverlayScrollbarsSourceCSS().toByteArray(StandardCharsets.UTF_8)),
          "text/css", this)
      }
      handler.addResource("/overlayscrollbars.js") {
        JBCefStreamResourceHandler(
          ByteArrayInputStream(JBCefScrollbarsHelper.getOverlayScrollbarsSourceJS().toByteArray(StandardCharsets.UTF_8)),
          "application/javascript", this)
      }
      handler.addResource("/overlay-style.css") {
        JBCefStreamResourceHandler(
          ByteArrayInputStream(JBCefScrollbarsHelper.getOverlayScrollbarStyle().toByteArray(StandardCharsets.UTF_8)),
          "text/css", this)
      }

      """
        <link rel="stylesheet" href="https://localhost/overlayscrollbars.css">
        <link rel="stylesheet" href="https://localhost/overlay-style.css">
        <script src="https://localhost/overlayscrollbars.js"></script>
        <script>
          document.addEventListener('DOMContentLoaded', function() {
            OverlayScrollbars(document.body, {
              scrollbars: { autoHide: 'move', autoHideDelay: 800 }
            });
          });
        </script>

      """
    })
  }

  private fun createBrowserPanel(
    title: String,
    bgColor: String,
    fgColor: String,
    extraSetup: (JBCefBrowser, JBCefLocalRequestHandler) -> String,
  ): JPanel {
    val browser = JBCefBrowserBuilder().build()
    Disposer.register(this, browser)

    val handler = JBCefLocalRequestHandler("https", "localhost")
    val extraHead = extraSetup(browser, handler)

    val html = buildHtml(bgColor, fgColor, extraHead)
    handler.addResource("/index.html") {
      JBCefStreamResourceHandler(
        ByteArrayInputStream(html.toByteArray(StandardCharsets.UTF_8)),
        "text/html", this)
    }

    browser.jbCefClient.addRequestHandler(handler, browser.cefBrowser)

    val panel = JPanel(BorderLayout())
    panel.border = BorderFactory.createTitledBorder(title)
    panel.add(browser.component, BorderLayout.CENTER)
    browser.loadURL("https://localhost/index.html")
    return panel
  }

  private fun buildHtml(bgColor: String, fgColor: String, extraHead: String): String {
    val paragraphs = (1..20).joinToString("\n") { "<p>$it. $loremIpsum</p>" }

    return """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <style>
          @import url('https://fonts.googleapis.com/css2?family=JetBrains+Mono&display=swap');
          body {
            background-color: $bgColor;
            color: $fgColor;
            font-family: 'JetBrains Mono', monospace;
            font-size: 13px;
            padding: 16px;
            margin: 0;
          }
        </style>
        $extraHead
      </head>
      <body>
        $paragraphs
      </body>
      </html>
    """.trimIndent()
  }

  private fun createSwingPanel(bgColor: Color, fgColor: Color): JPanel {
    val title = "Swing Scrollbars"
    val text = (1..20).joinToString("\n\n") { "$it. $loremIpsum" }

    val textPane = JTextPane().apply {
      isEditable = false
      this.text = text
      background = bgColor
      foreground = fgColor
      font = Font("JetBrains Mono", Font.PLAIN, 13)
      caretPosition = 0
    }

    val panel = JPanel(BorderLayout())
    panel.border = BorderFactory.createTitledBorder(title)
    panel.add(JScrollPane(textPane), BorderLayout.CENTER)
    return panel
  }

  private fun colorToCss(color: Color): String =
    "rgb(${color.red}, ${color.green}, ${color.blue})"
}
