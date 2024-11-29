// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.hints.codeVision.UsageCounterConfigurationBase
import com.intellij.codeInsight.hints.codeVision.UsagesCountManagerBase
import com.intellij.ide.actions.QualifiedNameProviderUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.DeepestSuperMethodsSearch
import com.intellij.serviceContainer.NonInjectable


@Service(Service.Level.PROJECT)
class UsagesCountManager @NonInjectable constructor(project: Project, configuration: UsageCounterConfiguration): UsagesCountManagerBase<PsiMember>(project, configuration) {

  @Suppress("unused")
  constructor(project: Project) : this(project, object : UsageCounterConfiguration {})

  companion object {
    @JvmStatic
    fun getInstance(project: Project): UsagesCountManager {
      return project.getService(UsagesCountManager::class.java)
    }
  }

  override fun findSupers(member: PsiMember): List<PsiMember> = findSuperMembers(member)
  override fun getKey(member: PsiMember): String? = QualifiedNameProviderUtil.getQualifiedName(member)

  private fun findSuperMembers(member: PsiMember): List<PsiMember> {
    val methodMembers = if (member is PsiMethod) DeepestSuperMethodsSearch.search(member).findAll().toList() else emptyList()
    return methodMembers.ifEmpty { listOf(member) }
  }
}

interface UsageCounterConfiguration: UsageCounterConfigurationBase<PsiMember> {
  override fun countUsages(file: PsiFile, members: List<PsiMember>, scope: SearchScope): Int {
    return JavaTelescope.usagesCount(file, members, scope)
  }
}