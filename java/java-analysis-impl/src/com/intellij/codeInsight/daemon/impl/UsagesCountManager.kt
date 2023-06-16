// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.serviceContainer.NonInjectable
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.ConcurrentMap


@Service(Service.Level.PROJECT)
class UsagesCountManager @NonInjectable constructor(project: Project, private val usagesCounter: UsagesCounter): Disposable {

  @Suppress("unused")
  constructor(project: Project): this(project, DefaultUsagesCounter())

  companion object {
    @JvmStatic
    fun getInstance(project: Project): UsagesCountManager {
      return project.getService(UsagesCountManager::class.java)
    }
  }

  private val externalUsagesCache: ConcurrentMap<VirtualFile, FileUsagesCache> = ContainerUtil.createConcurrentWeakKeySoftValueMap()

  init {
    val listener = object : PsiTreeAnyChangeAbstractAdapter() {
      override fun onChange(psiFile: PsiFile?) {
        val file = psiFile?.virtualFile ?: return
        val valueToKeep = externalUsagesCache[file]
        externalUsagesCache.clear()
        if (valueToKeep != null) {
          externalUsagesCache[file] = valueToKeep
        }
      }
    }
    PsiManager.getInstance(project).addPsiTreeChangeListener(listener, this)
  }

  fun countMemberUsages(file: PsiFile, member: PsiMember): Int {
    val virtualFile = PsiUtilCore.getVirtualFile(file)
    if (virtualFile == null) {
      return usagesCounter.countUsages(file, findSuperMembers(member), GlobalSearchScope.allScope(file.project))
    }
    return externalUsagesCache.getOrPut(virtualFile) { FileUsagesCache() }.countMemberUsagesCached(usagesCounter, file, member)
  }

  override fun dispose() {
  }

  interface UsagesCounter {
    fun countUsages(file: PsiFile, members: List<PsiMember>, scope: SearchScope): Int
  }

  private class DefaultUsagesCounter: UsagesCounter {
    override fun countUsages(file: PsiFile, members: List<PsiMember>, scope: SearchScope): Int {
      return JavaTelescope.usagesCount(file, members, scope)
    }
  }
}

private class FileUsagesCache {
  private val externalUsagesCache: ConcurrentMap<String, Int> = ContainerUtil.createConcurrentWeakKeySoftValueMap()

  fun countMemberUsagesCached(usagesCounter: UsagesCountManager.UsagesCounter, file: PsiFile, member: PsiMember): Int {
    val key = QualifiedNameProviderUtil.getQualifiedName(member)
    val superMembers = findSuperMembers(member)
    if (key == null) {
      return usagesCounter.countUsages(file, superMembers, GlobalSearchScope.allScope(file.project))
    }
    //external usages should be counted first to ensure heavy cases are skipped and to avoid freezes in ScopeOptimizer
    //CompilerReferenceServiceBase#getScopeWithCodeReferences method invokes kotlin resolve and can be very slow
    val localScope = GlobalSearchScope.fileScope(file)
    val externalScope = GlobalSearchScope.notScope(localScope)
    val externalUsages = externalUsagesCache.getOrPut(key) { usagesCounter.countUsages(file, superMembers, externalScope) }
    if (externalUsages < 0) return externalUsages
    val localUsages = usagesCounter.countUsages(file, superMembers, localScope)
    if (localUsages < 0) return localUsages
    return externalUsages + localUsages
  }
}

private fun findSuperMembers(member: PsiMember): List<PsiMember> {
  val methodMembers = if (member is PsiMethod) DeepestSuperMethodsSearch.search(member).findAll().toList() else emptyList()
  return methodMembers.ifEmpty { listOf(member) }
}