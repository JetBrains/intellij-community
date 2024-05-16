// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.projectRoots.impl

import com.intellij.java.JavaBundle
import com.intellij.openapi.updateSettings.impl.UpdateSettingsUIProvider
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected

class JdkUpdaterConfigurable: UpdateSettingsUIProvider {

  override fun init(panel: Panel) {
    panel.apply {
      row {
        checkBox(JavaBundle.message("checkbox.check.for.jdk.updates"))
          .bindSelected({ Registry.`is`("jdk.updater") }, { Registry.get("jdk.updater").setValue(it) })
      }
        .bottomGap(BottomGap.MEDIUM)
    }
  }
}