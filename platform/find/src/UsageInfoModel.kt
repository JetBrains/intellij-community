// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.find


import com.intellij.concurrency.captureThreadContext
import com.intellij.ide.SelectInEditorManager
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.ui.textChunk
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.ex.DocumentFullUpdateListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.*
import com.intellij.usageView.UsageInfo
import com.intellij.usages.TextChunk
import com.intellij.usages.UsageInfoAdapter
import com.intellij.usages.UsagePresentation
import com.intellij.usages.rules.MergeableUsage
import com.intellij.usages.rules.UsageDocumentProcessor
import com.intellij.usages.rules.UsageInFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

private val LOG = logger<UsageInfoModel>()

internal class UsageInfoModel private constructor(val project: Project, val model: FindInFilesResult, val coroutineScope: CoroutineScope, val onDocumentUpdated: (usageInfos: List<UsageInfo>) -> Unit?) : UsageInfoAdapter, UsageInFile, UsageDocumentProcessor, Disposable {
  private val virtualFile: VirtualFile? = run {
    val virtualFile = model.fileId.virtualFile()
    if (virtualFile == null) LOG.error("Cannot find virtualFile for ${model.presentablePath}")
    virtualFile
  }

  private var cachedPsiFile: PsiFile? = null
  private var document: Document? = null
  private var cachedSmartRange: SmartPsiFileRange? = null
  private var cachedMergedSmartRanges: List<SmartPsiFileRange> = emptyList()
  private var cachedUsageInfos: List<UsageInfo> = emptyList()
  private val initializationCompleted = CompletableFuture<Unit>()

  private val defaultRange: TextRange = TextRange(model.navigationOffset, model.navigationOffset + model.length)
  private val defaultMergedRanges: List<TextRange> = model.mergedOffsets.map {
    TextRange(it, it + model.length)
  }

  private val fullUpdateListener = object : DocumentFullUpdateListener {
    override fun onFullUpdateDocument(document: Document) {
      PsiDocumentManager.getInstance(project).performForCommittedDocument(document) {
        initialize(onDocumentUpdated)
      }
    }
  }

  override fun dispose() {
    (document as? DocumentImpl)?.removeFullUpdateListener(fullUpdateListener)
  }

  init {
    initialize()
  }

  private fun initialize(onDocumentUpdated: ((usageInfos: List<UsageInfo>) -> Unit?)? = null) {
    coroutineScope.launch(Dispatchers.Default) {
      try {

        val (file, range, mergedRanges, usageInfos) = readAction {

          val psiFile = virtualFile?.let { vFile ->
            PsiManager.getInstance(project).findFile(vFile)
          }
          if (document == null) document = psiFile?.fileDocument?.also {
            (it as? DocumentEx)?.addFullUpdateListener(fullUpdateListener)
          }

          if (psiFile == null) return@readAction CachedValues(null, null, emptyList(), emptyList())

          val smartRange = SmartPointerManager.getInstance(project)
            .createSmartPsiFileRangePointer(psiFile, defaultRange)

          val smartMergedRanges = if (defaultMergedRanges.size == 1) {
            listOf(smartRange)
          }
          else {
            defaultMergedRanges.map { range ->
              SmartPointerManager.getInstance(project)
                .createSmartPsiFileRangePointer(psiFile, range)
            }
          }
          val usageInfos = defaultMergedRanges.map { UsageInfo(psiFile, it, false) }

          CachedValues(psiFile, smartRange, smartMergedRanges, usageInfos)
        }

        cachedPsiFile = file
        if (cachedPsiFile == null) {
          LOG.error("Cannot find psiFile for file ${model.presentablePath}")
          initializationCompleted.complete(Unit)
          return@launch
        }

        cachedSmartRange = range
        cachedMergedSmartRanges = mergedRanges
        cachedUsageInfos = usageInfos
      }
      finally {
        initializationCompleted.complete(Unit)
        withContext(Dispatchers.EDT) {
          onDocumentUpdated?.invoke(cachedUsageInfos)
        }
      }
    }
  }

  private data class CachedValues(val psiFile: PsiFile?, val smartRange: SmartPsiFileRange?, val mergedSmartRanges: List<SmartPsiFileRange>, val usageInfos: List<UsageInfo>)

  fun getPsiFile(): PsiFile? {
    initializationCompleted.get()
    return cachedPsiFile
  }

  companion object {
    @JvmStatic
    @RequiresBackgroundThread
    fun createUsageInfoModel(project: Project, model: FindInFilesResult, coroutineScope: CoroutineScope, onDocumentUpdated: (usageInfos: List<UsageInfo>) -> Unit?): UsageInfoModel {
      return UsageInfoModel(project, model, coroutineScope, onDocumentUpdated)
    }
  }

  private fun getMergedRanges(): List<TextRange> {
    return if (cachedMergedSmartRanges.isEmpty()) defaultMergedRanges
    else cachedMergedSmartRanges
      .mapNotNull { smartRange ->
        smartRange.range?.let { TextRange(it.startOffset, it.endOffset) }
      }.ifEmpty { defaultMergedRanges }
  }

  private fun calculateRange(): Segment {
    val range: Segment?
    if (initializationCompleted.isDone) {
      range = cachedSmartRange?.range
      if (range == null) {
        LOG.warn("Smart range is null for ${model.presentablePath}. The default range will be used.")
      }
    }
    else {
      LOG.info("Range is not yet calculated for ${model.presentablePath}. The default range will be used.")
      range = defaultRange
    }
    return range ?: defaultRange
  }

  override fun isValid(): Boolean {
    val psiFile = getPsiFile()
    if (psiFile == null || psiFile.getFileType().isBinary()) {
      return false
    }
    val ranges = getMergedRanges()
    val fileEndOffset = psiFile.textRange.endOffset
    return ranges.isNotEmpty() && ranges.all { TextRange.isProperRange(it.startOffset, it.endOffset) && it.endOffset <= fileEndOffset}
  }

  override fun getMergedInfos(): Array<UsageInfo> {
    initializationCompleted.get()
    return cachedUsageInfos.toTypedArray()
  }

  override fun getMergedInfosAsync(): CompletableFuture<Array<UsageInfo>> {
    return CompletableFuture.supplyAsync(captureThreadContext { getMergedInfos() })
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
    if (cachedPsiFile == null) return runReadAction {
      LOG.warn("PsiFile is not yet loaded for path ${model.presentablePath}. Trying to get document from virtualFile")
      virtualFile?.findDocument()
    }
    return PsiDocumentManager.getInstance(project).getDocument(cachedPsiFile!!)
  }

  private class UsageInfoModelPresentation(val model: FindInFilesResult) : UsagePresentation {
    override fun getIcon(): Icon? = model.iconId?.icon()

    override fun getText(): Array<out TextChunk> = model.presentation.map { it.textChunk() }.toTypedArray()

    override fun getPlainText(): String = model.presentation.joinToString("") { it.text }

    override fun getTooltipText(): @NlsContexts.Tooltip String? = model.tooltipText
  }
}