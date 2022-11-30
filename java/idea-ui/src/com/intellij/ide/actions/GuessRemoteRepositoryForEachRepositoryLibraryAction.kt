// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.jarRepository.RepositoryLibrarySettings
import com.intellij.jarRepository.RepositoryLibraryUtils
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class GuessRemoteRepositoryForEachRepositoryLibraryAction : AnAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    e.presentation.isEnabledAndVisible = RepositoryLibrarySettings.getInstanceOrDefaults(project).isJarRepositoryBindingFeatureEnabled()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    RepositoryLibraryUtils.getInstance(project).guessAndBindRemoteRepositoriesModal()
  }
}