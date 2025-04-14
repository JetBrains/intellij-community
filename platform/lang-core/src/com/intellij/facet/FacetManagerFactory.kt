// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FacetManagerFactory {

  fun getFacetManager(module: Module): FacetManager

  companion object {
    @JvmStatic
    fun getInstance(project: Project): FacetManagerFactory = project.service<FacetManagerFactory>()
  }
}