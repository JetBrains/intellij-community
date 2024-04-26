// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("JavaSourceFiles")
package com.intellij.java.workspace.files

import com.intellij.java.workspace.entities.asJavaResourceRoot
import com.intellij.java.workspace.entities.asJavaSourceRoot
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.sourceRoots
import com.intellij.workspaceModel.ide.legacyBridge.sdk.SourceRootTypeRegistry

/**
 * Return a file located under the source or resource roots of this module which will be copied to the production output with [relativePath].  
 */
fun ModuleEntity.findResourceFileByRelativePath(relativePath: String): VirtualFile? {
  return sourceRoots.firstNotNullOfOrNull { sourceRoot ->
    if (SourceRootTypeRegistry.getInstance().findTypeById(sourceRoot.rootTypeId)?.isForTests == true) {
      return@firstNotNullOfOrNull null
    }
    
    val prefix = 
      sourceRoot.asJavaSourceRoot()?.packagePrefix?.replace('.', '/')
      ?: sourceRoot.asJavaResourceRoot()?.relativeOutputPath
      ?: ""
    val prefixWithSlash = if (prefix.isEmpty()) prefix else "$prefix/"
    if (!relativePath.startsWith(prefixWithSlash)) {
      return@firstNotNullOfOrNull null
    }
    val pathFromRoot = relativePath.removePrefix(prefixWithSlash)
    sourceRoot.url.virtualFile?.findFileByRelativePath(pathFromRoot)
  }
}