// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl.vcs

import com.intellij.build.BuildContentManager
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.compiler.impl.ModuleCompileScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.readAction
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption.Companion.create
import com.intellij.openapi.vcs.checkin.*
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.io.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture

private class UnloadedModulesCompilationCheckinHandler(private val project: Project,
                                               private val checkinPanel: CheckinProjectPanel) : CheckinHandler(), CommitCheck {

  override fun getBeforeCheckinConfigurationPanel(): RefreshableOnComponent? {
    return if (ModuleManager.getInstance(project).unloadedModuleDescriptions.isNotEmpty())
      create(checkinPanel.getProject(), this, false,
             JavaCompilerBundle.message("checkbox.text.compile.affected.unloaded.modules"),
             { settings.COMPILE_AFFECTED_UNLOADED_MODULES_BEFORE_COMMIT },
             { value: Boolean -> settings.COMPILE_AFFECTED_UNLOADED_MODULES_BEFORE_COMMIT = value }
      )
    else {
      null
    }
  }

  override suspend fun runCheck(commitInfo: CommitInfo): CommitProblem? {
    val files = commitInfo.committedVirtualFiles
    return withContext(Dispatchers.Default) {
      val unloadedModulesCompileScope = readAction {
        if (ModuleManager.getInstance(project).unloadedModuleDescriptions.isEmpty()) {
          return@readAction null
        }
        val fileIndex = ProjectFileIndex.getInstance(project)
        val compilerManager = CompilerManager.getInstance(project)
        val affectedModules = files
          .filter { compilerManager.isCompilableFileType(it.fileType) }
          .mapNotNullTo(LinkedHashSet()) { fileIndex.getModuleForFile(it) }
        val affectedUnloadedModules = affectedModules.flatMapTo(LinkedHashSet()) {
          DirectoryIndex.getInstance(project).getDependentUnloadedModules(it)
        }
        if (affectedUnloadedModules.isEmpty()) {
          return@readAction null
        }
        ModuleCompileScope(project, affectedModules, affectedUnloadedModules, true, false)
      }
      if (unloadedModulesCompileScope == null) {
        return@withContext null
      }

      val compiledSuccessfully = CompletableFuture<Boolean>()
      withContext(Dispatchers.EDT) {
        CompilerManager.getInstance(project).make(unloadedModulesCompileScope) { aborted, errors, _, _ ->
          if (aborted) {
            compiledSuccessfully.cancel(true)
          }
          else {
            compiledSuccessfully.complete(errors == 0)
          }
        }
      }

      if (compiledSuccessfully.await()) {
        return@withContext null
      }
      return@withContext CompilationFailedProblem()
    }
  }

  override fun getExecutionOrder(): CommitCheck.ExecutionOrder = CommitCheck.ExecutionOrder.LATE

  override fun isEnabled(): Boolean = settings.COMPILE_AFFECTED_UNLOADED_MODULES_BEFORE_COMMIT

  private val settings: CompilerWorkspaceConfiguration
    get() = CompilerWorkspaceConfiguration.getInstance(project)

  class CompilationFailedProblem : CommitProblemWithDetails {
    override val text: String
      get() = JavaCompilerBundle.message("dialog.message.compilation.of.unloaded.modules.failed")
    override val showDetailsAction: String
      get() = JavaCompilerBundle.message("button.text.checkin.handler.show.errors")
    override val showDetailsLink: String
      get() = JavaCompilerBundle.message("link.label.checkin.handler.show.errors")

    override fun showDetails(project: Project) {
      ApplicationManager.getApplication().invokeLater({
                                                        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(
                                                          BuildContentManager.TOOL_WINDOW_ID)
                                                        toolWindow?.activate(null, false)
                                                      }, ModalityState.nonModal())
    }
  }

  class Factory : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
      return UnloadedModulesCompilationCheckinHandler(panel.getProject(), panel)
    }
  }
}
