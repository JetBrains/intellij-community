// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

internal class ModuleWithDepUnion(
  rootContainer: RootContainer,
  override val mainModules: Set<Module>,
  allModules: Set<Module>,
  containsLibraries: Boolean,
  project: Project,
) : RootContainerScope(rootContainer, allModules, containsLibraries, project)
