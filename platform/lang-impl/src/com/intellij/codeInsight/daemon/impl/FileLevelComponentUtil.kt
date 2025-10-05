// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
@file:JvmName("FileLevelComponentUtil")

package com.intellij.codeInsight.daemon.impl

import com.intellij.codeInsight.daemon.impl.HighlightInfo.IntentionActionDescriptor
import com.intellij.codeInsight.intention.impl.FileLevelIntentionComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

@RequiresEdt
fun doAddFileLevelInfoComponent(
  info: HighlightInfo,
  psiFile: PsiFile,
  textEditor: TextEditor,
  fileEditorManager: FileEditorManager,
): FileLevelIntentionComponent {
  val actionRanges = getActionRanges(info)
  val component = FileLevelIntentionComponent(/* description = */ info.description,
                                              /* severity = */ info.severity,
                                              /* gutterMark = */ info.gutterIconRenderer,
                                              /* intentions = */ actionRanges,
                                              /* psiFile = */ psiFile,
                                              /* editor = */ textEditor.editor,
                                              /* tooltip = */ info.toolTip)
  fileEditorManager.addTopComponent(textEditor, component)
  info.addFileLevelComponent(textEditor, component)
  return component
}

@RequiresEdt
fun doRemoveFileLevelInfoComponent(
  info: HighlightInfo,
  fileEditor: FileEditor,
  fileEditorManager: FileEditorManager,
) {
  val component = info.getFileLevelComponent(fileEditor) ?: return
  fileEditorManager.removeTopComponent(fileEditor, component)
  info.removeFileLeverComponent(fileEditor)
}

private fun getActionRanges(info: HighlightInfo): List<Pair<IntentionActionDescriptor, TextRange>> {
  return buildList {
    info.findRegisteredQuickFix { descriptor, range ->
      this@buildList.add(Pair.create(descriptor, range))
      null
    }
  }
}
