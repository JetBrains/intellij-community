// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler

import com.intellij.java.workspace.entities.JavaModuleCompilerOptionsEntity
import com.intellij.java.workspace.entities.javaCompilerOptions
import com.intellij.java.workspace.entities.modifyJavaModuleCompilerOptionsEntity
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus

/**
 * Read/write access to per-module Java compiler options that are stored in the workspace model
 * ([JavaModuleCompilerOptionsEntity]) instead of `compiler.xml` ([JavacConfiguration]).
 *
 * Reading is used by Java highlighting (through [com.intellij.psi.JavaCompilerConfigurationProxy] →
 * [WorkspaceModelJavaCompilerConfigurationProxy]) to pick up build-tool defined options such as `--add-exports`.
 *
 *  Used only in LSP at the moment, see IDEA-307379 for more details
 */
@ApiStatus.Experimental
object JavaCompilerOptionsWorkspaceModel {
  /**
   * Returns the additional compiler options stored for [module] in the workspace model, or `null` when there is no
   * [JavaModuleCompilerOptionsEntity] for the module (the caller should then fall back to the legacy storage).
   */
  fun getModuleAdditionalOptions(module: Module): List<String>? {
    val snapshot = module.project.workspaceModel.currentSnapshot
    val moduleEntity = snapshot.resolve(ModuleId(module.name)) ?: return null
    return moduleEntity.javaCompilerOptions?.additionalOptions
  }

  /**
   * Stores [options] for [module] in the workspace model.
   *
   * The created [JavaModuleCompilerOptionsEntity] reuses the [EntitySource][com.intellij.platform.workspace.storage.EntitySource]
   * of its parent module so it is serialized together with the module configuration — this is the recommended way to
   * pick the entity source for a per-module child entity.
   */
  @ApiStatus.Experimental
  fun setModuleAdditionalOptions(module: Module, options: List<String>) {
    setModulesAdditionalOptions(module.project, mapOf(module to options))
  }

  /**
   * Stores the additional compiler options for several modules in a single workspace model update.
   *
   * Each created [JavaModuleCompilerOptionsEntity] reuses the
   * [EntitySource][com.intellij.platform.workspace.storage.EntitySource] of its parent module so it is serialized
   * together with the module configuration.
   */
  fun setModulesAdditionalOptions(project: Project, optionsByModule: Map<Module, List<String>>) {
    if (optionsByModule.isEmpty()) return
    project.workspaceModel.updateProjectModel("Update Java compiler options") { storage ->
      for ((module, options) in optionsByModule) {
        val moduleEntity = storage.resolve(ModuleId(module.name)) ?: continue
        setModuleAdditionalOptions(storage, moduleEntity, options)
      }
    }
  }

  /**
   * Stores [options] for [moduleEntity] in the given [storage]. Use this overload to write the options as part of an
   * existing workspace model transaction (e.g. during project import) instead of opening a new one.
   */
  fun setModuleAdditionalOptions(storage: MutableEntityStorage, moduleEntity: ModuleEntity, options: List<String>) {
    val existing = moduleEntity.javaCompilerOptions
    if (existing != null) {
      storage.modifyJavaModuleCompilerOptionsEntity(existing) {
        this.additionalOptions = options.toMutableList()
      }
    }
    else {
      storage.modifyModuleEntity(moduleEntity) {
        this.javaCompilerOptions = JavaModuleCompilerOptionsEntity(options, moduleEntity.entitySource)
      }
    }
  }
}
