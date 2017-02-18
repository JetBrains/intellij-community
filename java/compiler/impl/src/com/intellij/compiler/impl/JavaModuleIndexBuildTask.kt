/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.impl

import com.intellij.compiler.server.BuildManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompileTask
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.search.FilenameIndex
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.jps.model.java.impl.JavaModuleIndexImpl

class JavaModuleIndexBuildTask : CompileTask {
  override fun execute(context: CompileContext): Boolean {
    val project = context.project

    val systemDir = BuildManager.getInstance().getProjectSystemDirectory(project)
    if (systemDir == null) {
      context.addMessage(CompilerMessageCategory.ERROR, "Internal error: no system directory for project: ${project}", null, 0, 0)
      return false
    }

    val map = runReadAction {
      val compilerManager = CompilerManager.getInstance(project)
      ModuleManager.getInstance(project).modules.asSequence()
        .map {
          val files = FilenameIndex.getVirtualFilesByName(project, PsiJavaModule.MODULE_INFO_FILE, it.getModuleScope(false))
          it.name to files.filter { !compilerManager.isExcludedFromCompilation(it) }
        }
        .toMap()
    }

    val errors = map.filter { it.value.size > 1 }.map { IdeBundle.message("compiler.multiple.module.descriptors", it.key) }
    if (errors.isNotEmpty()) {
      errors.forEach { context.addMessage(CompilerMessageCategory.ERROR, it, null, 0, 0) }
      return false
    }

    val paths = map.map { it.key to it.value.firstOrNull()?.path }.toMap()

    try {
      JavaModuleIndexImpl.store(BuildDataPathsImpl(systemDir).dataStorageRoot, paths)
    }
    catch(e: Exception) {
      Logger.getInstance(JavaModuleIndexBuildTask::class.java).error(e)
      context.addMessage(CompilerMessageCategory.ERROR, "Internal error: can't save module index: ${e.message}", null, 0, 0)
      return false
    }

    return true
  }
}