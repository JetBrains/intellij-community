// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.codeInsight.documentation.render.DocRenderManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
internal class DocumentationSettingsListener : ProjectActivity {
  override suspend fun execute(project: Project) : Unit = blockingContext {
    ApplicationManager.getApplication().messageBus
      .simpleConnect()
      .subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
        override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
          if (StringUtil.startsWith(id, "documentation.components")) {
            DocRenderManager.resetAllEditorsToDefaultState()
          }
        }
      })
  }
}