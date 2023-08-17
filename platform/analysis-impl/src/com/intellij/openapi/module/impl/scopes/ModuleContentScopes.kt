// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("EqualsOrHashCode")
package com.intellij.openapi.module.impl.scopes

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.IndexingBundle
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

internal class ModuleContentScope(private val module: Module) : GlobalSearchScope(module.project) {
  private val projectFileIndex = ProjectFileIndex.getInstance(module.project)
  
  override fun contains(file: VirtualFile): Boolean = projectFileIndex.getModuleForFile(file) == module
  override fun isSearchInModuleContent(aModule: Module): Boolean = aModule == module
  override fun isSearchInLibraries(): Boolean = false
  override fun equals(other: Any?): Boolean = (other as? ModuleContentScope)?.module == module
  override fun calcHashCode(): Int = module.hashCode()
  override fun toString(): String = "ModuleContentScope{module=${module.name}}"
  override fun getDisplayName(): String = IndexingBundle.message("search.scope.module", module.name)
}

internal class ModuleWithDependenciesContentScope(private val rootModule: Module) : GlobalSearchScope(rootModule.project) {
  private val moduleToOrder : Object2IntMap<Module> = Object2IntOpenHashMap()
  private val projectFileIndex = ProjectFileIndex.getInstance(rootModule.project)

  init {
    var i = 1
    ModuleRootManager.getInstance(rootModule).orderEntries().recursively().withoutLibraries().withoutSdk().productionOnly().forEachModule {
      moduleToOrder.put(it, i++)
      true
    }
  }
  
  override fun contains(file: VirtualFile): Boolean {
    val module = projectFileIndex.getModuleForFile(file)
    return module != null && module in moduleToOrder
  }

  override fun isSearchInModuleContent(aModule: Module): Boolean = aModule in moduleToOrder
  override fun isSearchInLibraries(): Boolean = false

  override fun compare(file1: VirtualFile, file2: VirtualFile): Int {
    val order1 = projectFileIndex.getModuleForFile(file1)?.let { moduleToOrder.getInt(it) } ?: 0
    val order2 = projectFileIndex.getModuleForFile(file2)?.let { moduleToOrder.getInt(it) } ?: 0
    //see javadoc of GlobalSearchScope::compareTo: a positive result indicates that file1 is located in the classpath before file2 
    return order2.compareTo(order1)
  }

  override fun equals(other: Any?): Boolean = (other as? ModuleWithDependenciesContentScope)?.rootModule == rootModule
  override fun calcHashCode(): Int = rootModule.hashCode()
  override fun toString(): String = "ModuleWithDependenciesContentScope{rootModule=${rootModule.name}}" 
  override fun getDisplayName(): String = IndexingBundle.message("search.scope.module", rootModule.name)
}
