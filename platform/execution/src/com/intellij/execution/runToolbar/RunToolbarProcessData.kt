// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.execution.RunManager.Companion.getInstance
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.annotations.ApiStatus
import java.util.function.Consumer

class RunToolbarProcessData {
  companion object {
    @ApiStatus.Internal
    @JvmField
    val RW_SLOT: DataKey<String> = DataKey.create("RunWidgetSlot")

    @ApiStatus.Internal
    @JvmField
    val RW_MAIN_CONFIGURATION_ID: Key<String> = Key<String>("RunWidgetMainRunConfigurationId")

    @ApiStatus.Internal
    @JvmField
    val RUN_TOOLBAR_SUPPRESS_MAIN_SLOT_USER_DATA_KEY: Key<Boolean> = Key<Boolean>("RUN_TOOLBAR_SUPPRESS_MAIN_SLOT_USER_DATA_KEY")

    @ApiStatus.Internal
    @JvmStatic
    fun prepareBaseSettingCustomization(settings: RunnerAndConfigurationSettings?, addition: Consumer<ExecutionEnvironment>? = null): Consumer<ExecutionEnvironment>? {
      return settings?.let {
        Consumer { ee: ExecutionEnvironment ->
          ee.putUserData(RW_MAIN_CONFIGURATION_ID, settings.uniqueID)
        }.mix(addition)
      } ?: addition
    }

    @ApiStatus.Internal
    @JvmStatic
    fun prepareSuppressMainSlotCustomization(project: Project, addition: Consumer<ExecutionEnvironment>? = null): Consumer<in ExecutionEnvironment> {
      return Consumer { ee: ExecutionEnvironment ->
        val runManager = getInstance(project)
        if (runManager.isRiderRunWidgetActive()) {
          ee.putUserData(RUN_TOOLBAR_SUPPRESS_MAIN_SLOT_USER_DATA_KEY, true)
        }
      }.mix(addition)
    }

    private fun Consumer<ExecutionEnvironment>.mix(addition: Consumer<ExecutionEnvironment>?): Consumer<ExecutionEnvironment> {
      addition ?: return this
      return Consumer { ee: ExecutionEnvironment ->
        this.accept(ee)
        addition.accept(ee)
      }
    }
  }
}