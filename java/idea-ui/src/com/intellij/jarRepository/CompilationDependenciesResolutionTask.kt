// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jarRepository

import com.intellij.ide.JavaUiBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompileTask
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.LibraryPropertiesEntity
import com.intellij.task.impl.ProjectTaskManagerImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.idea.maven.utils.library.RepositoryUtils
import kotlin.sequences.any

/**
 * Compilation dependencies should be resolved before launching the build process not to have:
 * * parallel resolution in [LibraryIdSynchronizationQueue] and the build process;
 * * private Maven repositories authentication for the build process.
 */
internal class CompilationDependenciesResolutionTask : CompileTask {
  private class ResolutionTask(library: LibraryEx, val module: Module, project: Project) {
    val promise: Promise<*> = RepositoryUtils.reloadDependencies(project, library)

    fun join() {
      ProjectTaskManagerImpl.waitForPromise(promise)
    }
  }

  override fun execute(context: CompileContext): Boolean {
    if (DisableCompilationDependenciesResolutionTask.EP_NAME.extensionList.any { it.shouldDisable(context.project) }) {
      log.info("Compilation dependencies resolution task is disabled for the project ${context.project.name}")
      return true
    }
    
    /* Optimization: don't process dependencies of affected modules recursively if there are no repository libraries configured */
    val workspaceModelSnapshot = context.project.workspaceModel.currentSnapshot
    val hasRepositoryLibraries = workspaceModelSnapshot.entities(LibraryPropertiesEntity::class.java).any {
      it.library.typeId == RepositoryLibraryType.LIBRARY_TYPE_ID
    }
    if (!hasRepositoryLibraries) {
      log.debug("Skip compilation dependencies resolution task for the project ${context.project.name} because there are no 'repository' libraries")
      return true
    }

    val queue = LibraryIdSynchronizationQueue.getInstance(context.project)
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
            queue.revokeSynchronization((library as LibraryBridge).libraryId)
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
        thisLogger().warn(e)
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

  companion object {
    private val log = thisLogger()
  }
}

/**
 * Provides a way to opt out of this pre-compile task when it does not provide any values,
 * e.g., in Bazel plugin where Bazel resolves all the external dependencies.
 *
 * The [corresponding issue](https://youtrack.jetbrains.com/issue/IDEA-367562) has been fixed, so there is no need to use this extension
 * point anymore.
 */
@ApiStatus.Obsolete
interface DisableCompilationDependenciesResolutionTask {
  companion object {
    val EP_NAME: ExtensionPointName<DisableCompilationDependenciesResolutionTask> =
      ExtensionPointName.create("com.intellij.disableCompilationDependenciesResolutionTask")
  }

  fun shouldDisable(project: Project): Boolean
}