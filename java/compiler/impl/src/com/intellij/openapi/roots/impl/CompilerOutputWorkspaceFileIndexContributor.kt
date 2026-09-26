// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.JavaProjectSettingsEntity
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar

internal class JavaProjectSettingsWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<JavaProjectSettingsEntity> {
  override val entityClass: Class<JavaProjectSettingsEntity> get() = JavaProjectSettingsEntity::class.java

  override fun registerFileSets(entity: JavaProjectSettingsEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    entity.compilerOutput?.let {
      registrar.registerExcludedRoot(it, entity)
    }
  }
}

internal class JavaModuleSettingsWorkspaceFileIndexContributor : WorkspaceFileIndexContributor<JavaModuleSettingsEntity> {
  override val entityClass: Class<JavaModuleSettingsEntity> get() = JavaModuleSettingsEntity::class.java

  override fun registerFileSets(entity: JavaModuleSettingsEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    if (!entity.inheritedCompilerOutput && entity.excludeOutput) {
      entity.compilerOutput?.let {
        registrar.registerExcludedRoot(it, entity)
      }
      entity.compilerOutputForTests?.let {
        registrar.registerExcludedRoot(it, entity)
      }
    }
  }
}
