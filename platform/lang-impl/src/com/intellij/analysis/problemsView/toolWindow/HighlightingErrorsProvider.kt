// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.analysis.problemsView.toolWindow

import com.intellij.analysis.problemsView.ProblemsCollector
import com.intellij.lang.annotation.HighlightSeverity.ERROR
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.problems.ProblemListener

private class HighlightingErrorsProviderProblemListener(private val project: Project) : ProblemListener {
  private val highlightingErrorsProvider by lazy(LazyThreadSafetyMode.NONE) { project.service<HighlightingErrorsProviderBase>() }

  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override fun problemsAppeared(file: VirtualFile) {
    highlightingErrorsProvider.problemsAppeared(file)
  }

  override fun problemsChanged(file: VirtualFile) {
    highlightingErrorsProvider.problemsChanged(file)
  }

  override fun problemsDisappeared(file: VirtualFile) {
    highlightingErrorsProvider.problemsDisappeared(file)
  }
}

open class HighlightingErrorsProvider(final override val project: Project) : HighlightingErrorsProviderBase {
  private val watchers = HashMap<VirtualFile, ProblemsViewHighlightingWatcher>()

  override fun problemsAppeared(file: VirtualFile) {
    if (!file.isValid || FileTypeRegistry.getInstance().isFileIgnored(file)) {
      return
    }
    if (project.isDisposed || ProjectFileIndex.getInstance(project).isExcluded(file)) {
      return
    }

    val document = ReadAction.compute(ThrowableComputable { FileDocumentManager.getInstance().getDocument(file) }) ?: return
    synchronized(watchers) {
      watchers.computeIfAbsent(file) { file ->
        ProblemsViewHighlightingWatcher(provider = this,
                                        listener = ProblemsCollector.getInstance(project),
                                        file = file,
                                        document = document,
                                        level = ERROR.myVal)
      }
    }
  }

  override fun problemsDisappeared(file: VirtualFile) {
    val watcher = synchronized(watchers) { watchers.remove(file) } ?: return
    // removes a markup model listener
    Disposer.dispose(watcher)
  }
}
