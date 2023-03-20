package com.intellij.openapi.project

import com.intellij.openapi.vfs.VirtualFile

interface BaseProjectDirectoriesListener {
  fun changed(project: Project, diff: BaseProjectDirectoriesDiff)
}

data class BaseProjectDirectoriesDiff(val removed: Set<VirtualFile>, val added: Set<VirtualFile>)