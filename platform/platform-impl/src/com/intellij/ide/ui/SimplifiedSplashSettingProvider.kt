// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl.SIMPLIFIED_SPLASH_MARKER_FILE_NAME
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText

@Service(Service.Level.APP)
@Suppress("unused") // The advanced setting `ide.ui.simplified.splash.image` uses the members through reflection
internal class SimplifiedSplashSettingProvider {
  var useSimplifiedSplashImage: Boolean
    get() = UISettings.getInstance().useSimplifiedSplashImage
    set(value) {
      UISettings.getInstance().useSimplifiedSplashImage = value
      val markerFile = PathManager.getConfigDir().resolve(SIMPLIFIED_SPLASH_MARKER_FILE_NAME)
      runCatching {
        if (value) markerFile.writeText("") else markerFile.deleteIfExists()
      }.onFailure {
        thisLogger().warn("Failed to update marker file for simplified splash image", it)
      }
    }

  fun isUseSimplifiedSplashImageVisible(): Boolean = ApplicationInfo.getInstance().isSimplifiedSplashSupported
}
