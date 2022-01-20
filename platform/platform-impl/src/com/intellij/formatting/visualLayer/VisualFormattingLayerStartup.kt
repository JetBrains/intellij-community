// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity


class VisualFormattingLayerStartup : StartupActivity {

  override fun runActivity(project: Project) {
    val service = VisualFormattingLayerService.getInstance()
    service.enabledGlobally = UISettings.instance.showVisualFormattingLayer
    service.addEditorFactoryListener()
  }

}
