// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usages.UsageInfoAdapter

interface UsagePresentationProvider {
  companion object {
    private val EP_NAME = ExtensionPointName<UsagePresentationProvider>("com.intellij.usagePresentationProvider")

    fun getPresentation(usageInfo: UsageInfoAdapter, project: Project, scope: GlobalSearchScope): UsagePresentation? {
      for (extension in EP_NAME.extensionList) {
        val presentation = extension.getUsagePresentation(usageInfo, project, scope)
        if (presentation != null) {
          return presentation
        }
      }
      return null
    }
  }
  fun getUsagePresentation(usageInfo: UsageInfoAdapter, project: Project, scope: GlobalSearchScope): UsagePresentation?
}