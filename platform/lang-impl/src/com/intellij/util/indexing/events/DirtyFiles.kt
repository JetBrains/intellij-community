// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.events

import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.util.SmartList
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.containers.ContainerUtil
import it.unimi.dsi.fastutil.ints.IntSet

class DirtyFiles {
  private val myDirtyFiles = ContainerUtil.createLockFreeCopyOnWriteList<Pair<Project, ProjectDirtyFiles>>()
  private val myDirtyFilesWithoutProject = ProjectDirtyFiles()

  fun addFile(projects: Collection<Project>, fileId: Int) {
    if (projects.isEmpty()) {
      myDirtyFilesWithoutProject.addFile(fileId)
      return
    }
    for (project in projects) {
      myDirtyFiles.find { it.first == project }?.second?.addFile(fileId)
      ?: assert(false) {
        "Project (name: ${project.getName()} hash: ${project.getLocationHash()}) was not found in myDirtyFiles. " +
        "Projects in myDirtyFiles: ${myDirtyFiles.joinToString { p -> "(name: " + p.first.name + " hash: " + p.first.locationHash + ") " }}"
      }
    }
  }

  fun clear() {
    myDirtyFilesWithoutProject.clear()
    for (p in myDirtyFiles) {
      p.second.clear()
    }
  }

  fun removeProject(project: Project) {
    while (true) {
      // todo: this is a temp fix for the problem in LightPlatformTestCase where
      //  project is registered in FileBasedIndexImpl two times.
      //  First time via LightProjectDescriptor.setUpProject
      //  Second time via ((TestProjectManager)ProjectManagerEx.getInstanceEx()).openProject(project)
      if (!myDirtyFiles.removeIf { it.first == project }) break;
    }
  }

  fun removeFile(fileId: Int) {
    myDirtyFilesWithoutProject.removeFile(fileId)
    for (pair in myDirtyFiles) {
      pair.second.removeFile(fileId)
    }
  }

  fun addProject(project: Project) {
    myDirtyFiles.add(Pair(project, ProjectDirtyFiles()))
  }

  fun getProjects(): List<Project> {
    return myDirtyFiles.map { it.first }
  }

  fun getProjects(fileId: Int): List<Project> {
    if (myDirtyFilesWithoutProject.getFile(fileId)) return emptyList()
    return myDirtyFiles.mapNotNullTo(SmartList()) { p ->
      if (p.second.getFile(fileId)) p.first
      else null
    }
  }

  fun getDirtyFilesWithoutProject(): ProjectDirtyFiles = myDirtyFilesWithoutProject

  fun getProjectDirtyFiles(project: Project?): ProjectDirtyFiles? {
    if (project == null) return myDirtyFilesWithoutProject
    return myDirtyFiles.first { it.first == project }.second
  }
}

class ProjectDirtyFiles {
  private val filesSet: ConcurrentBitSet = ConcurrentBitSet.create()

  fun addFile(fileId: Int) = filesSet.set(fileId)
  fun getFile(fileId: Int) = filesSet.get(fileId)
  fun removeFile(fileId: Int) = filesSet.clear(fileId)
  fun clear() = filesSet.clear()

  fun addAllTo(set: IntSet) {
    for (fileId in 0 until filesSet.size()) {
      if (filesSet[fileId]) {
        PingProgress.interactWithEdtProgress()
        set.add(fileId)
      }
    }
  }
}
