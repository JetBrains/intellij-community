// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.impl.vcs

import com.intellij.CommonBundle
import com.intellij.compiler.CompilerWorkspaceConfiguration
import com.intellij.compiler.impl.ModuleCompileScope
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.ui.BooleanCommitOption.Companion.create
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.ui.RefreshableOnComponent
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.PairConsumer
import com.intellij.xml.util.XmlStringUtil
import java.util.concurrent.atomic.AtomicReference

class UnloadedModulesCompilationCheckinHandler(private val project: Project,
                                               private val checkinPanel: CheckinProjectPanel) : CheckinHandler() {
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

  override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>): ReturnResult {
    if (!settings.COMPILE_AFFECTED_UNLOADED_MODULES_BEFORE_COMMIT ||
        ModuleManager.getInstance(project).unloadedModuleDescriptions.isEmpty()) {
      return ReturnResult.COMMIT
    }
    val fileIndex = ProjectFileIndex.getInstance(project)
    val compilerManager = CompilerManager.getInstance(project)
    val affectedModules = checkinPanel.getVirtualFiles()
      .filter { compilerManager.isCompilableFileType(it.fileType) }
      .mapNotNullTo(LinkedHashSet()) { fileIndex.getModuleForFile(it) }
    val affectedUnloadedModules = affectedModules.flatMapTo(LinkedHashSet()) { 
      DirectoryIndex.getInstance(project).getDependentUnloadedModules(it) 
    }
    if (affectedUnloadedModules.isEmpty()) {
      return ReturnResult.COMMIT
    }
    val result = AtomicReference<BuildResult>()
    compilerManager.makeWithModalProgress(ModuleCompileScope(project, affectedModules, affectedUnloadedModules, true, false)) { aborted, errors, _, _ ->
      result.set(when {
        aborted -> BuildResult.CANCELED
        errors > 0 -> BuildResult.FAILED
        else -> BuildResult.SUCCESSFUL
      })
    }
    if (result.get() == BuildResult.SUCCESSFUL) {
      return ReturnResult.COMMIT
    }
    val message = JavaCompilerBundle.message("dialog.message.compilation.of.unloaded.modules.failed")
    val answer = Messages.showYesNoCancelDialog(project, XmlStringUtil.wrapInHtml(message), JavaCompilerBundle
      .message("dialog.title.compilation.failed"),
                                                JavaCompilerBundle.message("button.text.checkin.handler.commit"),
                                                JavaCompilerBundle.message("button.text.checkin.handler.show.errors"),
                                                CommonBundle.getCancelButtonText(), null)
    return when (answer) {
      Messages.CANCEL -> ReturnResult.CANCEL
      Messages.YES -> ReturnResult.COMMIT
      else -> {
        ApplicationManager.getApplication().invokeLater({
          val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.MESSAGES_WINDOW)
          toolWindow?.activate(null, false)
        }, ModalityState.nonModal())
        ReturnResult.CLOSE_WINDOW
      }
    }
  }

  private val settings: CompilerWorkspaceConfiguration
    get() = CompilerWorkspaceConfiguration.getInstance(project)

  private enum class BuildResult {
    SUCCESSFUL,
    FAILED,
    CANCELED
  }

  class Factory : CheckinHandlerFactory() {
    override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
      return UnloadedModulesCompilationCheckinHandler(panel.getProject(), panel)
    }
  }
}
