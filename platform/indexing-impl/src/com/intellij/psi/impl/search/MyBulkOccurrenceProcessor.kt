// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search

import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.util.text.StringSearcher

internal class MyBulkOccurrenceProcessor(
  private val project: Project,
  private val processors: RequestProcessors
) : BulkOccurrenceProcessor {
  companion object {
    val LOG: Logger = Logger.getInstance(MyBulkOccurrenceProcessor::class.java)
  }
  override fun execute(scope: PsiElement, offsetsInScope: IntArray, searcher: StringSearcher): Boolean {
    if (offsetsInScope.isEmpty()) {
      return true
    }
    try {
      return executeInner(scope, offsetsInScope, searcher)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error(e)
      return true
    }
    catch (e: Error) {
      LOG.error(e)
      return true
    }
  }

  private fun executeInner(scope: PsiElement, offsetsInScope: IntArray, searcher: StringSearcher): Boolean {
    val progress = indicatorOrEmpty
    val manager = InjectedLanguageManager.getInstance(project)
    val topOccurrenceProcessor = InjectionAwareOccurrenceProcessor(progress, processors, manager, searcher)
    return processOffsets(scope, offsetsInScope, searcher.patternLength, progress, topOccurrenceProcessor)
  }
}