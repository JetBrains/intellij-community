// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.newUiOnboarding

import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.text.HtmlChunk
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
import java.awt.Font
import java.util.*
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.border.Border

class NewUiOnboardingDialog(project: Project)
  : DialogWrapper(project, null, false, IdeModalityType.PROJECT, false) {
  init {
    init()
    setUndecorated(true)
    rootPane.windowDecorationStyle = JRootPane.NONE
    rootPane.border = PopupBorder.Factory.create(true, true)
    if (WindowRoundedCornersManager.isAvailable()) {
      if ((SystemInfoRt.isMac && UIUtil.isUnderDarcula()) || SystemInfoRt.isWindows) {
        WindowRoundedCornersManager.setRoundedCorners(window, JBUI.CurrentTheme.Popup.borderColor(true))
        rootPane.border = PopupBorder.Factory.createEmpty()
      }
      else {
        WindowRoundedCornersManager.setRoundedCorners(window)
      }
    }
  }

  override fun createCenterPanel(): JComponent {
    val videoSize = JBDimension(384, 242)
    val contentGaps = UnscaledGaps(28, 32, 22, 32)

    val panel = panel {
      row {
        val videoBase64 = readVideoAsBase64()
        val browser = JBCefBrowser.createBuilder().setMouseWheelEventEnable(false).build()
        val pageHtml = createVideoHtmlPage(videoBase64)
        browser.loadHTML(pageHtml)
        cell(browser.component)
          .customize(UnscaledGaps.EMPTY)
          .applyToComponent {
            WindowMoveListener(this).installTo(components?.firstOrNull() ?: this)
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

    panel.background = JBColor.namedColor("NewUiOnboarding.Dialog.background", UIUtil.getPanelBackground())
    return panel
  }

  override fun createContentPaneBorder(): Border? = null

  private fun createVideoHtmlPage(videoBase64: String): String {
    val componentId = "video"
    val head = HtmlChunk.head().child(LottieUtils.getSingleContentCssStyles(Gray._32, componentId))

    val videoTag = HtmlChunk.tag("video")
      .attr("id", componentId)
      .attr("autoplay")
      .attr("loop")
      .attr("muted")
      .child(HtmlChunk.tag("source")
               .attr("type", "video/webm")
               .attr("src", "data:video/webm;base64,$videoBase64"))
    val body = HtmlChunk.body().child(videoTag)

    return HtmlChunk.html()
      .child(head)
      .child(body)
      .toString()
  }

  private fun readVideoAsBase64(): String {
    val url = NewUiOnboardingDialog::class.java.classLoader.getResource(VIDEO_PATH)
              ?: error("Failed to find file by path: $VIDEO_PATH")
    val videoBytes = url.readBytes()
    return Base64.getEncoder().encodeToString(videoBytes)
  }

  companion object {
    private const val VIDEO_PATH: String = "newUiOnboarding/DialogVideo.webm"
  }
}