// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find


import com.intellij.ide.SelectInEditorManager
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.textChunk
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.usageView.UsageInfo
import com.intellij.usages.TextChunk
import com.intellij.usages.UsageInfoAdapter
import com.intellij.usages.UsagePresentation
import com.intellij.usages.rules.MergeableUsage
import com.intellij.usages.rules.UsageDocumentProcessor
import com.intellij.usages.rules.UsageInFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

private val LOG = logger<UsageInfoModel>()

internal class UsageInfoModel private constructor(val project: Project, val model: FindInFilesResult) : UsageInfoAdapter, UsageInFile, UsageDocumentProcessor {
  private val virtualFile: VirtualFile? = run {
    val virtualFile = model.fileId.virtualFile()
    if (virtualFile == null) LOG.error("Cannot find virtualFile for ${model.presentablePath}")
    virtualFile
  }

  private val psiFile: PsiFile? = run {
    val psiFile = virtualFile?.let {
      PsiManager.getInstance(project).findFile(it)
    }
    if (psiFile == null) LOG.error("Cannot find psiFile for file ${model.presentablePath}")
    psiFile
  }

  private val defaultRange: TextRange =
    TextRange(model.navigationOffset, model.navigationOffset + model.length)

  private val smartRange: SmartPsiFileRange? = run {
    val range = psiFile?.let {
      SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(it, defaultRange)
    }
    if (range == null) LOG.error("Cannot create smart range for ${model.presentablePath}")
    range
  }

  private val defaultMergedRanges: List<TextRange> = model.mergedOffsets.map {
    TextRange(it, it + model.length)
  }

  private val mergedSmartRanges: List<SmartPsiFileRange> = run {
    val psiFile = psiFile ?: return@run emptyList()
    if (smartRange != null && defaultMergedRanges.size == 1) {
      return@run listOf(smartRange)
    }
    defaultMergedRanges.map {
      SmartPointerManager.getInstance(project).createSmartPsiFileRangePointer(psiFile, it)
    }
  }

  private val usageInfos: Array<UsageInfo> = run {
    if (psiFile == null) return@run emptyArray<UsageInfo>()
    getMergedRanges().map {
      UsageInfo(
        psiFile,
        it, false
      )
    }.toTypedArray()
  }

  companion object {
    @JvmStatic
    @RequiresReadLock
    @RequiresBackgroundThread
    fun createUsageInfoModel(project: Project, model: FindInFilesResult): UsageInfoModel {
      return UsageInfoModel(project, model)
    }
  }
  private fun getMergedRanges(): List<TextRange> {
    return if (mergedSmartRanges.isEmpty()) defaultMergedRanges else mergedSmartRanges.mapNotNull { smartRange -> smartRange.range?.let { TextRange(it.startOffset, it.endOffset) } }
  }



  private fun calculateRange(): Segment {
    val range = smartRange?.range
    if (range == null) {
      LOG.warn("Smart range is null for ${model.presentablePath}. The default range will be used.")
    }
    return range ?: defaultRange
  }

  override fun isValid(): Boolean {
    if (psiFile == null || psiFile.getFileType().isBinary()) {
      return false
    }
    val ranges = getMergedRanges()
    val fileEndOffset = psiFile.textRange.endOffset
    return ranges.isNotEmpty() && ranges.all { TextRange.isProperRange(it.startOffset, it.endOffset) && it.endOffset <= fileEndOffset}
  }

  override fun getMergedInfos(): Array<UsageInfo> {
    return usageInfos
  }

  override fun getMergedInfosAsync(): CompletableFuture<Array<UsageInfo>> {
    val future = CompletableFuture<Array<UsageInfo>>()
    future.complete(mergedInfos)
    return future
  }

  override fun isReadOnly(): Boolean {
    return virtualFile == null || !virtualFile.isWritable()
  }

  override fun canNavigate(): Boolean = virtualFile != null && virtualFile.isValid
  override fun canNavigateToSource(): Boolean = canNavigate()

  override fun navigate(requestFocus: Boolean) {
    if (canNavigate()) {
      openTextEditor(requestFocus)
    }
   }

  private fun openTextEditor(requestFocus: Boolean): Editor? {
    val virtualFile = virtualFile ?: return null
    val range = calculateRange()
    val descriptor = OpenFileDescriptor(project, virtualFile, range.startOffset)
    return FileEditorManager.getInstance(project).openTextEditor(descriptor, requestFocus)
  }

  override fun getPath(): String = model.presentablePath

  override fun getLine(): Int = model.line

  override fun getNavigationOffset(): Int = calculateRange().startOffset

  override fun getLocation(): FileEditorLocation? {
    if (virtualFile == null) return null
    val editor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile)
    if (editor !is TextEditor) return null

    val segment = calculateRange()
    return TextEditorLocation(segment.getStartOffset(), editor)

  }

  override fun highlightInEditor() {
    if (!isValid()) return

    val marker = calculateRange()
    SelectInEditorManager.getInstance(project).selectInEditor(virtualFile, marker.getStartOffset(), marker.getEndOffset(), false, false)
  }

  override fun selectInEditor() {
    if (!isValid()) return
    val editor: Editor? = openTextEditor(true)
    if (editor == null) {
      LOG.error("Cannot open editor for $path")
      return
    }
    val marker = calculateRange()
    editor.getSelectionModel().setSelection(marker.getStartOffset(), marker.getEndOffset())
  }

  override fun merge(mergeableUsage: MergeableUsage): Boolean {
    return false
  }

  override fun reset() {
  }

  override fun getPresentation(): UsagePresentation {
    return UsageInfoModelPresentation(model)
  }

  override fun getFile(): VirtualFile? = virtualFile

  override fun getDocument(): Document? {
    if (psiFile == null) return null
    return PsiDocumentManager.getInstance(project).getDocument(psiFile)
  }

  private class UsageInfoModelPresentation(val model: FindInFilesResult) : UsagePresentation {
    override fun getIcon(): Icon? = model.iconId?.icon()

    override fun getText(): Array<out TextChunk> = model.presentation.map { it.textChunk() }.toTypedArray()

    override fun getPlainText(): String = model.presentation.joinToString("") { it.text }

    override fun getTooltipText(): @NlsContexts.Tooltip String? = model.tooltipText
  }
}