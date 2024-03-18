// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom.java

import com.intellij.openapi.application.*
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.containers.MultiMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

private class AcceptedLanguageLevelsSettingsStartupActivity : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val languageLevels = constrainedReadAction(ReadConstraint.inSmartMode(project)) {
      val previewLevels = TreeSet<LanguageLevel>()
      val unacceptedLevels = AcceptedLanguageLevelsSettings.getUnacceptedLevels(project, previewLevels)
      if (unacceptedLevels.isEmpty && previewLevels.isEmpty()) {
        null
      }
      else {
        LanguageLevels(unacceptedLevels, previewLevels)
      }
    } ?: return
    withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
      AcceptedLanguageLevelsSettings.applyUnacceptedLevels(project, languageLevels.unacceptedLevels, languageLevels.previewLevels)
    }
  }
}

private data class LanguageLevels(@JvmField val unacceptedLevels: MultiMap<LanguageLevel?, Module?>,
                                  @JvmField val previewLevels: TreeSet<LanguageLevel>)
