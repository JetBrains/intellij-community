// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.projectRoots.impl.jdkDownloader

import com.intellij.lang.LangBundle
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.projectRoots.impl.SdkVersionUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.SystemProperties
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

interface RuntimeChooserItemWithFixedLocation {
  val homeDir: String
}

data class RuntimeChooserCurrentItem(
  val isBundled: Boolean,
  override val homeDir: String,
  @NlsSafe val displayName: String?,
  val version: String?
) : RuntimeChooserItem(), RuntimeChooserItemWithFixedLocation {
  companion object
}

fun RuntimeChooserCurrentItem.Companion.currentRuntime(): RuntimeChooserCurrentItem {
  val javaHome = SystemProperties.getJavaHome()
  val isBundled = runCatching { PathManager.isUnderHomeDirectory(javaHome) }.getOrElse { false }
  val info = runCatching { SdkVersionUtil.getJdkVersionInfo(javaHome) }.getOrNull()

  val fullVersion = runCatching {
    val releaseFile = Paths.get(javaHome, "release")
    if (!Files.isRegularFile(releaseFile)) return@runCatching null
    val p = Properties()
    Files.newInputStream(releaseFile).use { p.load(it) }

    p.getProperty("IMPLEMENTOR_VERSION")
      ?.removeSurrounding("\"")
      ?.trim()
      ?.removePrefix("JBR-")
      ?.trim()
  }.getOrNull()

  return RuntimeChooserCurrentItem(
    isBundled = isBundled,
    homeDir = javaHome,
    displayName = info?.displayName,
    version = fullVersion ?: info?.version?.toString(),
  )
}

fun RuntimeChooserModel.fetchCurrentRuntime() {
  object : Task.Backgroundable(null, LangBundle.message("progress.title.choose.ide.runtime.scanning.current.runtime")) {
    override fun run(indicator: ProgressIndicator) {
      val runtime = RuntimeChooserCurrentItem.currentRuntime()
      invokeLater(modalityState = ModalityState.any()) {
        updateCurrentRuntime(runtime)
      }
    }
  }.queue()
}
