// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.project

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.RootsChangeRescanningInfo
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.impl.OrderRootsCache
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.util.indexing.BuildableRootsChangeRescanningInfo
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.OrderRootsCacheBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyListener
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
import java.util.function.Supplier

class ProjectRootManagerBridge(project: Project) : ProjectRootManagerComponent(project) {
  companion object {
    @JvmStatic
    private val LOG = logger<ProjectRootManagerBridge>()
  }

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

  inner class ModuleDependencyListenerImpl : ModuleDependencyListener, RootProvider.RootSetChangedListener {
    private var insideRootsChange = false

    override fun addedDependencyOn(library: Library) {
      if (shouldListen(library)) {
        (library as? RootProvider)?.addRootSetChangedListener(this)
      }
    }

    override fun removedDependencyOn(library: Library) {
      if (shouldListen(library)) {
        (library as? RootProvider)?.removeRootSetChangedListener(this)
      }
    }

    override fun rootSetChanged(wrapper: RootProvider) {
      if (wrapper is Library) {
        if (insideRootsChange) return
        insideRootsChange = true
        try {
          fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addLibrary(wrapper as Library))
        }
        finally {
          insideRootsChange = false
        }
      }
      else {
        LOG.assertTrue(wrapper is Supplier<*>, "Unexpected root provider $wrapper does not implement Supplier<Sdk>")
        fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addSdk((wrapper as Supplier<Sdk>).get()))
      }

    }

    override fun referencedLibraryAdded(library: Library) {
      if (shouldListen(library)) {
        fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addLibrary(library))
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

    override fun addedDependencyOn(sdk: Sdk) {
      sdk.rootProvider.addRootSetChangedListener(this)
    }

    override fun removedDependencyOn(sdk: Sdk) {
      sdk.rootProvider.removeRootSetChangedListener(this)
    }

    override fun referencedSdkAdded(sdk: Sdk) {
      fireRootsChanged(BuildableRootsChangeRescanningInfo.newInstance().addSdk(sdk))
    }

    override fun referencedSdkRemoved(sdk: Sdk) {
      fireRootsChanged(RootsChangeRescanningInfo.NO_RESCAN_NEEDED)
    }
  }
}