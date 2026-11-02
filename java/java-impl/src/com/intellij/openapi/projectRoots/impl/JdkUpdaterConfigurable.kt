// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.java.JavaBundle
import com.intellij.openapi.projectRoots.impl.jdkDownloader.JdkUpdaterSettings
import com.intellij.openapi.updateSettings.impl.UpdateSettingsUIProvider
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.selected

internal class JdkUpdaterConfigurable : UpdateSettingsUIProvider {

  override fun init(panel: Panel) {
    val settings = JdkUpdaterSettings.getInstance()
    panel.apply {
      lateinit var checkForJdkUpdates: Cell<JBCheckBox>
      row {
        checkForJdkUpdates = checkBox(JavaBundle.message("checkbox.check.for.jdk.updates"))
          .bindSelected({ Registry.`is`("jdk.updater") }, { Registry.get("jdk.updater").setValue(it) })
      }
      indent {
        row {
          checkBox(JavaBundle.message("checkbox.remove.outdated.jdks.automatically"))
            .bindSelected(settings.state::isRemoveOutdatedJdksAutomatically)
        }
      }.enabledIf(checkForJdkUpdates.selected)
    }
  }
}