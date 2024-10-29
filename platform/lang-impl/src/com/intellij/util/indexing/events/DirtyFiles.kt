// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events

import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.SmartList
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.containers.ContainerUtil
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DirtyFiles {
  private val myDirtyFiles = ContainerUtil.createLockFreeCopyOnWriteList<Pair<Project, ProjectDirtyFiles>>()
  private val myDirtyFilesWithoutProject = ProjectDirtyFiles()

  fun addFile(projects: Collection<Project>, fileId: Int) {
    var addedToAtLeastOneProject = false
    for (project in projects) {
      val projectDirtyFiles = myDirtyFiles.find { it.first == project }?.second
      if (projectDirtyFiles != null) {
        // Technically, we can lose this file id if thread suspends here
        // then the project is closed, queue is persisted, and only then thread resumes.
        // To avoid this, we would need to add synchronized blocks when working with myDirtyFiles which will make file events processing slow
        // So I think it's ok to risk some inconsistency
        projectDirtyFiles.addFile(fileId)
        addedToAtLeastOneProject = true
      }
    }

    // 'projects' parameter may be not empty in the case when a project is not yet removed from ProjectIndexableFilesFilterHolder
    // we just need to make sure that fileId is written to at least one set
    if (!addedToAtLeastOneProject) {
      myDirtyFilesWithoutProject.addFile(fileId)
      return
    }
  }

  fun clear() {
    myDirtyFilesWithoutProject.clear()
    for (p in myDirtyFiles) {
      p.second.clear()
    }
  }

  fun removeProject(project: Project) {
    myDirtyFiles.removeIf { it.first == project }
  }

  fun removeFile(fileId: Int) {
    myDirtyFilesWithoutProject.removeFile(fileId)
    for (pair in myDirtyFiles) {
      pair.second.removeFile(fileId)
    }
  }

  fun addProject(project: Project): ProjectDirtyFiles {
    val files = ProjectDirtyFiles()
    myDirtyFiles.add(Pair(project, files))
    return files
  }

  fun getProjects(): List<Project> {
    return myDirtyFiles.map { it.first }
  }

  fun getProjects(fileId: Int): List<Project> {
    if (myDirtyFilesWithoutProject.containsFile(fileId)) return emptyList()
    return myDirtyFiles.mapNotNullTo(SmartList()) { p ->
      if (p.second.containsFile(fileId)) p.first
      else null
    }
  }

  fun getProjectDirtyFiles(project: Project?): ProjectDirtyFiles? {
    if (project == null) return myDirtyFilesWithoutProject
    return myDirtyFiles.firstOrNull { it.first == project }?.second
  }
}

@ApiStatus.Internal
class ProjectDirtyFiles {
  private val filesSet: ConcurrentBitSet = ConcurrentBitSet.create()

  fun addFile(fileId: Int) = filesSet.set(fileId)
  fun containsFile(fileId: Int) = filesSet.get(fileId)
  fun removeFile(fileId: Int) = filesSet.clear(fileId)
  fun clear() = filesSet.clear()

  fun addFiles(fileIds: Collection<Int>) {
    for (fileId in fileIds) {
      addFile(fileId)
    }
  }

  fun removeFiles(fileIds: Collection<Int>) {
    for (fileId in fileIds) {
      removeFile(fileId)
    }
  }

  fun addAllTo(set: IntSet) {
    for (fileId in 0 until filesSet.size()) {
      if (filesSet[fileId]) {
        PingProgress.interactWithEdtProgress()
        set.add(fileId)
      }
    }
  }
}
