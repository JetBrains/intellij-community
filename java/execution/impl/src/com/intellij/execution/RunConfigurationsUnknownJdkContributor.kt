// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution

import com.intellij.execution.configurations.ConfigurationWithAlternativeJre
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.impl.UnknownSdkContributor
import com.intellij.openapi.projectRoots.impl.UnknownSdkTracker
import com.intellij.openapi.roots.ui.configuration.UnknownSdk
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry

private fun RunnerAndConfigurationSettings.isWatched() = !isTemplate && !isTemporary && isShared
private fun isRunConfigurationsWatchEnabled() = Registry.`is`("jdk.auto.runConfigurations")

private class RunConfigurationsUnknownJdkContributorRefresher : StartupActivity.DumbAware {
  override fun runActivity(project: Project) {
    if (!isRunConfigurationsWatchEnabled()) {
      return
    }

    project.messageBus.connect().subscribe(RunManagerListener.TOPIC, object: RunManagerListener {
      override fun runConfigurationAdded(settings: RunnerAndConfigurationSettings) {
        scheduleReload(settings)
      }

      override fun runConfigurationChanged(settings: RunnerAndConfigurationSettings) {
        scheduleReload(settings)
      }

      override fun runConfigurationRemoved(settings: RunnerAndConfigurationSettings) {
        scheduleReload(settings)
      }

      private fun scheduleReload(settings: RunnerAndConfigurationSettings) {
        if (!isRunConfigurationsWatchEnabled() || !settings.isWatched()) {
          return
        }

        UnknownSdkTracker.getInstance(project).updateUnknownSdks()
      }
    })
  }
}

private class RunConfigurationsUnknownJdkContributor : UnknownSdkContributor {
  override fun contributeUnknownSdks(project: Project): List<UnknownSdk> {
    if (!isRunConfigurationsWatchEnabled()) return listOf()
    val sdk = JavaSdk.getInstance()

    val names = RunManager.getInstance(project).allSettings
      .filter { it.isWatched() }
      .map { it.configuration }
      .asSequence()
      .filterIsInstance(ConfigurationWithAlternativeJre::class.java)
      .filter { it.isAlternativeJrePathEnabled }
      .mapNotNull { it.alternativeJrePath }
      .filterNot { it.contains("/") }
      .distinct()
      .filter { ProjectJdkTable.getInstance().findJdk(it) == null }
      .toSortedSet().toList()

    return names.map { sdkName ->
      object : UnknownSdk {
        override fun toString() = "UnknownJdk { name = $sdkName from run configurations }"
        override fun getSdkType() = sdk
        override fun getSdkName() = sdkName
      }
    }
  }
}
