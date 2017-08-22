/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.impl

import com.intellij.execution.Executor
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationType

fun countSettingsOfType(allSettings: List<RunnerAndConfigurationSettings>, type: ConfigurationType) = allSettings.count { it.type == type }

internal class RunConfigurationBean {
  val settings: RunnerAndConfigurationSettings
  val configurable: SingleConfigurationConfigurable<*>?

  constructor(settings: RunnerAndConfigurationSettings) {
    this.settings = settings
    configurable = null
  }

  constructor(configurable: SingleConfigurationConfigurable<*>) {
    this.configurable = configurable
    settings = this.configurable.settings as RunnerAndConfigurationSettings
  }

  override fun toString(): String {
    return settings.toString()
  }
}

enum class RunConfigurableNodeKind {
  CONFIGURATION_TYPE, FOLDER, CONFIGURATION, TEMPORARY_CONFIGURATION, UNKNOWN;

  fun supportsDnD() = this == FOLDER || this == CONFIGURATION || this == TEMPORARY_CONFIGURATION

  val isConfiguration: Boolean
    get() = (this == CONFIGURATION) or (this == TEMPORARY_CONFIGURATION)
}

interface RunDialogBase {
  fun setOKActionEnabled(isEnabled: Boolean)

  val executor: Executor?

  fun setTitle(title: String)

  fun clickDefaultButton()
}