// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events

import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.SmartList
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.containers.ContainerUtil.createLockFreeCopyOnWriteList
import it.unimi.dsi.fastutil.ints.IntSet
import org.jetbrains.annotations.ApiStatus

/**
 * Conceptually, it is a [project -> List of dirty files for this project]
 * Additionally, it keeps a List of dirty files for an 'unknown' project
 */
@ApiStatus.Internal
class DirtyFiles {
  /**
   * List[ (project, dirtyFilesForProject) ]. It should be <=1 entry per project -- which is not guaranteed by
   * this class itself, but by the fact that [addProject] method is not called >1 per project
   * (without corresponding [removeProject]).
   */
  private val dirtyFiles = createLockFreeCopyOnWriteList<Pair<Project, ProjectDirtyFiles>>()
  private val dirtyFilesWithoutProject = ProjectDirtyFiles()

  fun addFile(projects: Iterable<Project>, fileId: Int) {
    var addedToAtLeastOneProject = false
    for (project in projects) {
      val projectDirtyFiles = dirtyFiles.find { it.first == project }?.second
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
      dirtyFilesWithoutProject.addFile(fileId)
      return
    }
  }

  fun clear() {
    dirtyFilesWithoutProject.clear()
    for (p in dirtyFiles) {
      p.second.clear()
    }
  }

  fun removeProject(project: Project) {
    dirtyFiles.removeIf { it.first == project }
  }

  fun removeFile(fileId: Int) {
    dirtyFilesWithoutProject.removeFile(fileId)
    for (pair in dirtyFiles) {
      pair.second.removeFile(fileId)
    }
  }

  fun addProject(project: Project): ProjectDirtyFiles {
    //TODO RC: ensure that entry for the project is not yet exist
    val files = ProjectDirtyFiles()
    dirtyFiles.add(Pair(project, files))
    return files
  }

  fun getProjects(): List<Project> {
    return dirtyFiles.map { it.first }
  }

  fun getProjects(fileId: Int): List<Project> {
    if (dirtyFilesWithoutProject.containsFile(fileId)) return emptyList()
    return dirtyFiles.mapNotNullTo(SmartList()) { p ->
      if (p.second.containsFile(fileId)) p.first
      else null
    }
  }

  fun getProjectDirtyFiles(project: Project?): ProjectDirtyFiles? {
    if (project == null) return dirtyFilesWithoutProject
    return dirtyFiles.firstOrNull { it.first == project }?.second
  }
}

/**
 * Per-Project dirty files.
 * TODO RC: actually, it is better named just DirtyFiles, since 1) it doesn't contain a project ref, and 2) used
 *          for 'unknown project dirty files' also. While current DirtyFiles better be named PerProjectDirtyFiles
 */
@ApiStatus.Internal
class ProjectDirtyFiles {
  //TODO RC: using CBS for fileId is not very memory-efficient, because the fileId could be quite large, and CBS is forced to
  //         allocate a lot of memory for nothing
  private val filesSet: ConcurrentBitSet = ConcurrentBitSet.create()

  fun addFile(fileId: Int): Boolean = filesSet.set(fileId)
  fun containsFile(fileId: Int): Boolean = filesSet.get(fileId)
  fun removeFile(fileId: Int): Boolean = filesSet.clear(fileId)
  fun clear(): Unit = filesSet.clear()

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
