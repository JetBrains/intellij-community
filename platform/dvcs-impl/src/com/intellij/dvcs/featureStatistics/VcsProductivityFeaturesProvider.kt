// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.featureStatistics

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.featureStatistics.ApplicabilityFilter
import com.intellij.featureStatistics.ProductivityFeaturesProvider
import com.intellij.openapi.project.Project

internal class VcsProductivityFeaturesProvider : ProductivityFeaturesProvider() {
  override fun getXmlFilesUrls() = listOf("VcsProductivityFeatures.xml")

  override fun getApplicabilityFilters(): Array<ApplicabilityFilter> {
    return arrayOf(VcsFeaturesApplicabilityFilter())
  }
}

private class VcsFeaturesApplicabilityFilter : ApplicabilityFilter {
  override fun isApplicable(featureId: String, project: Project?): Boolean {
    if (featureId == "vcs.use.integration") return true
    if (project == null) return false
    return VcsRepositoryManager.getInstance(project).repositories.isNotEmpty()
  }

  override fun getPrefix() = "vcs"
}