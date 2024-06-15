// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener
import com.intellij.problems.WolfTheProblemSolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

open class PsiAwareFileEditorManagerImpl(project: Project, coroutineScope: CoroutineScope) : FileEditorManagerImpl(project, coroutineScope) {
  private val problemSolver by lazy(LazyThreadSafetyMode.NONE) { WolfTheProblemSolver.getInstance(getProject()) }

  /**
   * Updates icons for open files when project roots change
   */
  init {
    // Reinit syntax highlighter for Groovy.
    // In power save mode, keywords are highlighted by GroovySyntaxHighlighter instead of GrKeywordAndDeclarationHighlighter.
    // So we need to drop caches for token types of attributes in LayeredLexerEditorHighlighter
    val connection = project.messageBus.connect(coroutineScope)
    connection.subscribe(PowerSaveMode.TOPIC, PowerSaveMode.Listener {
      val editors = EditorFactory.getInstance().getEditorList()
      coroutineScope.launch(Dispatchers.EDT) {
        for (editor in editors) {
          if (!editor.isDisposed) {
            (editor as EditorEx).reinitSettings()
          }
        }
      }
    })
    connection.subscribe(ProblemListener.TOPIC, MyProblemListener())
  }

  override fun isProblem(file: VirtualFile): Boolean = problemSolver.isProblemFile(file)

  override fun getFileTooltipText(file: VirtualFile, composite: EditorComposite?): String {
    val tooltipText: @NlsSafe StringBuilder = StringBuilder()
    if (Registry.`is`("ide.tab.tooltip.module", false)) {
      val module = ModuleUtilCore.findModuleForFile(file, project)
      if (module != null && ModuleManager.getInstance(project).modules.size > 1) {
        tooltipText.append('[')
        tooltipText.append(module.name)
        tooltipText.append("] ")
      }
    }
    tooltipText.append(super.getFileTooltipText(file, composite))
    return tooltipText.toString()
  }

  private inner class MyProblemListener : ProblemListener {
    override fun problemsAppeared(file: VirtualFile) {
      queueUpdateFile(file)
    }

    override fun problemsDisappeared(file: VirtualFile) {
      queueUpdateFile(file)
    }

    override fun problemsChanged(file: VirtualFile) {
      queueUpdateFile(file)
    }
  }
}