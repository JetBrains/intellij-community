// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectWizard

import com.intellij.ide.projectWizard.generators.SdkPreIndexingService
import com.intellij.openapi.components.service
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.AddJdkService
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

  fun isAtLeast(version: Int, strict: Boolean = false): Boolean {
    val defaultValue = !strict
    return when (this) {
             is DownloadJdk -> JavaVersion.tryParse(task.plannedVersion)?.isAtLeast(version)
             is ExistingJdk -> if (jdk.versionString == null) defaultValue
                               else JavaVersion.tryParse(jdk.versionString)?.isAtLeast(version)
             is DetectedJdk -> JavaVersion.tryParse(this.version)?.isAtLeast(version)
             else -> false
           } ?: defaultValue
  }

  val versionString: String?
    get() = when (this) {
      is DownloadJdk -> task.plannedVersion
      is ExistingJdk -> jdk.versionString
      is DetectedJdk -> version
      else -> null
    }

  val javaVersion: JavaVersion?
    get() = JavaVersion.tryParse(versionString)

  val name: String?
    get() = when (this) {
      is DownloadJdk -> task.suggestedSdkName
      is ExistingJdk -> jdk.name
      is DetectedJdk -> version
      else -> null
    }

  val downloadTask: SdkDownloadTask?
    get() = when (this) {
      is DownloadJdk -> task
      else -> null
    }

  fun prepareJdk(): Sdk? = when (this) {
    is ExistingJdk -> jdk
    is DetectedJdk -> {
      val sdk = service<AddJdkService>().createIncompleteJdk(home)
      sdk?.let { service<SdkPreIndexingService>().requestPreIndexation(it) }
      sdk
    }
    else -> null
  }

  companion object {
    fun fromJdk(jdk: Sdk?): ProjectWizardJdkIntent = when (jdk) {
      null -> NoJdk
      else -> ExistingJdk(jdk)
    }
  }
}