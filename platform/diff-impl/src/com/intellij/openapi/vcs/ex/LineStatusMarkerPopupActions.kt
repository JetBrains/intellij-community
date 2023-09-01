// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection

object LineStatusMarkerPopupActions {
  @JvmStatic
  fun showDiff(tracker: LineStatusTrackerI<*>, range: Range) {
    val project = tracker.project
    val ourRange = expand(range, tracker.document, tracker.vcsDocument)
    val vcsContent = createDiffContent(project,
                                       tracker.vcsDocument,
                                       tracker.virtualFile,
                                       getVcsTextRange(tracker, ourRange))
    val currentContent = createDiffContent(project,
                                           tracker.document,
                                           tracker.virtualFile,
                                           getCurrentTextRange(tracker, ourRange))
    val request = SimpleDiffRequest(DiffBundle.message("dialog.title.diff.for.range"),
                                    vcsContent, currentContent,
                                    DiffBundle.message("diff.content.title.up.to.date"),
                                    DiffBundle.message("diff.content.title.current.range"))
    DiffManager.getInstance().showDiff(project, request)
  }

  private fun createDiffContent(project: Project?,
                                document: Document,
                                highlightFile: VirtualFile?,
                                textRange: TextRange): DiffContent {
    val content = DiffContentFactory.getInstance().create(project, document, highlightFile)
    return DiffContentFactory.getInstance().createFragment(project, content, textRange)
  }

  private fun expand(range: Range, document: Document, uDocument: Document): Range {
    val canExpandBefore = range.line1 != 0 && range.vcsLine1 != 0
    val canExpandAfter = range.line2 < DiffUtil.getLineCount(document) && range.vcsLine2 < DiffUtil.getLineCount(uDocument)
    val offset1 = range.line1 - if (canExpandBefore) 1 else 0
    val uOffset1 = range.vcsLine1 - if (canExpandBefore) 1 else 0
    val offset2 = range.line2 + if (canExpandAfter) 1 else 0
    val uOffset2 = range.vcsLine2 + if (canExpandAfter) 1 else 0
    return Range(offset1, offset2, uOffset1, uOffset2)
  }

  fun copyVcsContent(tracker: LineStatusTrackerI<*>, range: Range) {
    val content = getVcsContent(tracker, range).toString() + "\n"
    CopyPasteManager.getInstance().setContents(StringSelection(content))
  }

  fun getCurrentContent(tracker: LineStatusTrackerI<*>, range: Range): CharSequence {
    return DiffUtil.getLinesContent(tracker.document, range.line1, range.line2)
  }

  fun getVcsContent(tracker: LineStatusTrackerI<*>, range: Range): CharSequence {
    return DiffUtil.getLinesContent(tracker.vcsDocument, range.vcsLine1, range.vcsLine2)
  }

  fun getCurrentTextRange(tracker: LineStatusTrackerI<*>, range: Range): TextRange {
    return DiffUtil.getLinesRange(tracker.document, range.line1, range.line2)
  }

  fun getVcsTextRange(tracker: LineStatusTrackerI<*>, range: Range): TextRange {
    return DiffUtil.getLinesRange(tracker.vcsDocument, range.vcsLine1, range.vcsLine2)
  }
}
