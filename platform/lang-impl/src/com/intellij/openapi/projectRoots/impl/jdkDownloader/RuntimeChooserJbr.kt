// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path
import java.nio.file.Paths

data class RuntimeChooserDownloadableItem(val item: JdkItem) : RuntimeChooserItem() {
  override fun toString() = item.fullPresentationText
}

fun RuntimeChooserModel.fetchAvailableJbrs() {
  ApplicationManager.getApplication().assertIsDispatchThread()

  object : Task.Backgroundable(null, LangBundle.message("progress.title.downloading.jetbrains.runtime.list")) {
    override fun run(indicator: ProgressIndicator) {
      onUpdateDownloadJbrListScheduled()
      val builds = service<RuntimeChooserJbrListDownloader>().downloadForUI(indicator)

      invokeLater(modalityState = ModalityState.any()) {
        updateDownloadJbrList(builds)
      }
    }
  }.queue()
}

@Service(Service.Level.APP)
private class RuntimeChooserJbrListDownloader : JdkListDownloaderBase() {
  override val feedUrl: String by lazy {
    val majorVersion = ApplicationInfo.getInstance().build.components.firstOrNull()
    "https://download.jetbrains.com/jdk/feed/v1/jbr-choose-runtime-${majorVersion}.json.xz"
  }
}

@Service(Service.Level.APP)
class RuntimeChooserJbrInstaller : JdkInstallerBase() {
  override fun defaultInstallDir(): Path {
    val explicitHome = System.getProperty("jbr.downloader.home")
    if (explicitHome != null) {
      return Paths.get(explicitHome)
    }

    val home = Paths.get(FileUtil.toCanonicalPath(System.getProperty("user.home") ?: "."))
    return when {
      SystemInfo.isLinux   -> home.resolve(".jbr")
      //see https://youtrack.jetbrains.com/issue/IDEA-206163#focus=streamItem-27-3270022.0-0
      SystemInfo.isMac     -> home.resolve("Library/Java/JetBrainsRuntime")
      SystemInfo.isWindows -> home.resolve(".jbr")
      else -> error("Unsupported OS: ${SystemInfo.getOsNameAndVersion()}")
    }
  }
}
