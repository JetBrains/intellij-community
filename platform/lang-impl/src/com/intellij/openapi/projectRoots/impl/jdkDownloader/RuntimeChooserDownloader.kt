// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path
import java.nio.file.Paths

@Service(Service.Level.APP)
class RuntimeChooserDownloader {
  companion object {
    private val LOG = logger<RuntimeChooserDownloader>()
  }

  fun downloadAndUse(jdk: JdkItem, targetDir: Path) {
    val title = LangBundle.message("progress.title.choose.ide.runtime.downloading.jdk", jdk.fullPresentationText)
    object: Task.Backgroundable(null, title, true, ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) {
        try {
          val installer = service<RuntimeChooserJbrInstaller>()
          val request = installer.prepareJdkInstallation(jdk, targetDir)
          installer.installJdk(request, indicator, null)

          service<RuntimeChooserPaths>().installCustomJdk(request.item.fullPresentationText, request.installDir)
        } catch (t: Throwable) {
          LOG.warn("Failed to download boot runtime from $jdk. ${t.message}")
          service<RuntimeChooserNotifications>().notifyDownloadFailed(jdk, t.localizedMessage)
        }
      }
    }.queue()
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
