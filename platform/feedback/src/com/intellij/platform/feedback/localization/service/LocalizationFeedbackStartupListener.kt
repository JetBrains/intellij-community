// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.localization.service

import com.intellij.ide.AppLifecycleListener

class LocalizationFeedbackStartupListener : AppLifecycleListener {
  override fun appStarted() {
    /*
    Uncomment when strings are translated

    val service = LocalizationFeedbackService.getInstance()
    if (!service.isEnabled()) return

    service.tryRecordInstallation()

    if (service.hasLanguagePack() && !service.wasInteracted()) {
      thisLogger().info("Lang pack detected, starting watcher")

      service.runWatcher()
    }*/
  }
}