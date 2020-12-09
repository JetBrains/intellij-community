// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.ProjectFileScanner
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil

/**
 * This class doesn't push any file properties, it is used for scanning the project for `*.run.xml` files - files with run configurations.
 * This is to handle run configurations stored in arbitrary files within project content (not in .idea/runConfigurations or project.ipr file).
 */
class RunConfigurationInArbitraryFileScanner : ProjectFileScanner {
  companion object {
    fun isFileWithRunConfigs(file: VirtualFile): Boolean {
      if (!file.isInLocalFileSystem || !StringUtil.endsWith(file.nameSequence, ".run.xml")) return false
      var parent = file.parent
      while (parent != null) {
        if (StringUtil.equals(parent.nameSequence, ".idea")) return false
        parent = parent.parent
      }
      return true
    }

    fun isFileWithRunConfigs(path: String) = !path.contains("/.idea/") && PathUtil.getFileName(path).endsWith(".run.xml")
  }

  override fun startSession(project: Project, singleRoot: VirtualFile?): ProjectFileScanner.ScanSession {
    val runManagerImpl = RunManagerImpl.getInstanceImpl(project)
    return ProjectFileScanner.ScanSession {
      if (isFileWithRunConfigs(it)) {
        runManagerImpl.updateRunConfigsFromArbitraryFiles(emptyList(), listOf(it.path))
      }
    }
  }
}