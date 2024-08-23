// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.util.indexing.roots.IndexableFileScanner
import com.intellij.util.indexing.roots.kind.ContentOrigin
import com.intellij.util.indexing.roots.kind.ProjectFileOrDirOrigin

/**
 * This class doesn't push any file properties, it is used for scanning the project for `*.run.xml` files - files with run configurations.
 * This is to handle run configurations stored in arbitrary files within project content (not in .idea/runConfigurations or project.ipr file).
 */
private class RunConfigurationInArbitraryFileScanner : IndexableFileScanner {
  override fun startSession(project: Project): IndexableFileScanner.ScanSession {
    val runManager by lazy(LazyThreadSafetyMode.NONE) { RunManagerImpl.getInstanceImpl(project) }
    return IndexableFileScanner.ScanSession {
      if (it is ContentOrigin || it is ProjectFileOrDirOrigin) {
        IndexableFileScanner.IndexableFileVisitor { fileOrDir ->
          if (isFileWithRunConfigs(fileOrDir)) {
            runManager.updateRunConfigsFromArbitraryFiles(emptyList(), listOf(fileOrDir.path))
          }
        }
      }
      else {
        null
      }
    }
  }
}

internal fun loadFileWithRunConfigs(project: Project): List<String> = if (project.isDefault) listOf() else 
  FilenameIndex.getAllFilesByExt(project, "run.xml", ProjectScope.getContentScope(project)).filter { isFileWithRunConfigs(it) }.map { it.path }

private fun isFileWithRunConfigs(file: VirtualFile): Boolean {
  if (!file.isInLocalFileSystem || !file.nameSequence.endsWith(".run.xml")) return false
  var parent = file.parent
  while (parent != null) {
    if (StringUtil.equals(parent.nameSequence, ".idea")) return false
    parent = parent.parent
  }
  return true
}
