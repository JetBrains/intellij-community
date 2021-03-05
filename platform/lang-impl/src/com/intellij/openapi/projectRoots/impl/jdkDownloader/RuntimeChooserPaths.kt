// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.io.createDirectories
import com.intellij.util.io.delete
import com.intellij.util.io.write
import com.intellij.util.system.CpuArch
import java.nio.file.Path

private val LOG = logger<RuntimeChooserPaths>()

@Service(Service.Level.APP)
class RuntimeChooserPaths {
  private fun computeJdkFilePath(): Path {
    val directory = PathManager.getCustomOptionsDirectory() ?: throw IllegalStateException("Runtime selection not supported")
    val scriptName = ApplicationNamesInfo.getInstance().scriptName
    val configName = scriptName + (if (!SystemInfo.isWindows) "" else if (CpuArch.isIntel64()) "64.exe" else ".exe") + ".jdk"
    return Path.of(directory, configName)
  }

  fun installCustomJdk(@NlsSafe jdkName: String,
                       resolveSuggestedHome: (ProgressIndicator) -> Path?
  ) = runWithProgress { indicator, jdkFile ->
    val sdkHome = try {
      resolveSuggestedHome(indicator)
    }
    catch (t: Throwable) {
      if (t is ControlFlowException) throw t
      LOG.warn("resolve failed. ${t.message}", t)
      return@runWithProgress null
    }

    val home = RuntimeChooserJreValidator.testNewJdkUnderProgress(
      computeHomePath = { sdkHome?.toAbsolutePath()?.toString() },
      callback = object : RuntimeChooserJreValidatorCallback<Path?> {
        override fun onSdkResolved(versionString: String, sdkHome: Path) = sdkHome
        override fun onError(message: String): Path? {
          RuntimeChooserMessages.showErrorMessage(message)
          return null
        }
      }) ?: return@runWithProgress null

    jdkFile.parent?.createDirectories()
    jdkFile.write(home.toAbsolutePath().toString())
    LOG.warn("Set custom boot runtime to: $home in the $jdkFile. On errors, please remove the .jdk file")
    jdkName
  }

  fun resetCustomJdk() = runWithProgress { _, jdkFile ->
    jdkFile.delete()
    LOG.warn("Removed custom boot runtime from the $jdkFile. Bundled runtime will be used")
    LangBundle.message("dialog.message.choose.ide.runtime.is.set.to.param.default")
  }

  private fun runWithProgress(action: (indicator: ProgressIndicator, jdkFile: Path) -> /*runtime name*/ @NlsSafe String?) {
    val title = LangBundle.message("progress.title.choose.ide.runtime.set.jdk")
    object : Task.ConditionalModal(null, title, true, DEAF) {
      override fun run(indicator: ProgressIndicator) {
        var jdkFileShadow: Path? = null
        try {
          val jdkFile = computeJdkFilePath()
          jdkFileShadow = jdkFile

          @NlsSafe val runtimeName = action(indicator, jdkFile)
          if (runtimeName != null) {
            RuntimeChooserMessages.showRestartMessage(runtimeName)
          }
        }
        catch (t: Throwable) {
          if (t is ControlFlowException) throw t
          LOG.warn("Failed to change boot runtime in $jdkFileShadow. ${t.message}", t)
          RuntimeChooserMessages.showErrorMessage(LangBundle.message("dialog.message.choose.ide.runtime.unknown.error", t.localizedMessage))
        }
      }
    }.queue()
  }
}
