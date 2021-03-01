// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.SimpleJavaSdkType
import com.intellij.openapi.roots.ui.configuration.SdkPopup
import com.intellij.openapi.roots.ui.configuration.SdkPopupFactory
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.lang.JavaVersion
import org.jetbrains.jps.model.java.JdkVersionDetector
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JComponent

data class RuntimeChooserCustomItem(
  val version: String,
  override val homeDir: String,
) : RuntimeChooserItem(), RuntimeChooserItemWithFixedLocation

object RuntimeChooserAddCustomItem : RuntimeChooserItem()

object RuntimeChooserCustom {
  private val LOG = logger<RuntimeChooserCustom>()

  val sdkType
    get() = SdkType
      .getAllTypes()
      .singleOrNull(SimpleJavaSdkType.notSimpleJavaSdkTypeIfAlternativeExistsAndNotDependentSdkType()::value)

  val isActionAvailable
    get() = sdkType != null

  fun createSdkChooserPopup(parent: JComponent, model: RuntimeChooserModel) : SdkPopup? {
    return SdkPopupFactory
      .newBuilder()
      .withSdkType(sdkType ?: return null)
      .withSdkFilter {
        it != null && runCatching {
          val s = it.versionString
          s != null && JavaVersion.parse(s).feature >= 11
        }.getOrDefault(false)
      }
      .onSdkSelected { sdk -> importNewItem(parent, sdk, model) }
      .buildPopup()
  }

  private fun importNewItem(parent: JComponent, sdk: Sdk?, model: RuntimeChooserModel) {
    if (sdk == null) return

    object: Task.Backgroundable(null, LangBundle.message("progress.title.choose.ide.runtime.scanning.jdk", false, ALWAYS_BACKGROUND)) {
      override fun run(indicator: ProgressIndicator) {
        try {
          val homeDir = sdk.homePath ?: return
          val version = sdk.homePath ?: return
          val info = JdkVersionDetector.getInstance().detectJdkVersionInfo(homeDir) ?: return
          val fullVersion = listOfNotNull(info.displayName, info.version?.toString() ?: version).joinToString(" ")

          if (info.version.feature < 11) {
            return service<RuntimeChooserMessages>().notifyCustomJdkVersionIsTooOld(parent, homeDir, info.version.toString(), "11")
          }

          if (!isJdkAlive(homeDir)) {
            return service<RuntimeChooserMessages>().notifyCustomJdkDoesNotStart(parent, homeDir)
          }

          val newItem = RuntimeChooserCustomItem(fullVersion, homeDir)
          invokeLater(ModalityState.stateForComponent(parent)) {
            model.addExistingSdkItem(newItem)
          }
        } catch (t: Throwable) {
          LOG.warn("Failed to scan JDK for boot runtime: $sdk, ${sdk.homeDirectory}. ${t.message}", t)
        }
      }
    }.queue()
  }

  private fun isJdkAlive(javaHome: String): Boolean {
    val java = when {
      SystemInfo.isWindows -> "bin/java.exe"
      SystemInfo.isMac -> "Contents/Home/bin/java"
      else -> "bin/java"
    }

    val bin = Path.of(javaHome, java)
    if (!Files.isRegularFile(bin) || !(SystemInfo.isWindows || Files.isExecutable(bin))) return false

    try {
      val cmd = GeneralCommandLine(bin.toString(), "-version")
      return CapturingProcessHandler(cmd).runProcess(30_000).exitCode == 0
    }
    catch (t: Throwable) {
      LOG.warn("Failed to run JDK for boot runtime: $javaHome}. ${t.message}", t)
      return false
    }
  }
}
