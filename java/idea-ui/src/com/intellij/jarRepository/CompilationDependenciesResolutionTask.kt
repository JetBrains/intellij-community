// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompileTask
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.task.impl.ProjectTaskManagerImpl
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.utils.library.RepositoryUtils

/**
 * Compilation dependencies should be resolved before launching build process not to have:
 * * parallel resolution in [LibrarySynchronizationQueue] and build process;
 * * private Maven repositories authentication for build process.
 */
internal class CompilationDependenciesResolutionTask : CompileTask {
  companion object {
    private val log = logger<CompilationDependenciesResolutionTask>()
  }

  private class ResolutionTask(library: LibraryEx, val module: Module, project: Project) {
    val promise: Promise<*> = RepositoryUtils.reloadDependencies(project, library)

    fun join() {
      ProjectTaskManagerImpl.waitForPromise(promise)
    }
  }

  override fun execute(context: CompileContext): Boolean {
    val queue = context.project.service<LibrarySynchronizationQueue>()
    val missingLibrariesResolutionTasks = mutableMapOf<LibraryEx, ResolutionTask>()
    val application = ApplicationManager.getApplication()
    val affectedModules = application.runReadAction<Array<Module>> {
      context.compileScope.affectedModules
    }
    for (module in affectedModules) {
      OrderEnumerator.orderEntries(module)
        .recursively()
        .exportedOnly()
        .compileOnly()
        .withoutSdk()
        .forEachLibrary { library ->
          if (library is LibraryEx &&
              !missingLibrariesResolutionTasks.containsKey(library) &&
              library.needToReload()) {
            queue.revokeSynchronization(library)
            missingLibrariesResolutionTasks[library] = ResolutionTask(library, module, context.project)
          }
          true
        }
    }
    for ((library, task) in missingLibrariesResolutionTasks) {
      try {
        context.addMessage(
          CompilerMessageCategory.INFORMATION,
          JavaUiBundle.message("precompile.library.resolution.start", library.presentableName),
          null, -1, -1, null,
          listOf(task.module.name)
        )
        task.join()
      }
      catch (e: Exception) {
        log.warn(e)
        context.addMessage(
          CompilerMessageCategory.ERROR,
          JavaUiBundle.message("precompile.library.resolution.failure", library.presentableName, e.message),
          null, -1, -1, null,
          listOf(task.module.name)
        )
        return false
      }
    }
    return true
  }
}