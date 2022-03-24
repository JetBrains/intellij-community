// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runToolbar

import com.intellij.lang.LangBundle
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.layout.*

class RunToolbarSettingsConfigurableProvider(val project: Project) : ConfigurableProvider() {
  override fun createConfigurable(): Configurable {
    return RunToolbarSettingsConfigurable(project)
  }

  override fun canCreateConfigurable(): Boolean {
    return RunToolbarProcess.isSettingsAvailable
  }
}

class RunToolbarSettingsConfigurable internal constructor(private val project: Project)
  : BoundConfigurable(LangBundle.message("run.toolbar.configurable.title")) {

  private val settings = RunToolbarSettings.getInstance(project)

  override fun createPanel(): DialogPanel {
    val panel = panel {
      row {
        checkBox(LangBundle.message("run.toolbar.move.new.on.top"), settings::getMoveNewOnTop, settings::setMoveNewOnTop)
      }

      row {
        checkBox(LangBundle.message("run.toolbar.update.main.by.selected"), settings::getUpdateMainBySelected, settings::setUpdateMainBySelected)
      }

    }
    return panel
  }
}