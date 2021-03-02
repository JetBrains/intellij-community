// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.write
import com.intellij.util.system.CpuArch
import java.nio.file.Path
import java.nio.file.Paths

private val LOG = logger<RuntimeChooserPaths>()

@Service(Service.Level.APP)
class RuntimeChooserPaths {
  private fun computeJdkFilePath(): Path {
    //also used in the com.intellij.ide.SystemHealthMonitor.checkRuntime, but after a discussion with Roman, we decided to copy
    val appName = ApplicationNamesInfo.getInstance().scriptName
    val configName = appName + (if (!SystemInfo.isWindows) "" else if (CpuArch.isIntel64()) "64.exe" else ".exe") + ".jdk"
    return Paths.get(PathManager.getConfigPath(), configName)
  }

  fun installCustomJdk(@NlsSafe name: String, suggestedHome: Path) = runWithProgress {
    val home = RuntimeChooserJreValidator.testNewJdkUnderProgress(
      computeHomePath = { suggestedHome.toAbsolutePath().toString() },
      callback = object : RuntimeChooserJreValidatorCallback<Path?> {
        override fun onSdkResolved(versionString: String, sdkHome: Path) = sdkHome
        override fun onError(message: String): Path? {
          invokeLater { Messages.showErrorDialog(message, LangBundle.message("dialog.title.choose.ide.runtime")) }
          return null
        }
      }) ?: return@runWithProgress

    var jdkFileShadow : Path? = null
    try {
      val jdkFile = computeJdkFilePath()
      jdkFileShadow = jdkFile

      assertNotRunningFromSources()
      jdkFile.parent?.createDirectories()
      jdkFile.write(home.toAbsolutePath().toString())
      LOG.warn("Set custom boot runtime to: $home in the $jdkFile. On errors, please remove the .jdk file")
      service<RuntimeChooserNotifications>().notifyRuntimeChangeToCustomAndRestart(name)
    } catch (t: Throwable) {
      LOG.warn("Failed to set boot runtime to $home for $name in $jdkFileShadow. ${t.message}", t)
      service<RuntimeChooserNotifications>().notifySettingBootJdkFailed(home, jdkFileShadow)
    }
  }

  fun resetCustomJdk() = runWithProgress {
    var jdkFileShadow : Path? = null
    try {
      val jdkFile = computeJdkFilePath()
      jdkFileShadow = jdkFile
      if (!jdkFile.exists()) return@runWithProgress

      assertNotRunningFromSources()
      jdkFile.delete()
      LOG.warn("Removed custom boot runtime from the $jdkFile. Bundled runtime will be used")

      service<RuntimeChooserNotifications>().notifyRuntimeChangeToDefaultAndRestart()
    } catch (t: Throwable) {
      LOG.warn("Failed to reset boot runtime to default in $jdkFileShadow. ${t.message}", t)
      service<RuntimeChooserNotifications>().notifySettingDefaultBootJdkFailed(jdkFileShadow)
    }
  }

  private fun runWithProgress(action: (indicator: ProgressIndicator) -> Unit) {
    val title = LangBundle.message("progress.title.choose.ide.runtime.set.jdk")
    object : Task.Backgroundable(null, title, true, DEAF) {
      override fun run(indicator: ProgressIndicator) {
          action(indicator)
      }
    }.queue()
  }

  private fun assertNotRunningFromSources() {
    if (PluginManagerCore.isRunningFromSources()) {
      throw RuntimeException("IntelliJ is running from sources")
    }
  }
}
