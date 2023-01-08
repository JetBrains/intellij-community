// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.impl.OrderRootsCache
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.util.indexing.BuildableRootsChangeRescanningInfo
import com.intellij.util.indexing.IndexableFilesIndex
import com.intellij.util.indexing.roots.IndexableFilesIndexImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.OrderRootsCacheBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyListener

class ProjectRootManagerBridge(project: Project) : ProjectRootManagerComponent(project) {
  init {
    if (!project.isDefault) {
      moduleDependencyIndex.addListener(ModuleDependencyListenerImpl())
    }
  }

  private val moduleDependencyIndex
    get() = ModuleDependencyIndex.getInstance(project)

  override fun getActionToRunWhenProjectJdkChanges(): Runnable {
    return Runnable {
      super.getActionToRunWhenProjectJdkChanges().run()
      if (moduleDependencyIndex.hasProjectSdkDependency()) fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addInheritedSdk())
    }
  }

  override fun getOrderRootsCache(project: Project): OrderRootsCache {
    return OrderRootsCacheBridge(project, project)
  }

  fun isFiringEvent(): Boolean = isFiringEvent

  fun setupTrackedLibrariesAndJdks() {
    moduleDependencyIndex.setupTrackedLibrariesAndJdks()
  }

  private fun fireRootsChanged(info: RootsChangeRescanningInfo) {
    if (myProject.isOpen) {
      makeRootsChange(EmptyRunnable.INSTANCE, info)
    }
  }

  inner class ModuleDependencyListenerImpl : ModuleDependencyListener {
    private var insideRootsChange = false

    override fun referencedLibraryAdded(library: Library) {
      if (shouldListen(library)) {
        fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addLibrary(library))
      }
    }

    override fun referencedLibraryChanged(library: Library) {
      if (insideRootsChange || !shouldListen(library)) return
      insideRootsChange = true
      try {
        fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addLibrary(library))
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
      //project-level libraries are stored in WorkspaceModel, and changes in their roots are handled by RootsChangeWatcher 
      return library.table?.tableLevel != LibraryTablesRegistrar.PROJECT_LEVEL
    }

    override fun referencedSdkAdded(sdk: Sdk) {
      if (IndexableFilesIndex.shouldBeUsed()) {
        IndexableFilesIndexImpl.getInstanceImpl(project).referencedSdkAdded(sdk)
      }
      fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addSdk(sdk))
    }

    override fun referencedSdkChanged(sdk: Sdk) {
      if (IndexableFilesIndex.shouldBeUsed()) {
        IndexableFilesIndexImpl.getInstanceImpl(project).referencedSdkChanged(sdk)
      }
      fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addSdk(sdk))
    }

    override fun referencedSdkRemoved(sdk: Sdk) {
      if (IndexableFilesIndex.shouldBeUsed()) {
        IndexableFilesIndexImpl.getInstanceImpl(project).referencedSdkRemoved(sdk)
      }
      fireRootsChanged(RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
    }
  }
}