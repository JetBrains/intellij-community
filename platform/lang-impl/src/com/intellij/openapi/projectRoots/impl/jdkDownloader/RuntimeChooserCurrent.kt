// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.projectRoots.impl.jdkDownloader.RuntimeChooserJreValidator.testNewJdkUnderProgress
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
interface RuntimeChooserItemWithFixedLocation {
  val homeDir: String
  val version: @NlsSafe String?
  val displayName: @NlsSafe String?
}

internal data class RuntimeChooserCurrentItem(
  val isBundled: Boolean,
  override val homeDir: String,
  override val displayName: String?,
  override val version: String?
) : RuntimeChooserItem(), RuntimeChooserItemWithFixedLocation {
  companion object
}

internal fun RuntimeChooserCurrentItem.Companion.currentRuntime(): RuntimeChooserCurrentItem {
  var javaHome = SystemProperties.getJavaHome()
  var javaName: String? = null
  var javaVersion: String? = System.getProperty("java.version") ?: null
  val isBundled = runCatching { PathManager.isUnderHomeDirectory(javaHome) }.getOrElse { false }

  testNewJdkUnderProgress(false, { javaHome }, object : RuntimeChooserJreValidatorCallback<Unit> {
    override fun onSdkResolved(displayName: String?, versionString: String, sdkHome: Path) {
      javaHome = sdkHome.toString()
      javaName = displayName
      javaVersion = versionString
    }
    override fun onError(message: String) = Unit
  })

  return RuntimeChooserCurrentItem(
    isBundled = isBundled,
    homeDir = javaHome,
    displayName = javaName,
    version = javaVersion,
  )
}

internal fun RuntimeChooserModel.fetchCurrentRuntime() {
  object : Task.Backgroundable(null, LangBundle.message("progress.title.choose.ide.runtime.scanning.current.runtime")) {
    override fun run(indicator: ProgressIndicator) {
      val runtime = RuntimeChooserCurrentItem.currentRuntime()
      invokeLater(modalityState = ModalityState.any()) {
        updateCurrentRuntime(runtime)
      }
    }
  }.queue()
}
