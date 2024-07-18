// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry

internal data class RuntimeChooserDownloadableItem(val item: JdkItem) : RuntimeChooserItem() {
  override fun toString(): @NlsSafe String = item.fullPresentationText
}

internal fun RuntimeChooserModel.fetchAvailableJbrs() {
  object : Task.Backgroundable(null, LangBundle.message("progress.title.choose.ide.runtime.downloading.jetbrains.runtime.list")) {
    override fun run(indicator: ProgressIndicator) {
      val builds = service<RuntimeChooserJbrListDownloader>()
        .downloadForUI(indicator)
        .filter { RuntimeChooserJreValidator.isSupportedSdkItem(it) }

      invokeLater(modalityState = ModalityState.any()) {
        updateDownloadJbrList(builds)
      }
    }
  }.queue()
}

@Service(Service.Level.APP)
private class RuntimeChooserJbrListDownloader : JdkListDownloaderBase() {
  override val feedUrl: String
    get() {
      val registry = runCatching { Registry.get("runtime.chooser.url").asString() }.getOrNull()
      if (!registry.isNullOrBlank()) return registry

      val majorVersion = runCatching { Registry.get("runtime.chooser.pretend.major").asInteger() }.getOrNull()
                         ?: ApplicationInfo.getInstance().build.components.firstOrNull()

      return "https://download.jetbrains.com/jdk/feed/v1/jbr-choose-runtime-${majorVersion}.json.xz"
    }
}

