// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object ImportOldConfigsUsagesCollector : CounterUsagesCollector() {
  internal enum class InitialImportScenario {
    CLEAN_CONFIGS, IMPORTED_FROM_PREVIOUS_VERSION, IMPORT_SETTINGS_ACTION, RESTORE_DEFAULT_ACTION
  }

  private val GROUP = EventLogGroup("import.old.config", 7)

  private val INITIAL_IMPORT_SCENARIO = GROUP.registerEvent(
    "import.initially",
    EventFields.Enum("initial_import_scenario", InitialImportScenario::class.java),
    EventFields.Boolean("inherited_settings")
  )

  @Volatile private var initialImportScenario = null as InitialImportScenario?
  @Volatile private var inheritedSettings = false

  override fun getGroup(): EventLogGroup = GROUP

  @JvmStatic
  fun reportImportScenario(strategy: InitialImportScenario, inherited: Boolean) {
    initialImportScenario = strategy
    inheritedSettings = inherited
  }

  internal class Trigger : ApplicationActivity {
    override suspend fun execute() {
      initialImportScenario?.let {
        INITIAL_IMPORT_SCENARIO.log(it, inheritedSettings)
      }
    }
  }
}
