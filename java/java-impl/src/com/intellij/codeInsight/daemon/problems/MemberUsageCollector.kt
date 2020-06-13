// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.search.LowLevelSearchUtil
import com.intellij.util.Processor
import com.intellij.util.text.StringSearcher
import gnu.trove.TIntProcedure

open class MemberUsageCollector(targetName: String, project: Project,
                                private val usageExtractor: (PsiFile, Int) -> PsiElement?) : Processor<PsiFile> {

  private val maxFilesToProcess = Registry.intValue("ide.unused.symbol.calculation.maxFilesToSearchUsagesIn", 10)
  private val maxFilesSizeToProcess = Registry.intValue("ide.unused.symbol.calculation.maxFilesSizeToSearchUsagesIn", 524288)

  private var filesVisited = 0
  private var filesSize = 0L

  private val fileIndexFacade = FileIndexFacade.getInstance(project)
  private val searcher = StringSearcher(targetName, true, true, false)

  private val usages: MutableList<PsiElement> = mutableListOf()
  private var tooManyUsages = false

  val collectedUsages: MutableList<PsiElement>?
    get() = if (tooManyUsages) null else usages

  override fun process(psiFile: PsiFile): Boolean {
    if (!fileIndexFacade.isInSource(psiFile.virtualFile)) return true
    if (!isCheapEnoughToProcess(psiFile)) {
      tooManyUsages = true
      return false
    }
    val text = psiFile.viewProvider.contents
    val occurenceProcedure = TIntProcedure { index ->
      val usage = usageExtractor(psiFile, index)
      if (usage != null) usages.add(usage)
      return@TIntProcedure true
    }
    LowLevelSearchUtil.processTextOccurrences(text, 0, text.length, searcher, occurenceProcedure)
    return true
  }

  private fun isCheapEnoughToProcess(psiFile: PsiFile): Boolean {
    filesVisited++
    if (filesVisited >= maxFilesToProcess) return false
    filesSize += psiFile.textLength
    return filesSize < maxFilesSizeToProcess
  }
}