// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.FilePropertyPusher
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil

/**
 * This class doesn't push any file properties, it is used for scanning the project for `*.run.xml` files - files with run configurations.
 * This is to handle run configurations stored in arbitrary files within project content (not in .idea/runConfigurations or project.ipr file).
 */
class RunConfigurationInArbitraryFileScanner : FilePropertyPusher<Nothing> {

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

  override fun acceptsFile(file: VirtualFile, project: Project): Boolean {
    if (isFileWithRunConfigs(file)) {
      RunManagerImpl.getInstanceImpl(project).updateRunConfigsFromArbitraryFiles(emptyList(), listOf(file.path))
    }
    return false
  }

  override fun acceptsDirectory(file: VirtualFile, project: Project): Boolean = false
  private val key = Key.create<Nothing>("RCInArbitraryFileScanner")
  override fun getFileDataKey(): Key<Nothing> = key
  override fun pushDirectoriesOnly(): Boolean = false
  override fun getDefaultValue(): Nothing = throw NotImplementedError("not expected to be called")
  override fun getImmediateValue(project: Project, file: VirtualFile?): Nothing? = null
  override fun getImmediateValue(module: Module): Nothing? = null
  override fun persistAttribute(project: Project, fileOrDir: VirtualFile, value: Nothing) {}
}