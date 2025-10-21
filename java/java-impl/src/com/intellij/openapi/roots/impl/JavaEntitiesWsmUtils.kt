// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl

import com.intellij.java.workspace.entities.JavaProjectSettingsEntity
import com.intellij.java.workspace.entities.JavaProjectSettingsEntityBuilder
import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.workspaceModel.ide.WsmProjectSettingsEntityUtils
import com.intellij.workspaceModel.ide.WsmSingletonEntityUtils
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeJpsEntitySourceFactoryInternal
import com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory
import java.util.function.Consumer

internal object JavaEntitiesWsmUtils {

  @JvmStatic
  fun <E : WorkspaceEntity> getSingleEntity(storage: EntityStorage, entityClass: Class<E>): E? {
    return WsmSingletonEntityUtils.getSingleEntity(storage, entityClass)
  }

  @JvmStatic
  fun addOrModifyJavaProjectSettingsEntity(project: Project, mutableStorage: MutableEntityStorage, modFunction: Consumer<JavaProjectSettingsEntityBuilder>) {
    val sourceFactory = LegacyBridgeJpsEntitySourceFactory.getInstance(project) as LegacyBridgeJpsEntitySourceFactoryInternal
    val entitySource: EntitySource = sourceFactory.createEntitySourceForProjectSettings() ?: return // do not touch the default project

    WsmProjectSettingsEntityUtils.addOrModifyProjectSettingsEntity(project, mutableStorage) { projectSettingsBuilder ->
      WsmSingletonEntityUtils.addOrModifySingleEntity(mutableStorage,
                                                      JavaProjectSettingsEntity::class.java, JavaProjectSettingsEntityBuilder::class.java,
                                                      {
                                          JavaProjectSettingsEntity(entitySource) { projectSettings = projectSettingsBuilder }
                                        }, modFunction::accept)
    }
  }
}