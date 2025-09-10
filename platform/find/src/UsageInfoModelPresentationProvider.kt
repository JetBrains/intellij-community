// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.find.impl.UsagePresentation
import com.intellij.find.impl.UsagePresentationProvider
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.textChunk
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usages.UsageInfoAdapter

private class UsageInfoModelPresentationProvider: UsagePresentationProvider {
  override fun getUsagePresentation(usageInfo: UsageInfoAdapter, project: Project, scope: GlobalSearchScope?): UsagePresentation? {
    if (usageInfo !is UsageInfoModel) return null
    val model = usageInfo.model
    return UsagePresentation(model.presentation.map { it.textChunk() }.toTypedArray(),
                             model.backgroundColor?.color(), model.shortenPresentablePath)
  }
}