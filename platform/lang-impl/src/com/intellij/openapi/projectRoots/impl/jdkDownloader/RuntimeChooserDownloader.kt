// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Path
import java.nio.file.Paths

@Service(Service.Level.APP)
internal class RuntimeChooserDownloader {
  fun downloadAndUse(indicator: ProgressIndicator, jdk: JdkItem, targetDir: Path): Path? {
    try {
      val installer = RuntimeChooserJbrInstaller
      val request = installer.prepareJdkInstallation(jdk, targetDir)
      installer.installJdk(request, indicator, null)
      return request.javaHome
    }
    catch (t: Throwable) {
      if (t is ControlFlowException) throw t
      thisLogger().warn("Failed to download boot runtime from $jdk. ${t.message}")
      RuntimeChooserMessages.showErrorMessage(
        LangBundle.message("dialog.message.choose.ide.runtime.download.error", jdk.fullPresentationText, targetDir.toString())
      )
      return null
    }
  }
}

internal object RuntimeChooserJbrInstaller : JdkInstallerBase() {
  override fun defaultInstallDir(osAbstractionForJdkInstaller: OsAbstractionForJdkInstaller?): Path {
    // TODO Use osAbstractionForJdkInstaller
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
