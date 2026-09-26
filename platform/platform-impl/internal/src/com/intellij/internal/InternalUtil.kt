// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
enum class Module(val moduleName: String, val src: String, val stripPackage: String?) {

  INTERNAL("intellij.platform.ide.internal", "platform/platform-impl/internal", stripPackage = null),
  DEVKIT_UI_DSL("intellij.devkit.uiDsl", "plugins/devkit/intellij.devkit.uiDsl", stripPackage = "com.intellij.devkit.uiDsl"),
}

@ApiStatus.Internal
fun showSources(project: Project?, module: Module, c: Class<*>) {
  var name = c.name
  module.stripPackage?.let {
    name = name.removePrefix("$it.")
  }
  name = name
    .removeSuffix("Kt")
    .replace('.', '/')
  val fileName = "src/$name.kt"

  if (!openInIdeaProject(project, module, fileName)) {
    BrowserUtil.browse(BASE_URL + module.src + "/" + fileName)
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
