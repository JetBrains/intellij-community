// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usages.UsageInfoAdapter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface UsagePresentationProvider {
  companion object {
    private val EP_NAME = ExtensionPointName<UsagePresentationProvider>("com.intellij.usagePresentationProvider")

    @JvmStatic
    fun getPresentation(usageInfo: UsageInfoAdapter, project: Project, scope: GlobalSearchScope?): UsagePresentation? {
      return EP_NAME.computeSafeIfAny { extension -> extension.getUsagePresentation(usageInfo, project, scope)}
    }
  }
  fun getUsagePresentation(usageInfo: UsageInfoAdapter, project: Project, scope: GlobalSearchScope?): UsagePresentation?
}