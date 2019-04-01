// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

class IndexLibraryManager(val project: Project) : ProjectComponent {
  val nameSet = HashSet<String>()
  val rootsToIndex = HashSet<VirtualFile>()

  fun updateNames(names: Collection<String>) {
    var flag = false
    for (name in names) {
      if (!nameSet.contains(name)) {

        for (entry in ProjectRootManager.getInstance(project).orderEntries().classesRoots) {
          val canonicalFile = entry.canonicalFile
          if (canonicalFile != null) {
            val libFile = canonicalFile.findChild(name)
            if (libFile != null) {
              rootsToIndex.add(libFile)
              flag = true
            }
          }
        }
      }
    }
    nameSet.clear()
    nameSet.addAll(names)

    if (flag) {
      ApplicationManager.getApplication().invokeLater {
        ProjectRootManagerEx.getInstanceEx(project).makeRootsChange({}, false, true)
      }
    }
  }

  fun isUnusedLibrary(file: VirtualFile): Boolean {
    if (isInContentOfAnyProject(file)) {
      return false
    }

    for (module in ModuleManager.getInstance(project).modules) {
      val orderEntries = ModuleRootManager.getInstance(module).orderEntries
      for (orderEntry in orderEntries) {
        if (orderEntry is LibraryOrSdkOrderEntry) {
          if (orderEntry.isValid()) {
            for (root in orderEntry.getRootFiles(OrderRootType.SOURCES) +
                         orderEntry.getRootFiles(OrderRootType.CLASSES)) {
              if (root == file) {
                return false
              }
            }
          }
        }
      }
    }

    for (root in rootsToIndex) {
      if (VfsUtil.isAncestor(root, file, false)) {
        return false
      }
    }

    return true
  }

  //TODO: duplicate
  private fun isInContentOfAnyProject(file: VirtualFile): Boolean {
    for (project in ProjectManager.getInstance().openProjects) {
      if (ProjectFileIndex.getInstance(project).isInContent(file)) {
        return true
      }
    }
    return false
  }

  companion object {
    fun getInstance(project: Project): IndexLibraryManager = project.getComponent(IndexLibraryManager::class.java)!!
  }
}