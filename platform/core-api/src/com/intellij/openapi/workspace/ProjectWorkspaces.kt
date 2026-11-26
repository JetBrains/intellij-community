// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Experimental

package com.intellij.openapi.workspace

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * The workspace for the lifetime of this project. Any thread can access it without a read action.
 *
 * @throws IllegalStateException in codeServer and in the analyzer, where the workspace manager is unavailable.
 */
val Project.workspace: Workspace
  get() = ApplicationManager.getApplication().service<WorkspaceManager>().getWorkspace(this)
