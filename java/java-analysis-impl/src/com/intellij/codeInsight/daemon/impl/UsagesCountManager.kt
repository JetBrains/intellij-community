// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.ConcurrentMap


class UsagesCountManager(project: Project): Disposable {

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
    return externalUsagesCache.getOrPut(virtualFile) { FileUsagesCache() }.countMemberUsagesCached(file, member)
  }

  override fun dispose() {
  }
}

private class FileUsagesCache {
  private val externalUsagesCache: ConcurrentMap<String, Int> = ContainerUtil.createConcurrentWeakKeySoftValueMap()

  fun countMemberUsagesCached(file: PsiFile, member: PsiMember): Int {
    val methodMembers = if (member is PsiMethod) DeepestSuperMethodsSearch.search(member).findAll().toList() else emptyList()
    val superMembers = methodMembers.ifEmpty { listOf(member) }
    val localScope = GlobalSearchScope.fileScope(file)
    val externalScope = GlobalSearchScope.notScope(localScope)

    val internalUsages = JavaTelescope.usagesCount(file, superMembers, localScope)
    val key = QualifiedNameProviderUtil.getQualifiedName(member)
    val externalUsages = if (key != null) {
      externalUsagesCache.getOrPut(key) { JavaTelescope.usagesCount(file, superMembers, externalScope) }
    }
    else {
      JavaTelescope.usagesCount(file, superMembers, externalScope)
    }
    return externalUsages + internalUsages
  }
}