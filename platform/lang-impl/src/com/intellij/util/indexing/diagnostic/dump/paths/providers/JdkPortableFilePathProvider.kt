// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.diagnostic.dump.paths.providers

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.util.indexing.diagnostic.dump.paths.PortableFilePath
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.customName

object JdkPortableFilePathProvider : PortableFilePathProvider {
  override fun getRelativePortableFilePath(project: Project, virtualFile: VirtualFile): PortableFilePath.RelativePath? {
    val sdkEntity = ProjectFileIndex.getInstance(project).findContainingSdks(virtualFile).firstOrNull()
    if (sdkEntity == null) { return null }
    val jdkName = sdkEntity.name

    for ((rootIndex, sdkRoot) in sdkEntity.roots.withIndex()) {
      val inClassFiles = sdkRoot.type.name == OrderRootType.CLASSES.customName
      val rootFile = sdkRoot.url.virtualFile ?: continue
      val relativePath = VfsUtilCore.getRelativePath(virtualFile, rootFile)
      if (relativePath == null) continue
      return PortableFilePath.RelativePath(PortableFilePath.JdkRoot(jdkName, rootIndex, inClassFiles), relativePath)
    }
    return null
  }
}
