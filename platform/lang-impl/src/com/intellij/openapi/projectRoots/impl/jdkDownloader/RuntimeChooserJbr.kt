// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task

class RuntimeChooserDownloadableItem(val item : JdkItem) : RuntimeChooseItem() {
  override fun toString() = item.fullPresentationText
}

fun RuntimeChooserModel.fetchAvailableJbrs() {
  ApplicationManager.getApplication().assertIsDispatchThread()

  object: Task.Backgroundable(null, LangBundle.message("progress.title.downloading.jetbrains.runtime.list")) {
    override fun run(indicator: ProgressIndicator) {
      onUpdateDownloadJbrListScheduled()
      val builds = service<JbrListDownloader>().downloadForUI(indicator)
      updateDownloadJbrList(builds)
    }
  }.queue()
}

@Service(Service.Level.APP)
private class JbrListDownloader : JdkListDownloaderBase() {
  override val feedUrl: String by lazy {
    val majorVersion = ApplicationInfo.getInstance().build.components.firstOrNull()
    "https://download.jetbrains.com/jdk/feed/v1/jbr-choose-runtime-${majorVersion}.json.xz"
  }
}


