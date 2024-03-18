// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.settings

import com.intellij.application.options.editor.CheckboxDescriptor
import com.intellij.application.options.editor.checkBox
import com.intellij.history.integration.LocalHistoryBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.platform.lvcs.impl.ui.ActivityView
import com.intellij.ui.dsl.builder.panel

class ActivityConfigurable : BoundConfigurable(LocalHistoryBundle.message("activity.configurable.title")), SearchableConfigurable {
  override fun createPanel(): DialogPanel = panel {
    val activityViewApplicationSettings = service<ActivityViewApplicationSettings>()
    row {
      checkBox(CheckboxDescriptor(LocalHistoryBundle.message("activity.configurable.enable.checkbox.title"),
                                  activityViewApplicationSettings::isActivityToolWindowEnabled))
        .comment(LocalHistoryBundle.message("activity.configurable.enable.checkbox.comment"))
    }
  }

  override fun getId(): String = "lvcs.activity"
}

class ActivityConfigurableProvider : ConfigurableProvider() {
  override fun createConfigurable(): Configurable? {
    if (!ActivityView.isViewAvailable()) return null
    return ActivityConfigurable()
  }

  override fun canCreateConfigurable() = ActivityView.isViewAvailable()
}