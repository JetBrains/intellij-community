// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.jcef.test.cases

import com.intellij.internal.jcef.test.JBCefTestAppFrame
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBuilder
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.security.CefSSLInfo
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingUtilities

internal class BadSslCertificateTest : JBCefTestAppFrame.TestCase() {
  private val myComponent = JPanel(BorderLayout(0, 8))
  private val browsers = mutableListOf<JBCefBrowser>()
  private val logArea = JTextArea().apply {
    isEditable = false
    lineWrap = true
    wrapStyleWord = true
    border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
  }

  override fun getDisplayName(): String = "BadSSL Certificate Test"

  override fun getComponent(): Component = myComponent

  override fun initializeImpl() {
    browsers.forEach(Disposer::dispose)
    browsers.clear()
    myComponent.removeAll()

    val controlsPanel = JPanel(GridLayout(1, 2, 8, 0)).apply {
      add(JButton("Clear certificate exceptions").apply {
        addActionListener { clearCertificateExceptions() }
      })
    }

    val browsersPanel = JPanel(GridLayout(1, 2, 8, 0)).apply {
      add(createBrowserPanel("CertManager disabled", useCertificateManager = false))
      add(createBrowserPanel("CertManager enabled", useCertificateManager = true))
    }

    myComponent.add(controlsPanel, BorderLayout.NORTH)
    myComponent.add(browsersPanel, BorderLayout.CENTER)
    myComponent.add(JBScrollPane(logArea), BorderLayout.SOUTH)

    log("Open both browsers and compare the behavior.")
    log("The default URL is: $BAD_SSL_START_URL")
    log("The disabled browser uses a manual certificate-error handler with IDE trust dialogs.")
    log("Use the address bars and the Load button to open a page in each browser.")
    log("Use 'Clear certificate exceptions' after trust-store changes.")
  }

  private fun createBrowserPanel(title: String, useCertificateManager: Boolean): JPanel {
    val browser = JBCefBrowserBuilder()
      .setUseCertificateManager(useCertificateManager)
      .build()
      .also {
        Disposer.register(this, it)
        browsers += it
      }

    browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
      override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
        if (frame?.isMain == true) {
          log("[$title] load end, status=$httpStatusCode")
        }
      }

      override fun onLoadError(
        browser: CefBrowser?,
        frame: CefFrame?,
        errorCode: CefLoadHandler.ErrorCode?,
        errorText: String?,
        failedUrl: String?,
      ) {
        if (frame?.isMain == true) {
          log("[$title] load error, code=$errorCode, text=$errorText")
        }
      }
    }, browser.cefBrowser)

    if (!useCertificateManager) {
      browser.jbCefClient.addRequestHandler(object : CefRequestHandlerAdapter() {
        override fun onCertificateError(
          browser: CefBrowser?,
          cert_error: CefLoadHandler.ErrorCode?,
          request_url: String?,
          sslInfo: CefSSLInfo,
          callback: CefCallback,
        ): Boolean {
          SwingUtilities.invokeLater {
            val certificate = sslInfo.certificate.certificatesChain.firstOrNull()
            val details = buildString {
              request_url?.let { appendLine("URL: $it") }
              cert_error?.let { appendLine("CEF error: $it") }
              certificate?.let {
                appendLine("Subject: ${it.subjectX500Principal.name}")
                appendLine("Issuer: ${it.issuerX500Principal.name}")
              }
              append("Browser mode: IDE CertificateManager disabled")
            }

            val accepted = Messages.showYesNoDialog(
              null,
              "The page uses an invalid HTTPS certificate.\n\n$details\n\nContinue loading this page?",
              "Certificate Warning",
              "Continue",
              "Cancel",
              Messages.getWarningIcon(),
            ) == Messages.YES

            if (accepted) {
              log("[$title] certificate accepted by the user dialog.")
              callback.Continue()
            }
            else {
              log("[$title] certificate rejected by the user dialog.")
              callback.cancel()
            }
          }
          return true
        }
      }, browser.cefBrowser)
    }

    val panel = JPanel(BorderLayout())
    val addressField = JTextField(BAD_SSL_START_URL)
    val navigationPanel = JPanel(GridLayout(1, 4, 8, 0)).apply {
      add(addressField)
      add(JButton("Load").apply {
        addActionListener {
          val url = addressField.text.trim()
          if (url.isEmpty()) {
            log("[$title] URL is empty.")
            return@addActionListener
          }
          browser.loadURL(url)
          log("[$title] loading URL: $url")
        }
      })
      add(JButton("Back").apply {
        addActionListener {
          if (browser.cefBrowser.canGoBack()) {
            browser.cefBrowser.goBack()
            log("[$title] back requested.")
          }
          else {
            log("[$title] back is not available.")
          }
        }
      })
      add(JButton("Reload").apply {
        addActionListener {
          browser.cefBrowser.reload()
          log("[$title] reload requested.")
        }
      })
    }

    panel.border = BorderFactory.createTitledBorder(title)
    panel.add(navigationPanel, BorderLayout.NORTH)
    panel.add(browser.component, BorderLayout.CENTER)
    return panel
  }

  private fun clearCertificateExceptions() {
    var clearedContexts = 0
    browsers.forEach { browser ->
      val context = browser.cefBrowser.requestContext
      if (context != null) {
        context.ClearCertificateExceptions(null)
        clearedContexts++
      }
    }

    if (clearedContexts == 0) {
      log("Could not clear certificate exceptions: no browser request context is available.")
    }
    else {
      log("Cleared certificate exceptions for $clearedContexts browser contexts.")
    }
  }

  private fun log(message: String) {
    val appendText = Runnable {
      if (logArea.text.isEmpty()) {
        logArea.text = message
      }
      else {
        logArea.append("\n$message")
      }
      logArea.caretPosition = logArea.document.length
    }

    SwingUtilities.invokeLater(appendText)
  }

  companion object {
    private const val BAD_SSL_START_URL = "https://badssl.com/"
  }
}