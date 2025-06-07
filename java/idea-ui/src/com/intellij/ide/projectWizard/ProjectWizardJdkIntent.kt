// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownload
import com.intellij.openapi.roots.ui.configuration.projectRoot.SdkDownloadTask
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.lang.JavaVersion

/**
 * Represents an intent to set up a JDK:
 *
 * - [NoJdk] - Create the project without selecting a JDK.
 * - [DownloadJdk] - Download a JDK on project creation.
 * - [ExistingJdk] - Use a JDK already configured, or from a location selected by the user.
 * - [DetectedJdk] - Configure a JDK detected by the IDE.
 */
sealed class ProjectWizardJdkIntent {
  data object NoJdk : ProjectWizardJdkIntent()

  data class DownloadJdk(val task: SdkDownloadTask) : ProjectWizardJdkIntent()

  data class ExistingJdk(val jdk: Sdk) : ProjectWizardJdkIntent()

  data object AddJdkFromPath : ProjectWizardJdkIntent()

  data class AddJdkFromJdkListDownloader(val extension: SdkDownload) : ProjectWizardJdkIntent()

  data class DetectedJdk(val version: @NlsSafe String, val home: @NlsSafe String, val isSymlink: Boolean) : ProjectWizardJdkIntent()

  fun isAtLeast(version: Int): Boolean = when (this) {
    is DownloadJdk -> JavaVersion.tryParse(task.plannedVersion)?.isAtLeast(version)
    is ExistingJdk -> if (jdk.versionString == null) true
                      else JavaVersion.tryParse(jdk.versionString)?.isAtLeast(version)
    is DetectedJdk -> JavaVersion.tryParse(this.version)?.isAtLeast(version)
    else -> false
  } ?: true

  val versionString: String?
    get() = when (this) {
      is DownloadJdk -> task.plannedVersion
      is ExistingJdk -> jdk.versionString
      is DetectedJdk -> version
      else -> null
    }

  val name: String?
    get() = when (this) {
      is DownloadJdk -> task.suggestedSdkName
      is ExistingJdk -> jdk.name
      is DetectedJdk -> version
      else -> null
    }
}