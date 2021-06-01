// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.legacyBridge.impl.java

import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
import com.intellij.openapi.roots.ModuleExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleExtensionBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleExtensionBridgeFactory
import com.intellij.workspaceModel.storage.ExternalEntityMapping
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.ModifiableJavaModuleSettingsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addJavaModuleSettingsEntity

class LanguageLevelModuleExtensionBridge private constructor(private val module: ModuleBridge,
                                                             private val entityStorage: VersionedEntityStorage,
                                                             private val diff: WorkspaceEntityStorageDiffBuilder?) : LanguageLevelModuleExtension, ModuleExtensionBridge() {
  private var changed = false
  private val moduleEntity
    get() = entityStorage.current.moduleMap.getEntities(module).firstOrNull() as ModuleEntity?

  override fun setLanguageLevel(languageLevel: LanguageLevel?) {
    if (diff == null) error("Cannot modify data via read-only extensions")
    val moduleEntity = moduleEntity ?: error("Cannot find entity for $module")
    val javaSettings = moduleEntity.javaSettings
    if (javaSettings != null) {
      diff.modifyEntity(ModifiableJavaModuleSettingsEntity::class.java, javaSettings) {
        languageLevelId = languageLevel?.name
      }
    }
    else if (languageLevel != null) {
      diff.addJavaModuleSettingsEntity(inheritedCompilerOutput = true, excludeOutput = true, compilerOutput = null,
                                       compilerOutputForTests = null, languageLevelId = languageLevel.name, module = moduleEntity,
                                       source = moduleEntity.entitySource)
    }
  }

  override fun getLanguageLevel(): LanguageLevel? {
    return moduleEntity?.javaSettings?.languageLevelId?.let {
      try {
        LanguageLevel.valueOf(it)
      }
      catch (e: IllegalArgumentException) {
        null
      }
    }
  }

  override fun isChanged(): Boolean = changed

  companion object : ModuleExtensionBridgeFactory {
    //todo move corresponding fields from ModuleManagerComponentBridge to projectModel.impl module
    private val WorkspaceEntityStorage.moduleMap: ExternalEntityMapping<ModuleBridge>
      get() = getExternalMapping("intellij.modules.bridge")

    override val originalExtensionType: Class<out ModuleExtension>
      get() = LanguageLevelModuleExtensionImpl::class.java

    override fun createExtension(module: ModuleBridge, entityStorage: VersionedEntityStorage,
                                 diff: WorkspaceEntityStorageDiffBuilder?): ModuleExtensionBridge {
      return LanguageLevelModuleExtensionBridge(module, entityStorage, diff)
    }
  }
}