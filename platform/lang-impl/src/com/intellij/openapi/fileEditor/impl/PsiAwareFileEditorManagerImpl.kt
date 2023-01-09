// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.util.ui.EdtInvocationManager

open class PsiAwareFileEditorManagerImpl(project: Project) : FileEditorManagerImpl(project) {
  private val problemSolver by lazy(LazyThreadSafetyMode.NONE) { WolfTheProblemSolver.getInstance(getProject()) }

  /**
   * Updates icons for open files when project roots change
   */
  init {
    @Suppress("LeakingThis")
    registerExtraEditorDataProvider(TextEditorPsiDataProvider(), null)

    // reinit syntax highlighter for Groovy. In power save mode keywords are highlighted by GroovySyntaxHighlighter insteadof
    // GrKeywordAndDeclarationHighlighter. So we need to drop caches for token types attributes in LayeredLexerEditorHighlighter
    @Suppress("LeakingThis")
    val connection = project.messageBus.connect(this)
    connection.subscribe(PowerSaveMode.TOPIC, PowerSaveMode.Listener {
      EdtInvocationManager.invokeLaterIfNeeded {
        for (editor in EditorFactory.getInstance().allEditors) {
          (editor as EditorEx).reinitSettings()
        }
      }
    })
    connection.subscribe(ProblemListener.TOPIC, MyProblemListener())
  }

  override fun isProblem(file: VirtualFile) = problemSolver.isProblemFile(file)

  @Suppress("HardCodedStringLiteral")
  override fun getFileTooltipText(file: VirtualFile, window: EditorWindow): String {
    val tooltipText: @NlsSafe StringBuilder = StringBuilder()
    if (Registry.`is`("ide.tab.tooltip.module", false)) {
      val module = ModuleUtilCore.findModuleForFile(file, project)
      if (module != null && ModuleManager.getInstance(project).modules.size > 1) {
        tooltipText.append('[')
        tooltipText.append(module.name)
        tooltipText.append("] ")
      }
    }
    tooltipText.append(super.getFileTooltipText(file, window))
    return tooltipText.toString()
  }

  private inner class MyProblemListener : ProblemListener {
    override fun problemsAppeared(file: VirtualFile) {
      updateFile(file)
    }

    override fun problemsDisappeared(file: VirtualFile) {
      updateFile(file)
    }

    override fun problemsChanged(file: VirtualFile) {
      updateFile(file)
    }

    private fun updateFile(file: VirtualFile) {
      queueUpdateFile(file)
    }
  }
}