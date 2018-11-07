// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.settings

import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.SourceFolder
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

class PackagePrefixConfigurationHandler : ConfigurationHandler {
  override fun apply(module: Module, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    val contentRoots = modelsProvider.getContentRoots(module).map { it.canonicalPath }
    val sourceFolders = getAllSourceFolders(modelsProvider)
    configuration.forEachPackagePrefix { sourcePath, packagePrefix ->
      val expectedFolders = contentRoots.mapNotNull { findVirtualFile(it, sourcePath)?.path }
      for (sourceFolder in sourceFolders) {
        val actualFolder = sourceFolder.file?.path ?: continue
        if (!expectedFolders.contains(actualFolder)) continue
        sourceFolder.packagePrefix = packagePrefix
        return@forEachPackagePrefix
      }
      LOG.warn("source directory $sourcePath not found")
    }
  }

  companion object {
    private fun findVirtualFile(rootPath: String, relativePath: String) = VfsUtil.findFileByIoFile(File(rootPath, relativePath), true)

    private fun getSourceFolders(module: Module, modelsProvider: IdeModifiableModelsProvider): List<SourceFolder> {
      val modifiableRootModel = modelsProvider.getModifiableRootModel(module)
      val contentEntries = modifiableRootModel.contentEntries
      return contentEntries.map { it.sourceFolders.toList() }.flatten()
    }

    private fun getAllSourceFolders(modelsProvider: IdeModifiableModelsProvider): List<SourceFolder> {
      return modelsProvider.modules.map { getSourceFolders(it, modelsProvider) }.flatten()
    }

    private fun ConfigurationData.forEachPackagePrefix(action: (String, String) -> Unit) {
      val packagePrefixes = find("packagePrefix")
      if (packagePrefixes !is Map<*, *>) return
      for ((sourcePath, packagePrefix) in packagePrefixes) {
        if (sourcePath !is String) {
          LOG.warn("unexpected value type: ${sourcePath?.javaClass?.name}, skipping")
          continue
        }
        if (packagePrefix !is String) {
          LOG.warn("unexpected value type: ${packagePrefix?.javaClass?.name}, skipping")
          continue
        }
        action(sourcePath, packagePrefix)
      }
    }
  }
}