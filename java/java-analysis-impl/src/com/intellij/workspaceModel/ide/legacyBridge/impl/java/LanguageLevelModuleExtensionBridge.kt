// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.legacyBridge.impl.java

import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
import com.intellij.openapi.roots.ModuleExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.findModuleEntity
import com.intellij.workspaceModel.ide.java.languageLevel
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleExtensionBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleExtensionBridgeFactory
import com.intellij.workspaceModel.storage.VersionedEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.ModifiableJavaModuleSettingsEntity
import com.intellij.workspaceModel.storage.bridgeEntities.addJavaModuleSettingsEntity

class LanguageLevelModuleExtensionBridge private constructor(private val module: ModuleBridge,
                                                             private val entityStorage: VersionedEntityStorage,
                                                             private val diff: WorkspaceEntityStorageDiffBuilder?) : LanguageLevelModuleExtensionImpl(), ModuleExtensionBridge {
  private var changed = false
  private val moduleEntity
    get() = entityStorage.current.findModuleEntity(module)

  override fun setLanguageLevel(languageLevel: LanguageLevel?) {
    if (diff == null) error("Cannot modify data via read-only extensions")
    val moduleEntity = moduleEntity ?: error("Cannot find entity for $module")
    val javaSettings = moduleEntity.javaSettings
    if (javaSettings != null) {
      diff.modifyEntity(ModifiableJavaModuleSettingsEntity::class.java, javaSettings) {
        this.languageLevel = languageLevel
      }
    }
    else if (languageLevel != null) {
      diff.addJavaModuleSettingsEntity(inheritedCompilerOutput = true, excludeOutput = true, compilerOutput = null,
                                       compilerOutputForTests = null, languageLevelId = languageLevel.name, module = moduleEntity,
                                       source = moduleEntity.entitySource)
    }
  }

  override fun getLanguageLevel(): LanguageLevel? {
    return moduleEntity?.javaSettings?.languageLevel
  }

  override fun isChanged(): Boolean = changed

  override fun getModifiableModel(writable: Boolean): ModuleExtension {
    throw UnsupportedOperationException("This method must not be called for extensions backed by workspace model")
  }

  override fun commit() = Unit
  override fun dispose() = Unit

  companion object : ModuleExtensionBridgeFactory<LanguageLevelModuleExtensionBridge> {
    override fun createExtension(module: ModuleBridge,
                                 entityStorage: VersionedEntityStorage,
                                 diff: WorkspaceEntityStorageDiffBuilder?): LanguageLevelModuleExtensionBridge {
      return LanguageLevelModuleExtensionBridge(module, entityStorage, diff)
    }
  }
}