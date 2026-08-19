// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.util.Disposer
import com.intellij.ui.jcef.JBCefBrowserBuilder
import com.intellij.ui.jcef.utils.JBCefLocalRequestHandler
import com.intellij.ui.jcef.utils.JBCefStreamResourceHandler
import org.intellij.lang.annotations.Language
import java.awt.BorderLayout
import java.awt.Component
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import javax.swing.JPanel

internal class FullscreenDemo : JBCefTestAppFrame.TestCase() {
  private val myComponent = JPanel(BorderLayout())

  override fun getComponent(): Component = myComponent

  override fun getDisplayName(): String = "Fullscreen Demo"

  override fun initializeImpl() {
    myComponent.removeAll()

    val browser = JBCefBrowserBuilder().build()
    Disposer.register(this, browser)

    val handler = JBCefLocalRequestHandler("https", "localhost")
    handler.addResource("/index.html") {
      JBCefStreamResourceHandler(
        ByteArrayInputStream(HTML.toByteArray(StandardCharsets.UTF_8)),
        "text/html", this)
    }
    browser.jbCefClient.addRequestHandler(handler, browser.cefBrowser)

    myComponent.add(browser.component, BorderLayout.CENTER)
    browser.loadURL("https://localhost/index.html")
  }

  companion object {
    @Language("HTML")
    private val HTML = """
      <!DOCTYPE html>
      <html>
      <head>
        <meta charset="UTF-8">
        <style>
          html, body {
            height: 100%;
            margin: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            font-family: sans-serif;
            background-color: #2b2b2b;
            color: #f0f0f0;
          }
          button {
            font-size: 24px;
            padding: 16px 32px;
            cursor: pointer;
          }
          #status {
            margin-top: 24px;
            font-size: 16px;
          }
          .container {
            text-align: center;
          }
        </style>
      </head>
      <body>
        <div class="container">
          <button id="toggle">Enter fullscreen</button>
          <div id="status">Not in fullscreen</div>
        </div>
        <script>
          const button = document.getElementById('toggle');
          const status = document.getElementById('status');

          button.addEventListener('click', function () {
            if (document.fullscreenElement) {
              document.exitFullscreen();
            }
            else {
              document.documentElement.requestFullscreen();
            }
          });

          document.addEventListener('fullscreenchange', function () {
            const isFullscreen = !!document.fullscreenElement;
            button.textContent = isFullscreen ? 'Exit fullscreen' : 'Enter fullscreen';
            status.textContent = isFullscreen ? 'In fullscreen' : 'Not in fullscreen';
          });
        </script>
      </body>
      </html>
    """.trimIndent()
  }
}
