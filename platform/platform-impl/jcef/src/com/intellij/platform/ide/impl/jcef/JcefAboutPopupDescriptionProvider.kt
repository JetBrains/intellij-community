// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.impl.jcef

import com.intellij.ide.AboutPopupDescriptionProvider
import com.intellij.ide.IdeBundle
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.jcef.JBCefApp
import com.jetbrains.cef.JCefAppConfig
import com.jetbrains.cef.JCefVersionDetails
import java.text.MessageFormat

internal class JcefAboutPopupDescriptionProvider : AboutPopupDescriptionProvider {
  override fun getDescription(): String? {
    val jcefVersion = getJcefVersion() ?: return null
    val nativeBundleVersion = getJcefNativeBundleVersion()
    val value = if (nativeBundleVersion != null && nativeBundleVersion != jcefVersion) {
      "$jcefVersion (native $nativeBundleVersion)"
    }
    else {
      jcefVersion
    }
    return IdeBundle.message("about.box.jcef.version", value)
  }

  override fun getExtendedDescription(): String? {
    val description = getDescription() ?: return null
    return description.replace("<br/>", "\n")
  }

  private fun getJcefVersion(): String? {
    if (!JBCefApp.isSupported()) return null
    return try {
      shortenJcefVersion(JCefAppConfig.getVersionDetails().toString())
    }
    catch (_: JCefVersionDetails.VersionUnavailableException) {
      null
    }
  }

  private fun getJcefNativeBundleVersion(): String? {
    if (!JBCefApp.isSupported()) return null
    return JBCefApp.getNativeBundleVersionString()?.let(::shortenJcefVersion)
  }

  private fun shortenJcefVersion(detailedVersionString: String): String {
    val detailedVersionPattern = "#.#.#-g([0-9a-f]{7})-chromium-#.#.#.#-api-#.#(?:-([^-]+)-([^-]+))?"
      .replace(".", "\\.")
      .replace("#", "(\\d+)")
      .toRegex()

    val match = detailedVersionPattern.matchEntire(detailedVersionString)
    if (match == null) {
      logger<JcefAboutPopupDescriptionProvider>().warn("Cannot parse JCEF version string: $detailedVersionString")
      return detailedVersionString
    }

    val values = match.groupValues
    val branch = values[11].ifEmpty { null }
    val buildNumber = values[12].ifEmpty { null }
    return if (branch != null && buildNumber != null) {
      MessageFormat.format("{0}.{1}.{2}-{3}-{4}", values[1], values[2], values[3], branch, buildNumber)
    }
    else {
      MessageFormat.format("{0}.{1}.{2}", values[1], values[2], values[3])
    }
  }
}
