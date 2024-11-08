// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeAnyChangeAbstractAdapter
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.OverrideOnly
import java.util.concurrent.ConcurrentMap

@ApiStatus.Experimental
@OverrideOnly
abstract class UsagesCountManagerBase<ELEMENT>(project: Project, private val configuration: UsageCounterConfigurationBase<ELEMENT>): Disposable {
  private val externalUsagesCache: ConcurrentMap<VirtualFile, FileUsagesCache> = configuration.createCacheMap()

  init {
    val listener = psiTreeAnyChangeAbstractAdapter()
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, this)
  }

  private fun psiTreeAnyChangeAbstractAdapter() = object : PsiTreeAnyChangeAbstractAdapter() {
    override fun onChange(psiFile: PsiFile?) {
      val file = psiFile?.virtualFile ?: return
      val valueToKeep = externalUsagesCache[file]
      externalUsagesCache.clear()
      if (valueToKeep != null) {
        externalUsagesCache[file] = valueToKeep
      }
    }
  }

  /**
   * Counts the number of usages of a specified member.
   * Cache contains number of usages outside the current file on hard ref.
   * On any change in file, the cache is cleared everywhere but in this file.
   */
  fun countMemberUsages(file: PsiFile, member: ELEMENT): Int {
    val virtualFile = PsiUtilCore.getVirtualFile(file)
    if (virtualFile == null) {
      return configuration.countUsages(file, findSupers(member), GlobalSearchScope.allScope(file.project))
    }
    return externalUsagesCache.getOrPut(virtualFile) { FileUsagesCache(configuration) }.countMemberUsagesCached(file, member)
  }

  protected abstract fun findSupers(member: ELEMENT): List<ELEMENT>
  protected abstract fun getKey(member: ELEMENT): String?

  override fun dispose() {}

  private inner class FileUsagesCache(private val configuration: UsageCounterConfigurationBase<ELEMENT>) {
    private val externalUsagesCache: ConcurrentMap<String, Int> = configuration.createCacheMap()

    fun countMemberUsagesCached(file: PsiFile, member: ELEMENT): Int {
      val key = getKey(member)
      val superMembers = findSupers(member)
      if (key == null) {
        return configuration.countUsages(file, superMembers, GlobalSearchScope.allScope(file.project))
      }
      //external usages should be counted first to ensure heavy cases are skipped and to avoid freezes in ScopeOptimizer
      //CompilerReferenceServiceBase#getScopeWithCodeReferences method invokes kotlin resolve and can be very slow
      val localScope = GlobalSearchScope.fileScope(file)
      val externalScope = GlobalSearchScope.notScope(localScope)
      val externalUsages = externalUsagesCache.getOrPut(key) { configuration.countUsages(file, superMembers, externalScope) }
      if (externalUsages < 0) return externalUsages
      val localUsages = configuration.countUsages(file, superMembers, localScope)
      if (localUsages < 0) return localUsages
      return externalUsages + localUsages
    }
  }
}

@ApiStatus.Experimental
@OverrideOnly
interface UsageCounterConfigurationBase<ELEMENT> {
  fun <K: Any, V: Any> createCacheMap(): ConcurrentMap<K, V> {
    return CollectionFactory.createConcurrentWeakKeySoftValueMap()
  }

  fun countUsages(file: PsiFile, members: List<ELEMENT>, scope: SearchScope): Int
}