// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.templates

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Provides a way to perform additional customizations after a module is created from a template.
 * The implementations must be registered as extensions for `com.intellij.templateModuleBuilderPostprocessing` extension point.
 */
@ApiStatus.Internal
interface TemplateModuleBuilderPostprocessing {
  fun postProcessCreatedModule(module: Module)
  fun applyProjectDefaults(project: Project)
}