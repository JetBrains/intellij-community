// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.ui.svg.getSvgIconCacheFile
import com.intellij.ui.svg.getSvgIconCacheInvalidMarkerFile
import com.intellij.ui.svg.svgCache
import java.nio.file.Files
import java.nio.file.StandardOpenOption

// icons maybe loaded before app loaded, so, SvgCacheMapper cannot be as a service
private class IconDbMaintainer : AppLifecycleListener {
  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun appWillBeClosed(isRestart: Boolean) {
    svgCache?.close()
  }
}

private class IconCacheInvalidator : CachesInvalidator() {
  override fun invalidateCaches() {
    val svgIconCacheFile = getSvgIconCacheFile()
    val markerFile = getSvgIconCacheInvalidMarkerFile(svgIconCacheFile)
    if (Files.exists(svgIconCacheFile)) {
      Files.write(markerFile, ByteArray(0), StandardOpenOption.WRITE, StandardOpenOption.CREATE)
    }
    else {
      Files.deleteIfExists(markerFile)
    }
  }
}