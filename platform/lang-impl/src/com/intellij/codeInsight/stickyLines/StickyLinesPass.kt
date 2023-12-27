// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.stickyLines

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.stickyLines.StickyLineInfo
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesCollector
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import java.util.concurrent.atomic.AtomicBoolean

private val CACHED_LINES_KEY: Key<CachedValue<Runnable>> = Key.create("editor.sticky.lines.cached")

internal class StickyLinesPass(project: Project, document: Document, private val vFile: VirtualFile, private val psiFile: PsiFile)
  : TextEditorHighlightingPass(project, document), DumbAware {

  private var updater: Runnable? = null

  override fun doCollectInformation(progress: ProgressIndicator) {
    updater = getUpdater()
  }

  override fun doApplyInformationToEditor() {
    updater?.run()
    updater = null
  }

  private fun getUpdater(): Runnable {
    val cachedUpdater = psiFile.getUserData(CACHED_LINES_KEY)?.upToDateOrNull
    if (cachedUpdater != null) {
      return cachedUpdater.get()
    }
    return CachedValuesManager.getManager(myProject).getCachedValue(
      psiFile,
      CACHED_LINES_KEY,
      getValueProvider(myProject, myDocument, vFile, dependency = psiFile),
      false
    )
  }

  companion object {
    private fun getValueProvider(
      project: Project,
      document: Document,
      vFile: VirtualFile,
      dependency: Any
    ): CachedValueProvider<Runnable> {
      return CachedValueProvider {
        val collector = StickyLinesCollector(project, document)
        val infos: MutableSet<StickyLineInfo> = collector.collectLines(vFile)
        val alreadyExecuted = AtomicBoolean()
        val runnable = Runnable {
          if (alreadyExecuted.compareAndSet(false, true)) {
            collector.applyLines(infos)
          }
        }
        CachedValueProvider.Result.create(runnable, dependency)
      }
    }
  }
}
