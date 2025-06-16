// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the
// Apache 2.0 license.
package org.jetbrains.jewel.samples.ideplugin.dialog

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class ComponentShowcaseDialogAction : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = checkNotNull(event.project) { "Project not available" }
        val scope = project.service<ProjectScopeProviderService>().scope

        scope.launch(Dispatchers.EDT) { ComponentShowcaseDialog(project).showAndGet() }
    }
}
