// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find

import com.intellij.find.impl.UsagePresentation
import com.intellij.find.impl.UsagePresentationProvider
import com.intellij.ide.ui.colors.color
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usages.UsageInfoAdapter

class UsageInfoModelPresentationProvider: UsagePresentationProvider {
  override fun getUsagePresentation(usageInfo: UsageInfoAdapter, project: Project, scope: GlobalSearchScope): UsagePresentation? {
    if (usageInfo !is UsageInfoModel) return null
    val model = usageInfo.model
    return UsagePresentation(model.presentation.map { it.toTextChunk() }.toTypedArray(),
                             model.fileId.virtualFile()?.let { VfsPresentationUtil.getFileBackgroundColor(project, it) }, model.path)
  }
}