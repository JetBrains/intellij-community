// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.aggrtest;

import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.random.Random

/**
 * Custom JCEF WebView with AGGRESSIVE race-condition triggers
 * Based on JBR-9559 research - implements router churn, handler latency, and browser recreation
 */
internal class JCEFAggressiveRouterTest() : JPanel(), AutoCloseable {
    private var browser: JBCefBrowser? = null
    private var msgRouter: CefMessageRouter? = null
    private var msgRouter2: CefMessageRouter? = null
    private var msgHandler: CefMessageRouterHandlerAdapter? = null
    private var isDisposed = false
    private val queryCounter = AtomicInteger(0)
    private var routerChurnTimer: Timer? = null
    private var browserRecreateTimer: Timer? = null
    private var recreationCycles = 0

    init {
        layout = BorderLayout()
        initializeBrowser()
        startRouterChurn()
        startBrowserRecreation()
    }

    private fun initializeBrowser() {
        try {
            println("Creating JBCefBrowser...")
            browser = JBCefBrowser()
            browser?.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)

            // AGGRESSIVE HANDLER with latency and large responses
            msgHandler = object : CefMessageRouterHandlerAdapter() {
                override fun onQuery(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    queryId: Long,
                    request: String?,
                    persistent: Boolean,
                    callback: org.cef.callback.CefQueryCallback?
                ): Boolean {
                    val count = queryCounter.incrementAndGet()
                    println("Query #$count received, size: ${request?.length ?: 0} bytes")

                    try {
                        // INJECT LATENCY (100-200ms) to widen race window
                        val latency = 100L + Random.nextLong(100)
                        Thread.sleep(latency)

                        // Every 5th query returns LARGE response (>100KB)
                        val response = if (count % 5 == 0) {
                            val largeData = "X".repeat(150_000)
                            """{"status":"success","queryId":$queryId,"count":$count,"data":"$largeData"}"""
                        } else {
                            """{"status":"success","queryId":$queryId,"count":$count}"""
                        }

                        callback?.success(response)
                        println("Query #$count responded after ${latency}ms, response size: ${response.length}")
                    } catch (e: Throwable) {
                        println("ERROR in query #$count: ${e.message}")
                        e.printStackTrace()
                        callback?.failure(500, "Error: ${e.message}")
                    }
                    return true
                }
            }

            // FORCE EARLY ROUTER CREATION
            registerMessageHandler()

            // Load MASSIVE HTML with 1000 cefQuery calls
            val htmlContent = createMassiveTestHTML()
            browser?.loadHTML(htmlContent)

            browser?.component?.let { add(it, BorderLayout.CENTER) }

        } catch (e: Throwable) {
            println("ERROR in initializeBrowser: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun registerMessageHandler() {
        try {
            val client = browser?.cefBrowser?.client
            println("Creating and registering FIRST message router...")
            msgRouter = CefMessageRouter.create(msgHandler)
            client?.addMessageRouter(msgRouter)
            println("First router registered successfully")
        } catch (e: Throwable) {
            println("ERROR registering first router: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * ROUTER CHURN: After 300ms, remove first router and add second
     * This creates the race condition where queries may hit null router
     */
    private fun startRouterChurn() {
        SwingUtilities.invokeLater {
            routerChurnTimer = Timer(300) { event ->
                try {
                    val client = browser?.cefBrowser?.client
                    if (client != null && msgRouter != null && !isDisposed) {
                        println("*** CHURNING ROUTERS - removing first, adding second ***")

                        // Remove first router while queries may still be in-flight
                        client.removeMessageRouter(msgRouter)
                        msgRouter?.dispose()
                        msgRouter = null

                        // Immediately create and register second router
                        msgRouter2 = CefMessageRouter.create(msgHandler)
                        client.addMessageRouter(msgRouter2)

                        println("Router churn complete - second router active")
                    }
                } catch (e: Throwable) {
                    println("ERROR DURING ROUTER CHURN? *** ${e.message}")
                    e.printStackTrace()
                }
                (event.source as Timer).stop()
            }
            routerChurnTimer?.isRepeats = false
            routerChurnTimer?.start()
        }
    }

    /**
     * BROWSER RECREATION: Dispose and recreate browser every 10 seconds for 10 cycles
     * Maximum stress on router lifecycle
     */
    private fun startBrowserRecreation() {
        browserRecreateTimer = Timer(10_000) {
            if (recreationCycles < 10 && !isDisposed) {
                recreationCycles++
                println("*** BROWSER RECREATION CYCLE $recreationCycles/10 ***")

                SwingUtilities.invokeLater {
                    try {
                        // Dispose current browser
                        disposeBrowserOnly()

                        // Recreate immediately
                        Thread.sleep(50) // Tiny delay to force race
                        initializeBrowser()
                        revalidate()
                        repaint()

                        println("Browser recreated successfully")
                    } catch (e: Throwable) {
                        println("ERROR DURING BROWSER RECREATION? *** ${e.message}")
                        e.printStackTrace()
                    }
                }
            } else {
                browserRecreateTimer?.stop()
            }
        }
        browserRecreateTimer?.start()
    }

    private fun disposeBrowserOnly() {
        try {
            val client = browser?.cefBrowser?.client

            if (msgRouter != null && client != null) {
                client.removeMessageRouter(msgRouter)
                msgRouter?.dispose()
                msgRouter = null
            }

            if (msgRouter2 != null && client != null) {
                client.removeMessageRouter(msgRouter2)
                msgRouter2?.dispose()
                msgRouter2 = null
            }

            msgHandler = null
            browser?.dispose()
            browser = null
            removeAll()
        } catch (e: Throwable) {
            println("ERROR during browser disposal: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Generate MASSIVE HTML (~3MB) that fires 1000 cefQuery calls
     * Each query sends 0.5-1MB JSON payload to stress the router
     */
    private fun createMassiveTestHTML(): String {
        println("Generating massive HTML with 1000 auto-firing queries...")

        val largePayloadTemplate = "Y".repeat(500_000) // 500KB base

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <title>JCEF Router Stress Test - 1000 Queries</title>
            <style>
                body { font-family: monospace; padding: 20px; background: #1e1e1e; color: #00ff00; }
                .container { max-width: 1200px; margin: 0 auto; }
                #stats { 
                    position: fixed; 
                    top: 10px; 
                    right: 10px; 
                    background: rgba(0,0,0,0.8); 
                    padding: 15px; 
                    border: 2px solid #ff0000;
                    font-size: 14px;
                }
                #log {
                    height: 400px;
                    overflow-y: auto;
                    background: #000;
                    padding: 10px;
                    margin-top: 20px;
                    border: 1px solid #00ff00;
                }
                .error { color: #ff0000; font-weight: bold; }
                .success { color: #00ff00; }
                ${generateMassiveCSS()}
            </style>
        </head>
        <body>
            <div class="container">
                <h1>🔥 NPE REPRODUCTION STRESS TEST 🔥</h1>
                <div id="stats">
                    <div>Queries Sent: <span id="sent">0</span></div>
                    <div>Success: <span id="success">0</span></div>
                    <div>Errors: <span id="errors">0</span></div>
                    <div>In Flight: <span id="inflight">0</span></div>
                </div>
                <div id="log"></div>
            </div>
            
            ${generateMassiveDOMStructure()}
            
            <script>
                let sentCount = 0;
                let successCount = 0;
                let errorCount = 0;
                let inFlightCount = 0;
                
                const largePayload = "$largePayloadTemplate";
                
                function updateStats() {
                    document.getElementById('sent').textContent = sentCount;
                    document.getElementById('success').textContent = successCount;
                    document.getElementById('errors').textContent = errorCount;
                    document.getElementById('inflight').textContent = inFlightCount;
                }
                
                function log(message, isError) {
                    const logDiv = document.getElementById('log');
                    const entry = document.createElement('div');
                    entry.className = isError ? 'error' : 'success';
                    entry.textContent = new Date().toISOString() + ' - ' + message;
                    logDiv.insertBefore(entry, logDiv.firstChild);
                    if (logDiv.children.length > 50) {
                        logDiv.removeChild(logDiv.lastChild);
                    }
                }
                
                function fireQuery(index) {
                    // Vary payload size between 0.5MB and 1MB
                    const payloadSize = 500000 + Math.floor(Math.random() * 500000);
                    const payload = largePayload.substring(0, payloadSize);
                    const request = JSON.stringify({
                        queryIndex: index,
                        timestamp: Date.now(),
                        payload: payload,
                        size: payloadSize
                    });
                    
                    sentCount++;
                    inFlightCount++;
                    updateStats();
                    
                    log('Sending query #' + index + ' (size: ' + Math.round(request.length/1024) + ' KB)', false);
                    
                    window.cefQuery({
                        request: request,
                        onSuccess: function(response) {
                            inFlightCount--;
                            successCount++;
                            updateStats();
                            log('✓ Query #' + index + ' succeeded', false);
                        },
                        onFailure: function(error_code, error_message) {
                            inFlightCount--;
                            errorCount++;
                            updateStats();
                            log('✗ Query #' + index + ' FAILED: ' + error_message + ' (code: ' + error_code + ')', true);
                        }
                    });
                }
                
                // FIRE 1000 QUERIES as soon as DOM is ready
                document.addEventListener('DOMContentLoaded', function() {
                    log('🚀 Starting 1000-query flood attack...', false);
                    
                    // Fire queries in rapid succession to maximize race conditions
                    for (let i = 1; i <= 1000; i++) {
                        setTimeout(function() { fireQuery(i); }, i * 5); // 5ms between queries
                    }
                    
                    log('All 1000 queries scheduled!', false);
                });
                
                ${generateMassiveJavaScript()}
            </script>
        </body>
        </html>
    """.trimIndent()
    }

    private fun generateMassiveCSS(): String {
        val sb = StringBuilder()
        for (i in 1..2000) {
            sb.append("""
            .stress-class-$i { 
                color: rgb(${i % 255}, ${(i * 2) % 255}, ${(i * 3) % 255}); 
                margin: ${i % 20}px; 
                padding: ${(i * 2) % 20}px;
                font-size: ${12 + (i % 10)}px;
            }
        """.trimIndent())
        }
        return sb.toString()
    }

    private fun generateMassiveJavaScript(): String {
        val sb = StringBuilder()
        for (i in 1..1500) {
            sb.append("""
            function stressFn$i(a, b) {
                const x = $i + a * ${i % 100};
                const y = b - ${i * 2 % 1000};
                return x + y + Math.random() * $i;
            }
        """.trimIndent())
        }
        return sb.toString()
    }

    private fun generateMassiveDOMStructure(): String {
        val sb = StringBuilder()
        for (i in 1..300) {
            sb.append("""
            <div class="stress-section-$i" style="display:none;">
                <table>
                    <tr>
                        ${(1..5).joinToString("") { "<td>Data $i-$it-${(i * it).toString().reversed()}</td>" }}
                    </tr>
                </table>
            </div>
        """.trimIndent())
        }

        return sb.toString()
    }


    override fun close() {
        try {
            if (!isDisposed) {
                isDisposed = true

                println("Disposing CustomJCEFWebView...")

                // Stop timers
                routerChurnTimer?.stop()
                routerChurnTimer = null
                browserRecreateTimer?.stop()
                browserRecreateTimer = null

                // This disposal pattern matches the real implementation
                // The bug likely occurs during this cleanup sequence
                val client = browser?.cefBrowser?.client

                if (msgRouter != null && client != null) {
                    try {
                        println("Removing first router...")
                        client.removeMessageRouter(msgRouter)
                        msgRouter?.dispose()
                    } catch (e: Throwable) {
                        // This might be where the NullPointerException occurs
                        println("ERROR DURING FIRST ROUTER REMOVAL? *** ${e.message}")
                        e.printStackTrace()
                    }
                }

                if (msgRouter2 != null && client != null) {
                    try {
                        println("Removing second router...")
                        client.removeMessageRouter(msgRouter2)
                        msgRouter2?.dispose()
                    } catch (e: Throwable) {
                        println("ERROR DURING SECOND ROUTER REMOVAL? *** ${e.message}")
                        e.printStackTrace()
                    }
                }

                msgHandler = null
                msgRouter = null
                msgRouter2 = null
                browser?.dispose()
                browser = null

                println("Dispose complete")
            }
        } catch (e: Throwable) {
            // The bug manifests here during rapid open/close cycles
            println("ERROR DURING DISPOSE? *** ${e.message}")
            e.printStackTrace()
        }
    }
}
