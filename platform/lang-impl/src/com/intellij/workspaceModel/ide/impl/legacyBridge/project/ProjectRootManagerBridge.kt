// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.impl.OrderRootsCache
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.util.indexing.BuildableRootsChangeRescanningInfo
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.OrderRootsCacheBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyListener
import kotlinx.coroutines.CoroutineScope

class ProjectRootManagerBridge(project: Project, coroutineScope: CoroutineScope) : ProjectRootManagerComponent(project, coroutineScope) {
  init {
    if (!project.isDefault) {
      moduleDependencyIndex.addListener(ModuleDependencyListenerImpl())
    }
  }

  private val moduleDependencyIndex
    get() = ModuleDependencyIndex.getInstance(project)

  override val actionToRunWhenProjectJdkChanges: Runnable
    get() {
      return Runnable {
        super.actionToRunWhenProjectJdkChanges.run()
        if (moduleDependencyIndex.hasProjectSdkDependency()) {
          val info = BuildableRootsChangeRescanningInfo.newInstance().addInheritedSdk().buildInfo()
          fireRootsChanged(info)
        }
      }
    }

  override fun getOrderRootsCache(project: Project): OrderRootsCache {
    return OrderRootsCacheBridge(project, project)
  }

  internal fun setupTrackedLibrariesAndJdks() {
    moduleDependencyIndex.setupTrackedLibrariesAndJdks()
  }

  private fun fireRootsChanged(info: RootsChangeRescanningInfo) {
    if (project.isOpen) {
      makeRootsChange(EmptyRunnable.INSTANCE, info)
    }
  }

  inner class ModuleDependencyListenerImpl : ModuleDependencyListener {
    private var insideRootsChange = false

    override fun referencedLibraryAdded(library: Library) {
      if (Registry.`is`("use.workspace.file.index.for.partial.scanning")) return
      if (shouldListen(library)) {
        fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addLibrary(library).buildInfo())
      }
    }

    override fun referencedLibraryChanged(library: Library) {
      if (Registry.`is`("use.workspace.file.index.for.partial.scanning")) return
      if (insideRootsChange || !shouldListen(library)) return
      insideRootsChange = true
      try {
        fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addLibrary(library).buildInfo())
      }
      finally {
        insideRootsChange = false
      }
    }

    override fun referencedLibraryRemoved(library: Library) {
      if (shouldListen(library)) {
        fireRootsChanged(RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
      }
    }

    private fun shouldListen(library: Library): Boolean {
      //project, global and custom level libraries are stored in WorkspaceModel, and changes in their roots are handled by RootsChangeWatcher
      val libraryTableId = (library as? LibraryBridge)?.libraryId?.tableId ?: return true
      return libraryTableId !is LibraryTableId.ProjectLibraryTableId && libraryTableId !is LibraryTableId.GlobalLibraryTableId
    }

    override fun referencedSdkAdded(sdk: Sdk) {
      fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addSdk(sdk).buildInfo())
    }

    override fun referencedSdkChanged(sdk: Sdk) {
      fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addSdk(sdk).buildInfo())
    }

    override fun referencedSdkRemoved(sdk: Sdk) {
      fireRootsChanged(RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
    }
  }
}