// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus


/**
 * Use this extension to pre-configure newly created project with ExternalSystem-specific defaults.
 * These extensions are called from New Project Wizard.
 */
@ApiStatus.Internal
interface ExternalSystemProjectSetupExtension {
  fun setupCreatedProject(project: Project)
}