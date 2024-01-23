// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge.impl.java

import com.intellij.java.workspace.entities.JavaModuleSettingsEntity
import com.intellij.java.workspace.entities.javaSettings
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.roots.ModuleExtension
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleExtensionBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleExtensionBridgeFactory

internal class CompilerModuleExtensionBridge(
  private val module: ModuleBridge,
  private val entityStorage: VersionedEntityStorage,
  private val diff: MutableEntityStorage?
) : CompilerModuleExtension(), ModuleExtensionBridge {

  private var changed = false
  private val virtualFileManager = VirtualFileUrlManager.getInstance(module.project)

  private val javaSettings: JavaModuleSettingsEntity?
    get() = module.findModuleEntity(entityStorage.current)?.javaSettings

  private fun getSanitizedModuleName(): String =
    module.moduleFile?.nameWithoutExtension ?: module.name

  private fun getCompilerOutput(): VirtualFileUrl? = when {
    isCompilerOutputPathInherited -> {
      val url = CompilerProjectExtension.getInstance(module.project)?.compilerOutputUrl
      if (url != null) virtualFileManager.getOrCreateFromUri(url + "/" + PRODUCTION + "/" + getSanitizedModuleName()) else null
    }
    else -> javaSettings?.compilerOutput
  }

  private fun getCompilerOutputForTests(): VirtualFileUrl? = when {
    isCompilerOutputPathInherited -> {
      val url = CompilerProjectExtension.getInstance(module.project)?.compilerOutputUrl
      if (url != null) virtualFileManager.getOrCreateFromUri(url + "/" + TEST + "/" + getSanitizedModuleName()) else null
    }
    else -> javaSettings?.compilerOutputForTests
  }

  override fun isExcludeOutput(): Boolean = javaSettings?.excludeOutput ?: true
  override fun isCompilerOutputPathInherited(): Boolean = javaSettings?.inheritedCompilerOutput ?: true

  override fun getCompilerOutputUrl(): String? = getCompilerOutput()?.url
  override fun getCompilerOutputPath(): VirtualFile? = getCompilerOutput()?.virtualFile
  override fun getCompilerOutputPointer(): VirtualFilePointer? = getCompilerOutput() as? VirtualFilePointer

  override fun getCompilerOutputUrlForTests(): String? = getCompilerOutputForTests()?.url
  override fun getCompilerOutputPathForTests(): VirtualFile? = getCompilerOutputForTests()?.virtualFile
  override fun getCompilerOutputForTestsPointer(): VirtualFilePointer? = getCompilerOutputForTests() as? VirtualFilePointer

  override fun getModifiableModel(writable: Boolean): ModuleExtension = throw UnsupportedOperationException()

  override fun commit(): Unit = Unit
  override fun isChanged(): Boolean = changed

  private fun updateJavaSettings(updater: JavaModuleSettingsEntity.Builder.() -> Unit) {
    if (diff == null) {
      error("Read-only $javaClass")
    }

    val moduleEntity = module.findModuleEntity(entityStorage.current)
                       ?: error("Could not find entity for $module, ${module.hashCode()}, diff: ${entityStorage.base}")
    val moduleSource = moduleEntity.entitySource

    val oldJavaSettings = javaSettings ?: (diff addEntity JavaModuleSettingsEntity(inheritedCompilerOutput = true,
                                                                                   excludeOutput = true,
                                                                                   entitySource = moduleSource
    ) {
      compilerOutput = null
      compilerOutputForTests = null
      languageLevelId = null
      module = moduleEntity
    })

    diff.modifyEntity(JavaModuleSettingsEntity.Builder::class.java, oldJavaSettings, updater)
    changed = true
  }

  override fun setCompilerOutputPath(file: VirtualFile?) {
    if (compilerOutputPath == file) return
    updateJavaSettings { compilerOutput = file?.toVirtualFileUrl(virtualFileManager) }
  }

  override fun setCompilerOutputPath(url: String?) {
    if (compilerOutputUrl == url) return
    updateJavaSettings { compilerOutput = url?.let { virtualFileManager.getOrCreateFromUri(it) } }
  }

  override fun setCompilerOutputPathForTests(file: VirtualFile?) {
    if (compilerOutputPathForTests == file) return
    updateJavaSettings { compilerOutputForTests = file?.toVirtualFileUrl(virtualFileManager) }
  }

  override fun setCompilerOutputPathForTests(url: String?) {
    if (compilerOutputUrlForTests == url) return
    updateJavaSettings { compilerOutputForTests = url?.let { virtualFileManager.getOrCreateFromUri(it) } }
  }

  override fun inheritCompilerOutputPath(inherit: Boolean) {
    if (isCompilerOutputPathInherited == inherit) return
    updateJavaSettings { inheritedCompilerOutput = inherit }
  }

  override fun setExcludeOutput(exclude: Boolean) {
    if (isExcludeOutput == exclude) return
    updateJavaSettings { excludeOutput = exclude }
  }

  @Suppress("DuplicatedCode")
  override fun getOutputRoots(includeTests: Boolean): Array<VirtualFile> {
    val result = ArrayList<VirtualFile>()

    val outputPathForTests = if (includeTests) compilerOutputPathForTests else null
    if (outputPathForTests != null) {
      result.add(outputPathForTests)
    }

    val outputRoot = compilerOutputPath
    if (outputRoot != null && outputRoot != outputPathForTests) {
      result.add(outputRoot)
    }

    return result.toTypedArray()
  }

  @Suppress("DuplicatedCode")
  override fun getOutputRootUrls(includeTests: Boolean): Array<String> {
    val result = ArrayList<String>()

    val outputPathForTests = if (includeTests) compilerOutputUrlForTests else null
    if (outputPathForTests != null) {
      result.add(outputPathForTests)
    }

    val outputRoot = compilerOutputUrl
    if (outputRoot != null && outputRoot != outputPathForTests) {
      result.add(outputRoot)
    }

    return result.toTypedArray()
  }

  class Factory : ModuleExtensionBridgeFactory<CompilerModuleExtensionBridge> {
    override fun createExtension(module: ModuleBridge,
                                 entityStorage: VersionedEntityStorage,
                                 diff: MutableEntityStorage?): CompilerModuleExtensionBridge {
      return CompilerModuleExtensionBridge(module = module, entityStorage = entityStorage, diff = diff)
    }
  }
}
