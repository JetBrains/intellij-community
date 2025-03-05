// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.internal

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import org.jetbrains.annotations.ApiStatus

private const val BASE_URL = "https://github.com/JetBrains/intellij-community/blob/master/"

@ApiStatus.Internal
enum class Module(val moduleName: String, val moduleSrc: String) {

  PLATFORM_API("intellij.platform.ide", "platform/platform-api"),
  PLATFORM_IMPL("intellij.platform.ide.impl", "platform/platform-impl"),
  INTERNAL("intellij.platform.ide.internal", "platform/platform-impl/internal"),
}

@ApiStatus.Internal
internal fun showSources(project: Project?, module: Module, fileName: String) {
  if (!openInIdeaProject(project, module, fileName)) {
    BrowserUtil.browse(BASE_URL + module.moduleSrc + "/" + fileName)
  }
}

private fun openInIdeaProject(project: Project?, module: Module, fileName: String): Boolean {
  if (project == null) {
    return false
  }
  val moduleManager = ModuleManager.getInstance(project)
  val module = moduleManager.findModuleByName(module.moduleName)
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
