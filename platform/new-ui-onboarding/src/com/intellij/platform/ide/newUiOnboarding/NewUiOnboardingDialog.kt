// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.ide.plugins.MultiPanel
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.*
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Color
import java.awt.Font
import java.util.*
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JRootPane
import javax.swing.border.Border

class NewUiOnboardingDialog(project: Project)
  : DialogWrapper(project, null, false, IdeModalityType.IDE, false) {
  private val backgroundColor: Color
    get() = JBColor.namedColor("NewUiOnboarding.Dialog.background", UIUtil.getPanelBackground())

  init {
    init()
    setUndecorated(true)
    rootPane.windowDecorationStyle = JRootPane.NONE
    rootPane.border = PopupBorder.Factory.create(true, true)
    WindowRoundedCornersManager.configure(this)
  }

  override fun createCenterPanel(): JComponent {
    val videoSize = JBDimension(384, 242)
    val contentGaps = UnscaledGaps(28, 32, 22, 32)

    val panel = panel {
      row {
        cell(createAnimationPanel())
          .customize(UnscaledGaps.EMPTY)
          .applyToComponent {
            preferredSize = videoSize
          }
      }
      panel {
        row {
          label(NewUiOnboardingBundle.message("dialog.title"))
            .customize(UnscaledGaps.EMPTY)
            .applyToComponent {
              font = JBFont.label().deriveFont(Font.BOLD, JBUIScale.scale(20f))
            }
        }
        row {
          val maxWidth = videoSize.width - JBUI.scale(contentGaps.width)
          val charWidth = window.getFontMetrics(JBFont.label()).charWidth('0')
          val maxLineLength = maxWidth / charWidth
          text(NewUiOnboardingBundle.message("dialog.text"), maxLineLength)
            .customize(UnscaledGaps(top = 8))
        }
        row {
          button(NewUiOnboardingBundle.message("start.tour")) { close(0) }
            .focused()
            .applyToComponent {
              // make button blue without an outline
              putClientProperty("gotItButton", true)
              ClientProperty.put(this, DarculaButtonUI.DEFAULT_STYLE_KEY, true)
              // register Enter key binding
              this@NewUiOnboardingDialog.rootPane.defaultButton = this
            }

          link(NewUiOnboardingBundle.message("dialog.skip")) { close(1) }

          customize(UnscaledGapsY(top = 12))
        }
        customize(contentGaps)
      }
    }

    panel.background = backgroundColor
    return panel
  }

  override fun createContentPaneBorder(): Border? = null

  private fun createAnimationPanel(): JComponent {
    val browser = JBCefBrowser.createBuilder().setMouseWheelEventEnable(false).build()
    val browserComponent = browser.component
    WindowMoveListener(browserComponent).installTo(browserComponent.components.firstOrNull() ?: browserComponent)
    UIUtil.setNotOpaqueRecursively(browserComponent)

    val banner = IconLoader.findIcon(BANNER_PATH, NewUiOnboardingDialog::class.java.classLoader)
    val bannerLabel = JLabel(banner)
    val multiPanel = object : MultiPanel() {
      override fun create(key: Int): JComponent = when (key) {
        BANNER_KEY -> bannerLabel
        BROWSER_KEY -> browserComponent
        else -> throw IllegalArgumentException("Unknown key: ${key}")
      }
    }

    browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
      override fun onLoadingStateChange(b: CefBrowser?, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
        invokeLater(ModalityState.any()) {
          if (isLoading) {
            multiPanel.select(BANNER_KEY, true)
          }
          else multiPanel.select(BROWSER_KEY, true)
        }
      }
    }, browser.cefBrowser)

    val videoBase64 = readVideoAsBase64()
    val pageHtml = WebAnimationUtils.createVideoHtmlPage(videoBase64, backgroundColor)
    browser.loadHTML(pageHtml)

    multiPanel.select(BROWSER_KEY, true)
    return multiPanel
  }

  private fun readVideoAsBase64(): String {
    val url = NewUiOnboardingDialog::class.java.classLoader.getResource(VIDEO_PATH)
              ?: error("Failed to find file by path: $VIDEO_PATH")
    val videoBytes = url.readBytes()
    return Base64.getEncoder().encodeToString(videoBytes)
  }

  companion object {
    private const val VIDEO_PATH: String = "newUiOnboarding/DialogVideo.webm"
    private const val BANNER_PATH: String = "newUiOnboarding/banner.png"
    private const val BANNER_KEY: Int = 0
    private const val BROWSER_KEY: Int = 1
  }
}