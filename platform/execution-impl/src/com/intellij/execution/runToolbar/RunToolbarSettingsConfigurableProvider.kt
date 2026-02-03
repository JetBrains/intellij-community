// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.lang.LangBundle
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class RunToolbarSettingsConfigurableProvider(val project: Project) : ConfigurableProvider() {
  override fun createConfigurable(): Configurable {
    return RunToolbarSettingsConfigurable(project)
  }

  override fun canCreateConfigurable(): Boolean {
    return ToolbarSettings.getInstance().isAvailable
  }
}

@ApiStatus.Internal
class RunToolbarSettingsConfigurable internal constructor(project: Project)
  : BoundConfigurable(LangBundle.message("run.toolbar.configurable.title")) {

  private val settings = RunToolbarSettings.getInstance(project)

  override fun createPanel(): DialogPanel {
    return panel {
      row {
        checkBox(LangBundle.message("run.toolbar.move.new.on.top"))
          .bindSelected(settings::getMoveNewOnTop, settings::setMoveNewOnTop)
      }
      row {
        checkBox(LangBundle.message("run.toolbar.update.main.by.selected"))
          .bindSelected(settings::getUpdateMainBySelected, settings::setUpdateMainBySelected)
      }
    }
  }
}