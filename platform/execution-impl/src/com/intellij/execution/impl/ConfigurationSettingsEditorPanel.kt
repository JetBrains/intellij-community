// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.CollapsibleRow
import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import java.awt.GridBagLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

private const val EXPAND_PROPERTY_KEY = "ExpandBeforeRunStepsPanel"

@ApiStatus.Internal
class ConfigurationSettingsEditorPanel(rcStorage: JComponent?) {

  lateinit var isAllowRunningInParallelCheckBox: JCheckBox
  lateinit var targetPanel: JPanel
  lateinit var componentPlace: JPanel
  lateinit var beforeRunStepsRow: CollapsibleRow
  lateinit var beforeRunStepsPlaceholder: Placeholder

  @JvmField
  val panel = panel {
    row {
      isAllowRunningInParallelCheckBox = checkBox(ExecutionBundle.message("run.configuration.allow.running.parallel.tag"))
        .resizableColumn()
        .align(AlignX.RIGHT)
        .component

      rcStorage?.let {
        cell(it)
      }
    }

    row {
      targetPanel = cell(JPanel(GridBagLayout()))
        .align(AlignX.FILL)
        .component
    }

    row {
      componentPlace = cell(JPanel())
        .align(AlignX.FILL)
        .component
    }

    beforeRunStepsRow = collapsibleGroup("") {
      row {
        beforeRunStepsPlaceholder = placeholder()
          .align(AlignX.FILL)
      }
    }
    beforeRunStepsRow.expanded = PropertiesComponent.getInstance().getBoolean(EXPAND_PROPERTY_KEY, true)
    beforeRunStepsRow.addExpandedListener {
      PropertiesComponent.getInstance().setValue(EXPAND_PROPERTY_KEY, beforeRunStepsRow.expanded.toString())
    }
  }
}
