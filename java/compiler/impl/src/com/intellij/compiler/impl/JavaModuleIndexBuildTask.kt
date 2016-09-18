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
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.psi.PsiJavaModule
import com.intellij.psi.search.FilenameIndex
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl
import org.jetbrains.jps.model.java.impl.JavaModuleIndexImpl

class JavaModuleIndexBuildTask : CompileTask {
  override fun execute(context: CompileContext): Boolean {
    val project = context.project

    val map = runReadAction {
      val map = mutableMapOf<String, String?>()
      for (module in ModuleManager.getInstance(project).modules) {
        val files = FilenameIndex.getVirtualFilesByName(project, PsiJavaModule.MODULE_INFO_FILE, module.getModuleScope(false))
        if (files.size > 1) {
          val message = IdeBundle.message("compiler.multiple.module.descriptors", module.name)
          context.addMessage(CompilerMessageCategory.ERROR, message, null, 0, 0)
          return@runReadAction null
        }
        map += module.name to ContainerUtil.getFirstItem(files)?.path
      }
      map
    } ?: return false

    try {
      val systemDir = BuildManager.getInstance().getProjectSystemDirectory(project)!!
      JavaModuleIndexImpl.store(BuildDataPathsImpl(systemDir).dataStorageRoot, map)
    }
    catch(e: Exception) {
      Logger.getInstance(JavaModuleIndexBuildTask::class.java).error(e)
      context.addMessage(CompilerMessageCategory.ERROR, "Failed to save module index file: ${e.message}", null, 0, 0)
      return false
    }

    return true
  }
}