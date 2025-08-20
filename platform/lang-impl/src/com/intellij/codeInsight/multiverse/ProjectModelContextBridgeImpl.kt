// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.multiverse

import com.intellij.multiverse.LibraryContextImpl
import com.intellij.multiverse.ModuleContextImpl
import com.intellij.multiverse.SdkContextImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkContext
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryContext
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.workspace.storage.CachedValueWithParameter
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.findLibraryEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.SdkBridgeImpl.Companion.findSdkEntity
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity

internal class ProjectModelContextBridgeImpl(private val project: Project) : ProjectModelContextBridge {

  private val sdkCachedValue = CachedValueWithParameter<Sdk, SdkContext> { storage, parameter ->
    val sdkEntity = storage.findSdkEntity(parameter)
    val context = sdkEntity?.let { SdkContextImpl(sdkEntity.createPointer(), project) }
    context ?: NULL
  }

  override fun getContext(entry: Module): ModuleContext? {
    val snapshot = currentSnapshot
    val moduleEntity = entry.findModuleEntity(snapshot) ?: return null
    return ModuleContextImpl(moduleEntity.createPointer(), project)
  }

  override fun getContext(entry: Library): LibraryContext? {
    val snapshot = currentSnapshot
    val lib = snapshot.findLibraryEntity(entry as LibraryBridge) ?: return null
    return LibraryContextImpl(lib.createPointer(), project)
  }

  override fun getContext(entry: Sdk): SdkContext? {
    val entityStorage = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).entityStorage
    return entityStorage.cachedValue(sdkCachedValue, entry).takeUnless { it === NULL }
  }

  private val currentSnapshot: ImmutableEntityStorage
    get() = WorkspaceModel.getInstance(project).currentSnapshot
}

private object NULL : SdkContext {
  override fun getSdk() = throw UnsupportedOperationException("NULL object can't be used")
}