// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge.impl.java

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.javaSettings
import com.intellij.java.workspace.entities.modifyEntity
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl
import com.intellij.openapi.roots.ModuleExtension
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.pom.java.LanguageLevel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModuleEntity
import com.intellij.workspaceModel.ide.java.languageLevel
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleExtensionBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleExtensionBridgeFactory

internal class LanguageLevelModuleExtensionBridge private constructor(
  private val module: ModuleBridge,
  private val entityStorage: VersionedEntityStorage,
  private val diff: MutableEntityStorage?,
) : LanguageLevelModuleExtensionImpl(), ModuleExtensionBridge {
  private var changed = false
  private val moduleEntity
    get() = module.findModuleEntity(entityStorage.current)

  override fun setLanguageLevel(languageLevel: LanguageLevel?) {
    if (diff == null) error("Cannot modify data via read-only extensions")
    val moduleEntity = moduleEntity ?: error("Cannot find entity for $module")
    val javaSettings = moduleEntity.javaSettings
    if (javaSettings != null) {
      diff.modifyEntity(javaSettings) {
        this.languageLevel = languageLevel
      }
    }
    else if (languageLevel != null) {
      diff addEntity JavaModuleSettingsEntity(inheritedCompilerOutput = true,
                                              excludeOutput = true,
                                              entitySource = moduleEntity.entitySource) {
        languageLevelId = languageLevel.name
        module = moduleEntity
      }
    }
  }

  override fun getLanguageLevel(): LanguageLevel? {
    return moduleEntity?.javaSettings?.languageLevel
  }

  override fun isChanged(): Boolean = changed

  override fun getModifiableModel(writable: Boolean): ModuleExtension {
    throw UnsupportedOperationException("This method must not be called for extensions backed by workspace model")
  }

  override fun commit() {}

  class Factory : ModuleExtensionBridgeFactory<LanguageLevelModuleExtensionBridge> {
    override fun createExtension(module: ModuleBridge,
                                 entityStorage: VersionedEntityStorage,
                                 diff: MutableEntityStorage?): LanguageLevelModuleExtensionBridge {
      return LanguageLevelModuleExtensionBridge(module = module, entityStorage = entityStorage, diff = diff)
    }
  }
}