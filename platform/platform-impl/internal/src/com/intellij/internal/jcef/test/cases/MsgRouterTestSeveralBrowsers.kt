// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.awt.BorderLayout
import javax.swing.JPanel

internal class MsgRouterTestJCEFPanel(uid: String, doFailCallback: Boolean) : JPanel(), AutoCloseable {
  private var browser: JBCefBrowser? = null
  private var msgRouter: CefMessageRouter? = null
  private var msgHandler: CefMessageRouterHandlerAdapter? = null
  private var isDisposed = false

  init {
    layout = BorderLayout()

    browser = JBCefBrowser()
    browser?.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)

    msgHandler = object : CefMessageRouterHandlerAdapter() {
      override fun onQuery(
        browser: CefBrowser?,
        frame: CefFrame?,
        queryId: Long,
        request: String?,
        persistent: Boolean,
        callback: org.cef.callback.CefQueryCallback?
      ): Boolean {
        if (doFailCallback)
          callback?.failure(500, "Failure response [${uid}]: $request")
        else
          callback?.success("Success response [${uid}]: $request")
        return true
      }
    }

    val client = browser?.cefBrowser?.client
    msgRouter = CefMessageRouter.create(msgHandler)
    client?.addMessageRouter(msgRouter)

    val htmlContent = createTestHTML()
    browser?.loadHTML(htmlContent)

    browser?.component?.let { add(it, BorderLayout.CENTER) }
  }

  private fun createTestHTML(): String {
    return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>JCEF several browsers test</title>
                <style>
                    body { font-family: Arial, sans-serif; padding: 20px; background: #f5f5f5; }
                    .container { max-width: 600px; margin: 0 auto; }
                    button { 
                        padding: 12px 24px; 
                        margin: 8px; 
                        background: #007acc; 
                        color: white; 
                        border: none; 
                        border-radius: 4px; 
                        cursor: pointer;
                    }
                    button:hover { background: #005a99; }
                    #result { 
                        margin-top: 20px; 
                        padding: 10px; 
                        background: white; 
                        border-radius: 4px; 
                        border: 1px solid #ddd;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>JCEF Message Router Test</h1>
                    <p>This page tests the JCEF message router.</p>
                    <p><strong>Instructions:</strong> Click the button below, then close and reopen this dialog 2-3 times rapidly.</p>
                    
                    <button onclick="testQuery('test_message_1')">Test Message 1</button>
                    <button onclick="testQuery('test_message_2')">Test Message 2</button>
                    <button onclick="rapidTest()">Rapid Test (Multiple Queries)</button>
                    
                    <div id="result">Click a button to test the message router...</div>
                </div>
                
                <script>
                    let queryCount = 0;
                    
                    function testQuery(message) {
                        queryCount++;
                        const result = document.getElementById('result');
                        result.innerHTML = 'Sending query ' + queryCount + ': ' + message + '...';
                        
                        window.cefQuery({
                            request: message + '_' + queryCount,
                            onSuccess: function(response) {
                                result.innerHTML = '<strong>Success:</strong> ' + response;
                            },
                            onFailure: function(error_code, error_message) {
                                result.innerHTML = '<strong>Error:</strong> ' + error_message + ' (Code: ' + error_code + ')';
                            }
                        });
                    }
                    
                    function rapidTest() {
                        // Send multiple rapid queries to stress test the message router
                        for (let i = 0; i < 10; i++) {
                            setTimeout(() => testQuery('rapid_test_' + i), i * 100);
                        }
                    }
                    
                    // Auto-test on load to trigger communication immediately
                    window.addEventListener('load', function() {
                        for (let i = 0; i < 5; i++) {
                            setTimeout(() => testQuery('auto_load_test_' + i), i * 300);
                        }
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
  }

  override fun close() {
    if (!isDisposed) {
      isDisposed = true

      // This disposal pattern matches the real implementation
      // The bug likely occurs during this cleanup sequence
      val client = browser?.cefBrowser?.client
      if (msgRouter != null && client != null) {
        try {
          client.removeMessageRouter(msgRouter)
        } catch (e: Exception) {
          // This might be where the NullPointerException occurs
          e.printStackTrace()
        }
      }

      msgHandler = null
      msgRouter?.dispose()
      msgRouter = null
      browser?.dispose()
      browser = null
    }
  }
}
