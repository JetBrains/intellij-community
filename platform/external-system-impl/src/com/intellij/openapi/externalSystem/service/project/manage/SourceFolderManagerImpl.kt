// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage

import com.intellij.ide.projectView.actions.MarkRootActionBase
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.*
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jetbrains.jps.model.module.JpsModuleSourceRootType

class SourceFolderManagerImpl(private val project: Project) : SourceFolderManager, Disposable {

  private var isDisposed = false
  private val mutex = Any()
  private val postponedSourceFolderCreator = PostponedSourceFolderCreator()
  private val sourceFolders = THashMap<String, SourceFolderModel>(FileUtil.PATH_HASHING_STRATEGY)
  private val sourceFoldersByModule = THashMap<String, ModuleModel>()

  override fun addSourceFolder(module: Module, url: String, type: JpsModuleSourceRootType<*>) {
    val sourceFolder = SourceFolderModel(module, type, "")
    synchronized(mutex) {
      sourceFolders[url] = sourceFolder
      val moduleModel = sourceFoldersByModule.getOrPut(module.name) {
        ModuleModel(module).also {
          Disposer.register(module, it)
        }
      }
      moduleModel.sourceFolders.add(url)
    }
  }

  override fun setSourceFolderPackagePrefix(url: String, packagePrefix: String) {
    synchronized(mutex) {
      val sourceFolder = sourceFolders[url] ?: return
      sourceFolder.packagePrefix = packagePrefix
    }
  }

  override fun removeSourceFolders(module: Module) {
    synchronized(mutex) {
      val moduleModel = sourceFoldersByModule.remove(module.name) ?: return
      moduleModel.sourceFolders.forEach { sourceFolders.remove(it) }
    }
  }

  override fun dispose() {
    assert(!isDisposed) { "Source folder manager already disposed" }
    isDisposed = true
    val virtualFileManager = VirtualFileManager.getInstance()
    virtualFileManager.removeVirtualFileListener(postponedSourceFolderCreator)
  }

  private fun remove(url: String): SourceFolderModel? {
    return synchronized(mutex) {
      sourceFolders.remove(url)
    }
  }

  fun isDisposed() = isDisposed

  fun getSourceFolders(moduleName: String) = synchronized(mutex) {
    sourceFoldersByModule[moduleName]?.sourceFolders
  }

  private inner class PostponedSourceFolderCreator : VirtualFileListener {
    override fun fileCreated(event: VirtualFileEvent) {
      ExternalSystemApiUtil.executeProjectChangeAction(false, object : DisposeAwareProjectChange(project) {
        override fun execute() {
          val sourceFolderUrl = event.file.url
          val (module, type, packagePrefix) = remove(sourceFolderUrl) ?: return
          val moduleManager = ModuleRootManager.getInstance(module)
          val modifiableModuleModel = moduleManager.modifiableModel
          try {
            val contentEntry = MarkRootActionBase.findContentEntry(modifiableModuleModel, event.file)
            if (contentEntry != null) {
              val sourceFolder = contentEntry.addSourceFolder(sourceFolderUrl, type)
              sourceFolder.packagePrefix = packagePrefix
            }
          }
          finally {
            modifiableModuleModel.commit()
          }
        }
      })
    }
  }

  private data class SourceFolderModel(val module: Module, val type: JpsModuleSourceRootType<*>, var packagePrefix: String)

  private inner class ModuleModel(val module: Module, val sourceFolders: MutableSet<String>) : Disposable {
    constructor(module: Module) : this(module, THashSet(FileUtil.PATH_HASHING_STRATEGY))

    override fun dispose() = removeSourceFolders(module)
  }

  init {
    val virtualFileManager = VirtualFileManager.getInstance()
    virtualFileManager.addVirtualFileListener(postponedSourceFolderCreator, project)
  }
}