// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler

import com.intellij.compiler.JavaCompilerOptionsWorkspaceModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaCompilerConfigurationProxy

/**
 * A [JavaCompilerConfigurationProxy] that reads per-module additional compiler options from the workspace model
 * ([com.intellij.java.workspace.entities.JavaModuleCompilerOptionsEntity]) first, never falling back to the legacy
 * `compiler.xml` based [JavacConfiguration] storage
 *
 * Used only in LSP at the moment, see IDEA-307379 for more details
 */
internal class WorkspaceModelJavaCompilerConfigurationProxy : JavaCompilerConfigurationProxy() {
  override fun getAdditionalOptionsImpl(project: Project, module: Module): List<String> {
    return JavaCompilerOptionsWorkspaceModel.getModuleAdditionalOptions(module) ?: emptyList()
  }

  override fun setAdditionalOptionsImpl(project: Project, module: Module, options: List<String>) {
    JavaCompilerOptionsWorkspaceModel.setModuleAdditionalOptions(module, options)
  }
}
