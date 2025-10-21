// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields.Boolean
import com.intellij.internal.statistic.eventLog.events.EventFields.Enum
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import javax.swing.JRadioButton

internal object ImportOldConfigsUsagesCollector : CounterUsagesCollector() {
  private enum class ImportOldConfigType {
    FROM_PREVIOUS, FROM_CUSTOM, DO_NOT_IMPORT, OTHER, NOT_INITIALIZED
  }

  enum class InitialImportScenario {
    CLEAN_CONFIGS, IMPORTED_FROM_PREVIOUS_VERSION, IMPORT_SETTINGS_ACTION, RESTORE_DEFAULT_ACTION,
    SHOW_DIALOG_REQUESTED_BY_PROPERTY, SHOW_DIALOG_NO_CONFIGS_FOUND
  }

  private val GROUP = EventLogGroup("import.old.config", 6)

  private val IMPORT_DIALOG_SHOWN_EVENT =
    GROUP.registerEvent("import.dialog.shown", Enum("selected", ImportOldConfigType::class.java), Boolean("config_folder_exists"))

  private val INITIAL_IMPORT_SCENARIO =
    GROUP.registerEvent("import.initially", Enum("initial_import_scenario", InitialImportScenario::class.java))

  @Volatile private var initialImportScenario = null as InitialImportScenario?
  @Volatile private var oldConfigPanelWasOpened = false
  @Volatile private var sourceConfigFolderExists = false
  @Volatile private var importType = ImportOldConfigType.NOT_INITIALIZED

  override fun getGroup(): EventLogGroup = GROUP

  fun reportImportScenario(strategy: InitialImportScenario) {
    initialImportScenario = strategy
  }

  fun saveImportOldConfigType(previous: JRadioButton, custom: JRadioButton, doNotImport: JRadioButton, configFolderExists: Boolean) {
    oldConfigPanelWasOpened = true
    sourceConfigFolderExists = configFolderExists
    importType = when {
      previous.isSelected -> ImportOldConfigType.FROM_PREVIOUS
      custom.isSelected -> ImportOldConfigType.FROM_CUSTOM
      doNotImport.isSelected -> ImportOldConfigType.DO_NOT_IMPORT
      else -> ImportOldConfigType.OTHER
    }
  }

  internal class Trigger : ApplicationActivity {
    override suspend fun execute() {
      initialImportScenario?.let {
        INITIAL_IMPORT_SCENARIO.log(it)
      }
      if (oldConfigPanelWasOpened) {
        IMPORT_DIALOG_SHOWN_EVENT.log(importType, sourceConfigFolderExists)
      }
    }
  }
}
