// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar.data

import com.intellij.execution.RunnerAndConfigurationSettings

interface RWSlotsConfigurationListener {
  fun slotsConfigurationChanged(slotConfigurations: Map<String, RunnerAndConfigurationSettings?>)

  fun configurationChanged(slotId: String, configuration: RunnerAndConfigurationSettings?)
}