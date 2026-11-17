// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.navigation

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.testFramework.common.runAll
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

/**
 * The run configurations that one test adds to the project. Create it with [tempRunConfigurationsFixture].
 * After the test, the fixture removes each configuration of [tempSettings] from the [RunManager].
 */
@TestOnly
class TempRunConfigurations internal constructor(private val project: Project) {
  /**
   * The configurations to remove after the test. A test can add a configuration that it creates in another way.
   */
  val tempSettings: MutableSet<RunnerAndConfigurationSettings> = HashSet()

  /**
   * Adds [configuration] to the [RunManager], selects it, and removes it after the test.
   */
  fun setupRunConfiguration(configuration: RunConfiguration) {
    val manager = RunManager.getInstance(project)
    val settings = RunnerAndConfigurationSettingsImpl(manager as RunManagerImpl, configuration)
    manager.addConfiguration(settings)
    tempSettings.add(settings)
    manager.selectedConfiguration = settings
  }

  internal fun removeAll() {
    val manager = RunManager.getInstance(project)
    runAll(tempSettings) { manager.removeConfiguration(it) }
  }
}

/**
 * Creates the [TempRunConfigurations] of one test in the project of [codeInsightFixture]. Declare it as an instance field.
 * It removes the configurations before the teardown of [codeInsightFixture].
 */
@TestOnly
fun tempRunConfigurationsFixture(codeInsightFixture: TestFixture<CodeInsightTestFixture>): TestFixture<TempRunConfigurations> =
  testFixture("tempRunConfigurationsFixture") {
    val configurations = TempRunConfigurations(codeInsightFixture.init().project)
    initialized(configurations) {
      withContext(Dispatchers.EDT) {
        configurations.removeAll()
      }
    }
  }
