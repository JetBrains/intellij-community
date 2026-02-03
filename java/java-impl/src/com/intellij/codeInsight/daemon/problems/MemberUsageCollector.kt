// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.problems

import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.impl.search.JavaFilesSearchScope
import com.intellij.psi.impl.search.LowLevelSearchUtil
import com.intellij.psi.impl.source.PsiJavaFileImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.util.text.StringSearcher
import java.util.function.IntPredicate


public open class MemberUsageCollector {
  public companion object {
    /**
     * Search files with memberName occurence in code.
     * 
     * Note that it is not necessarily the same member that we were searching for,
     * it might be just reference to an element with a same name (see ProblemCollector#extractUsage for details).
     * Also note that there's a limit on how many files we analyze for references (and their total size) 
     */
    public fun collect(
      memberName: String,
      containingFile: PsiFile,
      scope: GlobalSearchScope,
      usageExtractor: (PsiFile, Int) -> PsiElement?
    ): List<PsiElement>? {
      val project = containingFile.project
      val cacheManager = CacheManager.getInstance(project)
      val javaScope = scope.intersectWith(JavaFilesSearchScope(project))
      val relatedFiles = cacheManager.getVirtualFilesWithWord(memberName, UsageSearchContext.IN_CODE, javaScope, true)
      val javaFiles = getJavaFiles(relatedFiles, containingFile) ?: return null
      val usages: MutableList<PsiElement> = mutableListOf()
      val searcher = StringSearcher(memberName, true, true, false)
      for (javaFile in javaFiles) {
        val text = javaFile.viewProvider.contents
        val occurenceProcedure = IntPredicate { index ->
          val usage = usageExtractor(javaFile, index)
          if (usage != null) usages.add(usage)
          true
        }
        LowLevelSearchUtil.processTexts(text, 0, text.length, searcher, occurenceProcedure)
      }
      return usages
    }

    private fun getJavaFiles(relatedFiles: Array<VirtualFile>, targetFile: PsiFile): List<PsiJavaFile>? {
      val project = targetFile.project
      val fileIndexFacade = FileIndexFacade.getInstance(project)
      val virtualFile = targetFile.virtualFile
      var nFiles = 0
      var filesSize = 0L
      val maxFilesToProcess = Registry.intValue("ide.unused.symbol.calculation.maxFilesToSearchUsagesIn", 10)
      val maxFilesSizeToProcess = Registry.intValue("ide.unused.symbol.calculation.maxFilesSizeToSearchUsagesIn", 524288)
      val filtered = mutableSetOf<VirtualFile>()
      for (relatedFile in relatedFiles) {
        if (virtualFile == relatedFile || !fileIndexFacade.isInSource(relatedFile)) continue
        nFiles += 1
        if (nFiles >= maxFilesToProcess) return null
        filesSize += relatedFile.length
        if (filesSize >= maxFilesSizeToProcess) return null
        filtered.add(relatedFile)
      }
      val psiManager = PsiManager.getInstance(project)
      return filtered.mapNotNull { vFile -> psiManager.findFile(vFile) as? PsiJavaFileImpl }
    }
  }
}