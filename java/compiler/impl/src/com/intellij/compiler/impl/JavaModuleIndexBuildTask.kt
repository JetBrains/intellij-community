/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.search.FilenameIndex
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.jps.model.java.impl.JavaModuleIndexImpl

class JavaModuleIndexBuildTask : CompileTask {
  override fun execute(context: CompileContext): Boolean {
    val project = context.project

    val systemDir = BuildManager.getInstance().getProjectSystemDirectory(project)
    if (systemDir == null) {
      context.error("Internal error: no system directory for project: ${project}")
      return false
    }

    val badModules = mutableListOf<String>()
    val paths = mutableMapOf<String, String?>()

    val compilerManager = CompilerManager.getInstance(project)
    runReadAction {
      ModuleManager.getInstance(project).modules.forEach { module ->
        val index = ModuleRootManager.getInstance(module).fileIndex
        val sourceFiles = mutableListOf<String>()
        val testFiles = mutableListOf<String>()
        FilenameIndex.getVirtualFilesByName(project, PsiJavaModule.MODULE_INFO_FILE, module.moduleScope).forEach { file ->
          if (!compilerManager.isExcludedFromCompilation(file)) {
            (if (index.isInTestSourceContent(file)) testFiles else sourceFiles) += file.path
          }
        }
        if (sourceFiles.size + testFiles.size > 1) {
          badModules += module.name
        }
        else {
          paths += module.name + JavaModuleIndexImpl.SOURCE_SUFFIX to sourceFiles.firstOrNull()
          paths += module.name + JavaModuleIndexImpl.TEST_SUFFIX to testFiles.firstOrNull()
        }
      }
    }

    if (badModules.isNotEmpty()) {
      badModules.forEach { context.error(IdeBundle.message("compiler.multiple.module.descriptors", it)) }
      return false
    }

    try {
      JavaModuleIndexImpl.store(BuildDataPathsImpl(systemDir).dataStorageRoot, paths)
    }
    catch(e: Exception) {
      Logger.getInstance(JavaModuleIndexBuildTask::class.java).error(e)
      context.error("Internal error: can't save module index: ${e.message}")
      return false
    }

    return true
  }

  private fun CompileContext.error(message: String) = addMessage(CompilerMessageCategory.ERROR, message, null, 0, 0)
}