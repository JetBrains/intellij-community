// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usages.TextChunk
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageInfoAdapter
import com.intellij.usages.impl.UsageViewImpl.USAGE_COMPARATOR_BY_FILE_AND_OFFSET
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color


@ApiStatus.Internal
class FindPopupItem(
  val usage: UsageInfoAdapter,
  val presentation: UsagePresentation?,
) {

  val path: String = usage.path
  val line: Int = usage.line
  val navigationOffset: Int = usage.navigationOffset

  val presentableText: String?
    get() = presentation?.text?.joinToString("")

  fun withPresentation(presentation: UsagePresentation?): FindPopupItem {
    return FindPopupItem(usage, presentation)
  }
}

internal class SearchEverywhereItem(
  val usage: UsageInfo2UsageAdapter,
  val presentation: UsagePresentation,
) {
  val presentableText: String
    get() = presentation.text.joinToString("")

  fun withPresentation(presentation: UsagePresentation): SearchEverywhereItem {
    return SearchEverywhereItem(usage, presentation)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    if (USAGE_COMPARATOR_BY_FILE_AND_OFFSET.compare(usage, (other as SearchEverywhereItem).usage) != 0) return false

    return presentableText == other.presentableText
  }

  override fun hashCode(): Int = presentableText.hashCode()
}

@ApiStatus.Internal
class UsagePresentation(
  val text: Array<out TextChunk>,
  val backgroundColor: Color?,
  val fileString: @Nls String,
)

internal fun usagePresentation(
  project: Project,
  scope: GlobalSearchScope,
  usage: UsageInfoAdapter,
): UsagePresentation? {
  return if (usage is UsageInfo2UsageAdapter) {
    usagePresentation(project, scope, usage)
  }
  else {
    null
  }
}

internal fun usagePresentation(
  project: Project,
  scope: GlobalSearchScope,
  usage: UsageInfo2UsageAdapter,
): UsagePresentation {
  ApplicationManager.getApplication().assertIsNonDispatchThread()
  val text: Array<TextChunk> = usage.presentation.text
  return UsagePresentation(
    text = text,
    backgroundColor = VfsPresentationUtil.getFileBackgroundColor(project, usage.file),
    fileString = getFilePath(project, scope, usage),
  )
}

private fun getFilePath(project: Project, scope: GlobalSearchScope, usage: UsageInfo2UsageAdapter): @NlsSafe String {
  val file = usage.file ?: return ""
  return if (ScratchUtil.isScratch(file)) {
    ScratchUtil.getRelativePath(project, file)
  }
  else {
    UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file, scope)
  }
}
