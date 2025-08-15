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
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.*
import com.intellij.usageView.UsageInfo
import com.intellij.usages.TextChunk
import com.intellij.usages.UsageChangedListener
import com.intellij.usages.UsageInfoAdapter
import com.intellij.usages.UsagePresentation
import com.intellij.usages.rules.MergeableUsage
import com.intellij.usages.rules.UsageDocumentProcessor
import com.intellij.usages.rules.UsageInFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.*
import java.util.concurrent.CompletableFuture
import javax.swing.Icon

private val LOG = logger<UsageInfoModel>()

internal class UsageInfoModel private constructor(val project: Project, val model: FindInFilesResult, val coroutineScope: CoroutineScope) : UsageInfoAdapter, UsageInFile, UsageDocumentProcessor, Disposable {
  private val virtualFile: VirtualFile? = run {
    val virtualFile = model.usageInfos.firstOrNull()?.file?.virtualFile ?: model.fileId.virtualFile()
    if (virtualFile == null) LOG.error("Cannot find virtualFile for ${model.presentablePath}")
    virtualFile
  }

  private var cachedPsiFile: PsiFile? = null
  private var document: Document? = null
  private var cachedSmartRange: SmartPsiFileRange? = null
  private var cachedMergedSmartRanges: List<SmartPsiFileRange> = emptyList()
  private var cachedUsageInfos: List<UsageInfo> = emptyList()
    get() {
      if (field.isEmpty()) {
        LOG.warn("UsageInfos are not yet initialized for ${model.presentablePath}")
      }
      return field
    }
  private var isLoaded: Boolean = false
    set(value) {
      field = value
      if (value) {
        initializationListeners.forEach { it.modelInitialized() }
      }
    }
  private var initializationJob: Job? = null
  private val initializationListeners: MutableList<UsageChangedListener> = ContainerUtil.createLockFreeCopyOnWriteList()

  private val defaultRange: TextRange = TextRange(model.navigationOffset, model.navigationOffset + model.length)
  private val defaultMergedRanges: List<TextRange> = model.mergedOffsets.map {
    TextRange(it, it + model.length)
  }

  private val fullUpdateListener = object : DocumentFullUpdateListener {
    override fun onFullUpdateDocument(document: Document) {
      PsiDocumentManager.getInstance(project).performForCommittedDocument(document) {
        initialize()
      }
    }
  }

  override fun dispose() {
    (document as? DocumentEx)?.removeFullUpdateListener(fullUpdateListener)
    initializationListeners.clear()
  }

  init {
    initialize()
  }

  private fun initialize(onDocumentUpdated: ((usageInfos: List<UsageInfo>) -> Unit?)? = null) {
    //local IDE case
    if (model.usageInfos.isNotEmpty()) {
      cachedUsageInfos = model.usageInfos
      cachedPsiFile = cachedUsageInfos.firstOrNull()?.file
      cachedMergedSmartRanges = cachedUsageInfos.map { it.psiFileRange }.sortedBy { it.range?.startOffset ?: 0 }
      cachedSmartRange = cachedMergedSmartRanges.firstOrNull()
      isLoaded = true
    }
    //RemDev case - we need to load psi elements
    else {
      if (initializationJob?.isActive == true) {
        LOG.debug("Initialization job is already in progress ${model.presentablePath}")
        return
      }
      initializationJob = coroutineScope.launch(Dispatchers.Default) {
        try {
          if (project.isDisposed) {
            LOG.warn("Project is disposed for ${model.presentablePath}")
            return@launch
          }
          if (virtualFile?.isValid == false) {
            LOG.warn("VirtualFile is invalid for ${model.presentablePath}")
            return@launch
          }

          readAction {
            val psiFile = virtualFile?.let { vFile ->
              PsiManager.getInstance(project).findFile(vFile)
            }
            cachedPsiFile = psiFile
            if (document == null) document = psiFile?.fileDocument?.also {
              (it as? DocumentEx)?.addFullUpdateListener(fullUpdateListener)
            }

            if (psiFile == null) {
              LOG.error("Cannot find psiFile for file ${model.presentablePath}")
              return@readAction
            }

            val smartPointerManager = SmartPointerManager.getInstance(project)
            val smartRange = smartPointerManager.createSmartPsiFileRangePointer(psiFile, defaultRange)
            cachedSmartRange = smartRange

            cachedMergedSmartRanges = if (defaultMergedRanges.size == 1) {
              listOf(smartRange)
            }
            else {
              defaultMergedRanges.map { range ->
                smartPointerManager.createSmartPsiFileRangePointer(psiFile, range)
              }
            }
            cachedUsageInfos = defaultMergedRanges.map { UsageInfo(psiFile, it, false) }
          }
        }
        finally {
          //if we get some model without ranges or proper ranges were loaded - full model loaded
          val loaded = defaultMergedRanges.isEmpty() || cachedUsageInfos.isNotEmpty()
          isLoaded = loaded
          if (loaded) {
            withContext(Dispatchers.EDT) {
              onDocumentUpdated?.invoke(cachedUsageInfos)
            }
          }
        }
      }
    }
  }

  override fun addInitializationListener(listener: UsageChangedListener, disposable: Disposable) {
    initializationListeners.add(listener)
    Disposer.tryRegister(disposable) {
      initializationListeners.remove(listener)
    }
  }

  companion object {
    @JvmStatic
    @RequiresBackgroundThread
    fun createUsageInfoModel(project: Project, model: FindInFilesResult, coroutineScope: CoroutineScope): UsageInfoModel {
      return UsageInfoModel(project, model, coroutineScope)
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
    val range: Segment? = cachedSmartRange?.range
    if (range == null) {
      val logMessage = "Smart range is null for ${model.presentablePath}. The default range will be used."
      if (initializationJob?.isActive != true) LOG.warn(logMessage) else LOG.debug(logMessage)
      return defaultRange
    }
    return range
  }

  override fun isValid(): Boolean {
    if (virtualFile?.isValid != true) {
      return false
    }

    if (FileTypeManager.getInstance().getFileTypeByFile(virtualFile).isBinary()) {
      return false
    }

    val fileLength = document?.textLength ?: cachedPsiFile?.textLength ?: model.fileLength

    val ranges = getMergedRanges()
    return ranges.isNotEmpty() && ranges.all { range ->
      TextRange.isProperRange(range.startOffset, range.endOffset) &&
      range.endOffset <= fileLength
    }
  }

  override fun getMergedInfos(): Array<UsageInfo> {
    if (isLoaded && defaultMergedRanges.isNotEmpty() && cachedUsageInfos.isEmpty()) {
      initialize()
    }
    return cachedUsageInfos.toTypedArray()
  }

  override fun getMergedInfosAsync(): CompletableFuture<Array<UsageInfo>> {
    return CompletableFuture.supplyAsync(captureThreadContext { mergedInfos })
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
    document?.let { return it }
    return try {
      runReadAction {
        val psiDoc = cachedPsiFile?.let { psiFile -> PsiDocumentManager.getInstance(project).getDocument(psiFile) }
        if (psiDoc == null) {
          LOG.warn("PsiFile is not yet loaded for path ${model.presentablePath}. Trying to get document from virtualFile")
        }
        val doc = psiDoc ?: virtualFile?.findDocument()
        if (doc != null && document == null) {
          document = doc
          (doc as? DocumentEx)?.addFullUpdateListener(fullUpdateListener)
        }
        doc
      }
    }
    catch (t: Throwable) {
      LOG.warn("Failed to get document for ${model.presentablePath}", t)
      null
    }
  }

  private class UsageInfoModelPresentation(val model: FindInFilesResult) : UsagePresentation {
    override fun getIcon(): Icon? = model.iconId?.icon()

    override fun getText(): Array<out TextChunk> = model.presentation.map { it.textChunk() }.toTypedArray()

    override fun getPlainText(): String = model.presentation.joinToString("") { it.text }

    override fun getTooltipText(): @NlsContexts.Tooltip String? = model.tooltipText
  }
}