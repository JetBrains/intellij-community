// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.system.OS
import java.nio.file.Path

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
      return Path.of(explicitHome)
    }

    val home = Path.of(System.getProperty("user.home") ?: ".")
    return when (OS.CURRENT) {
      OS.Windows -> home.resolve(".jbr")
      OS.macOS -> home.resolve("Library/Java/JetBrainsRuntime")
      OS.Linux -> home.resolve(".jbr")
      else -> error("Unsupported OS: ${OS.CURRENT}")
    }
  }
}
