// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.internal.jcef.test.JCEFNonModalDialog
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefBrowserBuilder
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextField

internal class MessageRouterTests : JBCefTestAppFrame.TestCase() {
  override fun getDisplayName() = "JS message tests"

  override fun getComponent(): Component {
    val panel = JPanel(VerticalFlowLayout(FlowLayout.LEFT))

    val doCallbackFail1 = JCheckBox("js callback fail")
    val runSection1 = JPanel(BorderLayout())
    runSection1.add(doCallbackFail1, BorderLayout.WEST)
    val startButton1 = JButton("Run")
    val testName = "Simple JCEF JS routing test"
    startButton1.addActionListener {
      val dialog = JCEFNonModalDialog(createSimpleTestBrowser(doCallbackFail1.isSelected), testName)
      dialog.show()
    }
    runSection1.add(startButton1, BorderLayout.CENTER)
    panel.add(TestCasePanel(testName, "Creates simple html that interacts with JS", runSection1))

    val runSection2 = JPanel(BorderLayout(10, 5))
    val doCallbackFail2 = JCheckBox("js callback fail")
    runSection2.add(doCallbackFail2, BorderLayout.WEST)
    val countTxt = JTextField("5")
    runSection2.add(countTxt, BorderLayout.CENTER)

    val startButton2 = JButton("Run")
    startButton2.addActionListener {
      val count = countTxt.text.toIntOrNull() ?: 2
      for (i in 0 until count) {
        val dialog = JCEFNonModalDialog(MsgRouterTestJCEFPanel("SeveralBrowsersTestDlg_" + i, doCallbackFail2.isSelected), "JCEF Rapid Open/Close Test")
        dialog.show()
      }
    }
    runSection2.add(startButton2, BorderLayout.EAST)

    panel.add(TestCasePanel("Several browsers test", "Creates several browsers with JS interaction (it should stress message router)", runSection2))

    val scrollPane = JBScrollPane(panel)
    scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
    scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED

    return scrollPane
  }

  override fun initializeImpl() {
  }
}

// NOTE: this test browser doesn't call client.removeMessageRouter after closing (for testing, it should be done on cef_server-side automatically)
private fun createSimpleTestBrowser(doFailCallback: Boolean): JBCefBrowser {
  val browser = JBCefBrowserBuilder().build()
  browser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)

  val msgHandler = object : CefMessageRouterHandlerAdapter() {
    override fun onQuery(
      browser: CefBrowser?,
      frame: CefFrame?,
      queryId: Long,
      request: String?,
      persistent: Boolean,
      callback: org.cef.callback.CefQueryCallback?
    ): Boolean {
      if (doFailCallback)
        callback?.failure(500, "Failure response [simple test]: $request")
      else
        callback?.success("Success response [simple test]: $request")
      return true
    }
  }
  val msgRouter = CefMessageRouter.create(msgHandler)
  browser.cefBrowser.client.addMessageRouter(msgRouter)

  val html = """
            <!DOCTYPE html>
            <html lang=\"en\">
            <head>
                <meta charset=\"UTF-8\">
                <title>JCEF Simple JS Test Window</title>
                <style>
                    body { font-family: Arial, sans-serif; padding: 20px; background: #f5f5f5; }
                    .container { max-width: 600px; margin: 0 auto; }
                    button { padding: 12px 24px; margin: 8px; background: #007acc; color: white; border: none; border-radius: 4px; cursor: pointer; }
                    button:hover { background: #005a99; }
                    #result { margin-top: 20px; padding: 10px; background: white; border-radius: 4px; border: 1px solid #ddd; }
                    #cefQueryStatus { color: red; font-weight: bold; margin-bottom: 10px; }
                </style>
            </head>
            <body>
                <div class=\"container\">
                    <h1>JCEF Simple Message Router Test</h1>
                    <div id=\"cefQueryStatus\"></div>
                    <button onclick="testQuery('simple_test_message')">Send Test Query</button>
                    <div id="result">Click the button to test the message router...</div>
                </div>
                <script>
                    function testQuery(message) {
                        const result = document.getElementById('result');
                        if (!window.cefQuery) {
                            result.innerHTML = '<strong>Error:</strong> window.cefQuery is not available!';
                            return;
                        }
                        result.innerHTML = 'Sending query: ' + message + '...';
                        window.cefQuery({
                            request: message,
                            onSuccess: function(response) {
                                result.innerHTML = '<strong>Success:</strong> ' + response;
                            },
                            onFailure: function(error_code, error_message) {
                                result.innerHTML = '<strong>Error:</strong> ' + error_message + ' (Code: ' + error_code + ')';
                            }
                        });
                    }
                    // Show status of cefQuery injection
                    window.addEventListener('DOMContentLoaded', function() {
                        var status = document.getElementById('cefQueryStatus');
                        if (window.cefQuery) {
                            status.innerHTML = 'window.cefQuery is available.';
                            status.style.color = 'green';
                        } else {
                            status.innerHTML = 'window.cefQuery is NOT available!';
                            status.style.color = 'red';
                        }
                    });
                </script>
            </body>
            </html>
        """
  browser.loadHTML(html)

  return browser
}