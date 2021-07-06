// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.nls.NlsMessages
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.AppUIUtil
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.LicensingFacade
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.JBFont
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.event.HyperlinkEvent

/**
 * @author Konstantin Bulenkov
 */
class AboutDialogUI(val project: Project?) {
  fun show() {
    val ui = panel {
      row {
        cell(isVerticalFlow = true) {
          component(JBLabel(AppUIUtil.loadApplicationIcon(ScaleContext.create(), 100))).withLargeLeftGap()
        }
        cell(isVerticalFlow = true, isFullWidth = true) {
          label(fullAppName(), JBFont.h2()).constraints(CCFlags.pushX).withLargeLeftGap()
          label(buildInfo()).withLargeLeftGap()
          label("")
          addLicenseInfo(this)
          label("")
          label(runtimeVersion()).withLargeLeftGap()
          label(jvmVersion()).withLargeLeftGap()
          label("")
          addOpenSourceLink(this)
        }
      }
    }
    val appName = ApplicationNamesInfo.getInstance().fullProductName
    dialog(IdeBundle.message("dialog.title.about", appName), ui, resizable = false, project = project ).apply {
      setSize(600, 400)
    }.show()
  }

  private fun addOpenSourceLink(cell: InnerCell) {
    val thirdPartyLibraries = AboutPopup.loadThirdPartyLibraries()
    if (thirdPartyLibraries == null) {
      @Suppress("HardCodedStringLiteral")
      cell.label(IdeBundle.message("about.box.powered.by.open.source")
                   .replace("<hyperlink>", "")
                   .replace("</hyperlink>", "")).withLargeLeftGap()
    }
    val label = HyperlinkLabel()
    label.setTextWithHyperlink(IdeBundle.message("about.box.powered.by.open.source"));
    label.addHyperlinkListener(object: HyperlinkAdapter() {
      override fun hyperlinkActivated(e: HyperlinkEvent?) {
        AboutPopup.showOpenSoftwareSources(thirdPartyLibraries ?: "")
      }
    })
    cell.component(label).withLargeLeftGap()
  }

  @NlsSafe
  private fun runtimeVersion(): String {
    val properties = System.getProperties()
    val javaVersion = properties.getProperty("java.runtime.version", properties.getProperty("java.version", "unknown"))
    val arch = properties.getProperty("os.arch", "")
    return IdeBundle.message("about.box.jre", javaVersion, arch)
  }

  @NlsSafe
  private fun jvmVersion(): String {
    val properties = System.getProperties()
    val vmVersion = properties.getProperty("java.vm.name", "unknown")
    val vmVendor = properties.getProperty("java.vendor", "unknown")
    return IdeBundle.message("about.box.vm", vmVersion, vmVendor)
  }

  private fun addLicenseInfo(cell: InnerCell) {
    val la = LicensingFacade.getInstance()
    if (la != null) {
      val licensedTo = la.licensedToMessage
      if (licensedTo != null) {
        cell.label(licensedTo, bold = true).withLargeLeftGap()
      }
      for (message in la.licenseRestrictionsMessages) {
        cell.label(message).withLargeLeftGap()
      }
    }
  }

  @NlsSafe
  private fun buildInfo(): String {
    val appInfo = ApplicationInfo.getInstance()
    var buildInfo = IdeBundle.message("about.box.build.number", appInfo.build.asString())
    val timestamp: Date = appInfo.buildDate.time
    buildInfo += if (appInfo.build.isSnapshot) {
      IdeBundle.message("about.box.build.date.time", NlsMessages.formatDateLong(timestamp),
                        SimpleDateFormat("HH:mm").format(timestamp))
    }
    else {
      IdeBundle.message("about.box.build.date", NlsMessages.formatDateLong(timestamp))
    }
    return buildInfo
  }

  @NlsSafe
  private fun fullAppName():String {
    var appName = ApplicationInfo.getInstance().fullApplicationName
    val edition = ApplicationNamesInfo.getInstance().editionName
    if (edition != null) {
      appName += " ($edition)"
    }
    return appName
  }
}