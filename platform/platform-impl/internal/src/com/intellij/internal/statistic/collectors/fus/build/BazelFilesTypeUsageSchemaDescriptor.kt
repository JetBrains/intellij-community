// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.build

import com.intellij.internal.statistic.collectors.fus.BazelProjectDetector
import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageSchemaDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

private fun updateBazelProjectDetectorState(project: Project, hasBazelFile: Boolean) {
  if (hasBazelFile && !BazelProjectDetector.hasBazelFiles(project)) {
    BazelProjectDetector.setHasBazelFiles(project, true)
  }
}

private const val DOT_BAZEL_FILE = ".bazel"
private const val DOT_BZL_FILE = ".bzl"
private const val BAZEL_BUILD_FILE = "BUILD"
private const val BAZEL_MODULE_FILE = "MODULE.bazel"
private const val BAZEL_WORKSPACE_FILE = "WORKSPACE"

private fun isBazelBuildFile(file: VirtualFile): Boolean {
  return FileUtil.getNameWithoutExtension(file.name) == BAZEL_BUILD_FILE
}

private fun isBazelModuleFile(file: VirtualFile): Boolean {
  return file.name == BAZEL_MODULE_FILE
}

private fun isBazelWorkspaceFile(file: VirtualFile): Boolean {
  return FileUtil.getNameWithoutExtension(file.name) == BAZEL_WORKSPACE_FILE
}

private fun isBazelSuffixed(file: VirtualFile, lowercaseName: String): Boolean {
  return (lowercaseName.endsWith(DOT_BAZEL_FILE) || lowercaseName.endsWith(DOT_BZL_FILE))
         && !isBazelBuildFile(file)
         && !isBazelWorkspaceFile(file)
         && !isBazelModuleFile(file)
}

@ApiStatus.Internal
class DotBazelFileTypeUsageSchemaDescriptor : FileTypeUsageSchemaDescriptor {
  override fun describes(project: Project, file: VirtualFile): Boolean {
    return isBazelSuffixed(file, file.name.lowercase()).also {
      updateBazelProjectDetectorState(project, it)
    }
  }
}

@ApiStatus.Internal
class BazelBuildFileTypeUsageSchemaDescriptor : FileTypeUsageSchemaDescriptor {
  override fun describes(project: Project, file: VirtualFile): Boolean {
    return isBazelBuildFile(file).also {
      updateBazelProjectDetectorState(project, it)
    }
  }
}

@ApiStatus.Internal
class BazelWorkspaceFileTypeUsageSchemaDescriptor : FileTypeUsageSchemaDescriptor {
  override fun describes(project: Project, file: VirtualFile): Boolean {
    return isBazelWorkspaceFile(file).also {
      updateBazelProjectDetectorState(project, it)
    }
  }
}

@ApiStatus.Internal
class BazelModuleFileTypeUsageSchemaDescriptor : FileTypeUsageSchemaDescriptor {
  override fun describes(project: Project, file: VirtualFile): Boolean {
    return isBazelModuleFile(file).also {
      updateBazelProjectDetectorState(project, it)
    }
  }
}
