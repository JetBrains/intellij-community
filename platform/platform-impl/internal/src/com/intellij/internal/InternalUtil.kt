// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.internal

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import org.jetbrains.annotations.ApiStatus

private const val BASE_URL = "https://github.com/JetBrains/intellij-community/blob/master/platform/platform-impl/"

@ApiStatus.Internal
internal fun showSources(project: Project?, fileName: String) {
  if (!openInIdeaProject(project, fileName)) {
    BrowserUtil.browse(BASE_URL + fileName)
  }
}

private fun openInIdeaProject(project: Project?, fileName: String): Boolean {
  if (project == null) {
    return false
  }
  val moduleManager = ModuleManager.getInstance(project)
  val module = moduleManager.findModuleByName("intellij.platform.ide.impl")
  if (module == null) {
    return false
  }
  for (contentRoot in module.rootManager.contentRoots) {
    val file = contentRoot.findFileByRelativePath(fileName)
    if (file?.isValid == true) {
      OpenFileDescriptor(project, file).navigate(true)
      return true
    }
  }
  return false
}
