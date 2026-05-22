// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.formatting.service.CoreFormattingService
import com.intellij.formatting.service.FormattingServiceUtil
import com.intellij.formatting.service.structuredAsyncDocumentFormattingScope
import com.intellij.lang.LanguageFormatting
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.SelectionModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.calcRelativeToProjectPath
import com.intellij.openapi.project.isProjectOrWorkspaceFile
import com.intellij.openapi.roots.GeneratedSourcesFilter.Companion.isGeneratedSourceByAnyFilter
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.IncorrectOperationException
import com.intellij.util.application
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.diff.FilesTooBigForDiffException
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.function.Consumer

abstract class AbstractLayoutCodeProcessor private constructor(
  project: Project,
  private val target: Target,
  private val progressText: @NlsContexts.ProgressText String,
  private val commandName: @NlsContexts.Command String,
  private var processChangedTextOnly: Boolean,
  private val fileFilters: MutableList<VirtualFileFilter> = ArrayList(),
) {
  private var postRunnable: Runnable? = null
  private var processAllFilesAsSingleUndoStep = true

  @JvmField
  protected var myPreviousCodeProcessor: AbstractLayoutCodeProcessor? = null

  @JvmField
  protected val myProject: Project = project

  var infoCollector: LayoutCodeInfoCollector? = null
    private set

  private sealed interface Target {
    object Project : Target
    class Module(val module: com.intellij.openapi.module.Module) : Target
    // todo: BUG! includeSubdirs is unused
    class Directory(val directory: PsiDirectory, val includeSubdirs: Boolean) : Target
    class Files(val files: List<PsiFile>) : Target
    class SingleFile(val psiFile: PsiFile) : Target
  }

  protected constructor(
    project: Project,
    commandName: @NlsContexts.Command String,
    progressText: @NlsContexts.ProgressText String,
    processChangedTextOnly: Boolean,
  ) : this(
    project,
    Target.Project,
    progressText,
    commandName,
    processChangedTextOnly
  )

  protected constructor(
    previous: AbstractLayoutCodeProcessor,
    commandName: @NlsContexts.Command String,
    progressText: @NlsContexts.ProgressText String,
  ) : this(
    previous.myProject,
    previous.target,
    progressText,
    commandName,
    previous.processChangedTextOnly,
    previous.fileFilters
  ) {
    myPreviousCodeProcessor = previous
    infoCollector = previous.infoCollector
  }

  protected constructor(
    project: Project,
    module: Module?,
    commandName: @NlsContexts.Command String,
    progressText: @NlsContexts.ProgressText String,
    processChangedTextOnly: Boolean,
  ) : this(
    project,
    if (module != null) Target.Module(module) else Target.Project,
    progressText,
    commandName,
    processChangedTextOnly
  )

  protected constructor(
    project: Project,
    directory: PsiDirectory,
    includeSubdirs: Boolean,
    progressText: @NlsContexts.ProgressText String,
    commandName: @NlsContexts.Command String,
    processChangedTextOnly: Boolean,
  ) : this(
    project,
    Target.Directory(directory, includeSubdirs),
    progressText,
    commandName,
    processChangedTextOnly
  )

  protected constructor(
    project: Project,
    psiFile: PsiFile,
    progressText: @NlsContexts.ProgressText String,
    commandName: @NlsContexts.Command String,
    processChangedTextOnly: Boolean,
  ) : this(
    project,
    Target.SingleFile(psiFile),
    progressText,
    commandName,
    processChangedTextOnly
  )

  protected constructor(
    project: Project,
    files: Array<PsiFile>,
    progressText: @NlsContexts.ProgressText String,
    commandName: @NlsContexts.Command String,
    postRunnable: Runnable?,
    processChangedTextOnly: Boolean,
  ) : this(
    project,
    Target.Files(files.toList()),
    progressText,
    commandName,
    processChangedTextOnly
  ) {
    this.postRunnable = postRunnable
  }

  fun setPostRunnable(postRunnable: Runnable?) {
    this.postRunnable = postRunnable
  }

  fun setCollectInfo(isCollectInfo: Boolean) {
    val newInfoCollector = if (isCollectInfo) LayoutCodeInfoCollector() else null
    getProcessorsSequence().forEach { it.infoCollector = newInfoCollector }
  }

  fun addFileFilter(filter: VirtualFileFilter) {
    fileFilters.add(filter)
  }

  fun setProcessChangedTextOnly(value: Boolean) {
    processChangedTextOnly = value
  }

  /**
   * @param singleUndoStep
   *  * if `true` then it will be possible to Undo all files processing in one shot (at least right
   * after the action, until any of the files edited further). The downside is completely that once a user edits any
   * of the files. The modal error dialog will appear: "Following files affected by this action have been already
   * changed".
   *  * if `false` then it won't be possible to Undo the action for all files in one shot, even right
   * after the action. The advantage is that the Undo chain for each file won't be broken, and it will be
   * possible to undo this action and previous changes in each file regardless of the state of other processed files.
   *
   */
  fun setProcessAllFilesAsSingleUndoStep(singleUndoStep: Boolean) {
    processAllFilesAsSingleUndoStep = singleUndoStep
  }

  /**
   * Ensures that a given file is ready to reformatting and prepares it if necessary.
   *
   * @param psiFile                    file to process
   * @param processChangedTextOnly  flag that defines is only the changed text (in terms of VCS change) should be processed
   * @return          task that triggers formatting of the given file. Returns value of that task indicates whether formatting
   * is finished correctly or not (exception occurred, user canceled formatting etc.)
   * @throws IncorrectOperationException    if unexpected exception occurred during formatting
   */
  @Throws(IncorrectOperationException::class)
  protected abstract fun prepareTask(psiFile: PsiFile, processChangedTextOnly: Boolean): FutureTask<Boolean>

  protected open fun needsReadActionToPrepareTask(): Boolean {
    return true
  }

  /**
   * Runs `writeTask` under a [WriteCommandAction] as part of file processing.
   *
   * Subclasses can override this method to delegate write wrapping to a language plugin
   * (see [OptimizeImportsProcessor.runTask], which honours
   * [com.intellij.lang.ImportOptimizer.getActionMode]).
   *
   * @param file                            virtual file
   * @param commandName                     undo-able command name
   * @param groupId                         undo group id
   * @param processAllFilesAsSingleUndoStep see [setProcessAllFilesAsSingleUndoStep]
   * @param modifyTask                      task to modify the file
   */
  @ApiStatus.Experimental
  @ApiStatus.OverrideOnly
  protected open fun runTask(
    file: VirtualFile,
    commandName: @NlsContexts.Command String,
    groupId: String,
    processAllFilesAsSingleUndoStep: Boolean,
    modifyTask: Runnable,
  ) {
    runUnderDefaultWriteCommandAction(myProject, commandName, groupId, processAllFilesAsSingleUndoStep, modifyTask)
  }

  open fun run() {
    if (target is Target.SingleFile) {
      PsiUtilCore.ensureValid(target.psiFile)
      val virtualFile = PsiUtilCore.getVirtualFile(target.psiFile)
      if (virtualFile != null) {
        runProcessFile(virtualFile)
      }
      return
    }

    val isSuccess = try {
      ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable {
        val indicator = ProgressManager.getInstance().getProgressIndicator()
        processFilesUnderProgress(indicator)
      }, getProgressTitle(), true, myProject)
    }
    catch (e: ProcessCanceledException) {
      false
    }

    if (isSuccess) {
      postRunnable?.run()
    }
  }

  fun runBackground() {
    if (target is Target.SingleFile) {
      PsiUtilCore.ensureValid(target.psiFile)
      val virtualFile = PsiUtilCore.getVirtualFile(target.psiFile)
      if (virtualFile != null) {
        runProcessFile(virtualFile)
      }
      return
    }

    object : Task.Backgroundable(myProject, getProgressTitle(), true) {
      override fun run(indicator: ProgressIndicator) {
        processFilesUnderProgress(indicator)

        if (postRunnable != null) {
          postRunnable?.run()
        }
      }
    }.queue()
  }

  private fun buildFilesIterator(): FileRecursiveIterator {
    if (target is Target.Files) {
      return FileRecursiveIterator(myProject, target.files)
    }
    if (processChangedTextOnly) {
      return buildChangedFilesIterator()
    }
    if (target is Target.Directory) {
      return FileRecursiveIterator(target.directory)
    }
    if (target is Target.Module) {
      return FileRecursiveIterator(target.module)
    }
    return FileRecursiveIterator(myProject)
  }

  private fun buildChangedFilesIterator(): FileRecursiveIterator {
    val files = getChangedFilesFromContext()
    return FileRecursiveIterator(myProject, files)
  }

  private fun getChangedFilesFromContext(): List<PsiFile> {
    val dirs = getAllSearchableDirsFromContext()
    return VcsFacade.getInstance().getChangedFilesFromDirs(myProject, dirs)
  }

  private fun getAllSearchableDirsFromContext(): List<PsiDirectory> = when (target) {
    is Target.Directory -> listOf(target.directory)
    is Target.Module -> FileRecursiveIterator.collectModuleDirectories(target.module)
    else -> FileRecursiveIterator.collectProjectDirectories(myProject)
  }


  private fun runProcessFile(file: VirtualFile) {
    val status = ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(listOf(file))
    if (status.hasReadonlyFiles()) {
      return
    }
    val runnable = Consumer { indicator: ProgressIndicator ->
      indicator.setText(progressText)
      try {
        ProcessingTask(indicator).performFileProcessing(file)
      }
      catch (e: IndexNotReadyException) {
        LOG.warn(e)
        return@Consumer
      }
      val postRunnable = postRunnable
      if (postRunnable != null) {
        ApplicationManager.getApplication().invokeLater(postRunnable)
      }
    }

    val isModal = ApplicationManager.getApplication().isHeadlessEnvironment()
    val task = object : Task.Backgroundable(myProject, getProgressTitle(), true) {
      override fun run(indicator: ProgressIndicator) {
        runnable.accept(indicator)
      }
    }
    ProgressManager.getInstance().run(task.toModalIfNeeded(isModal))
  }

  private fun getProgressTitle(): @NlsContexts.ProgressTitle String = getInitialProcessor().commandName

  private fun getProcessorsSequence() = generateSequence(this) { it.myPreviousCodeProcessor }
  private fun getInitialProcessor() = getProcessorsSequence().last()
  private fun getAllProcessors(): List<AbstractLayoutCodeProcessor> = getProcessorsSequence().toMutableList().also { it.reverse() }


  @Throws(IncorrectOperationException::class)
  fun runWithoutProgress() {
    if (target !is Target.SingleFile) {
      return
    }
    val virtualFile = PsiUtilCore.getVirtualFile(target.psiFile)
    if (virtualFile != null) {
      ProcessingTask(EmptyProgressIndicator()).performFileProcessing(virtualFile)
    }
  }

  fun processFilesUnderProgress(indicator: ProgressIndicator): Boolean {
    indicator.setIndeterminate(false)
    val task = ProcessingTask(indicator)
    return task.process()
  }

  private inner class ProcessingTask(val progressIndicator: ProgressIndicator) {
    private val processors = getAllProcessors()

    /** @return Presentable file path or `null` if the processing of this file should be skipped */
    @RequiresReadLock
    private fun shouldProcessFile(file: VirtualFile): @NlsSafe String? {
      val psiFile = file.findPsiFileSafely()

      val shouldProcess = psiFile != null &&
                          psiFile.isWritable() &&
                          isFormattable(psiFile) &&
                          !isProjectOrWorkspaceFile(file) &&
                          !isGeneratedSourceByAnyFilter(file, myProject) &&
                          isAcceptedByFileFilters(file)

      return if (shouldProcess) {
        calcRelativeToProjectPath(file, myProject)
      } else {
        null
      }
    }

    /** @return `true` if file processing succeeded and processing should continue;
     *          `false` is processing should stop. */
    fun performFileProcessing(file: VirtualFile): Boolean {
      // Using the same groupId for several file-processing actions allows undoing [format + optimize imports + rearrange code + cleanup code] in one shot.
      // Using the same groupId for *all* processed files makes this a single undoable action for all processed files.
      // See docs for #setProcessAllFilesAsSingleUndoRedoCommand(boolean)
      val groupId: String = if (processAllFilesAsSingleUndoStep)
        this@AbstractLayoutCodeProcessor.toString()
      else
        this@AbstractLayoutCodeProcessor.toString() + file.hashCode()
      for (processor in processors) {
        val writeTask: FutureTask<Boolean>?
        if (processor.needsReadActionToPrepareTask()) {
          writeTask = ReadAction.nonBlocking<FutureTask<Boolean>> {
            val psiFile = PsiManager.getInstance(myProject).findFile(file)
            if (psiFile != null) processor.prepareTask(psiFile, processChangedTextOnly) else null
          }
            .executeSynchronously()
        }
        else {
          val psiFile = runReadActionBlocking {
            PsiManager.getInstance(myProject).findFile(file)
          }
          writeTask = if (psiFile != null) processor.prepareTask(psiFile, processChangedTextOnly) else null
        }
        if (writeTask == null) continue

        ProgressIndicatorProvider.checkCanceled()

        withStructuredAsyncDocumentFormattingIfNotEdt {
          processor.runTask(file, commandName, groupId, processAllFilesAsSingleUndoStep) {
            AbstractLayoutCodeProcessorWriteInterceptor.getInstance().runFileWrite(writeTask, myProject, commandName);
          }
        }

        if (!shouldContinue(writeTask, file)) {
          return false
        }
      }
      return true
    }

    private fun shouldContinue(task: FutureTask<Boolean>, file: VirtualFile): Boolean {
      try {
        if (!task.get() || task.isCancelled) {
          return false
        }
      }
      catch (e: Exception) {
        when (e) {
          is InterruptedException, is ExecutionException -> {
            val cause = e.cause
            if (cause is IndexNotReadyException) {
              LOG.warn(cause)
              return true
            }
            LOG.error("Got unexpected exception during formatting $file", e)
          }
          else -> throw e
        }
      }
      return true
    }

    /** @return `true` if file processing succeeded;
     *          `false` if processing failed and was stopped early. */
    fun process(): Boolean {
      progressIndicator.apply {
        isIndeterminate = true
        text = ApplicationBundle.message("bulk.reformat.prepare.progress.text")
        text2 = ""
      }
      val files = runReadActionBlocking { buildFilesIterator() }.collectAll()
      if (files.isEmpty()) return true
      val totalFiles = files.size
      if (!application.isUnitTestMode()) {
        val shouldContinue = invokeAndWaitIfNeeded {
          val status = ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(files)
          !status.hasReadonlyFiles()
        }
        if (!shouldContinue) return false
      }

      progressIndicator.apply {
        isIndeterminate = false
        fraction = 0.0
        text = ApplicationBundle.message("bulk.reformat.process.progress.text")
      }
      for ((index, file) in files.withIndex()) {
        progressIndicator.fraction = index.toDouble() / totalFiles

        val presentableFilePath = runReadActionBlocking { shouldProcessFile(file) } ?: continue
        progressIndicator.text2 = presentableFilePath

        val shouldContinue = DumbService.getInstance(myProject).withAlternativeResolveEnabled {
          performFileProcessing(file)
        }
        if (!shouldContinue) return false
      }
      return true
    }
  }

  @RequiresReadLock
  private fun VirtualFile.findPsiFileSafely(): PsiFile? =
    if (!myProject.isDisposed && isValid) {
      PsiManager.getInstance(myProject).findFile(this)
    }
    else {
      null
    }

  private fun isAcceptedByFileFilters(file: VirtualFile): Boolean = fileFilters.all { it.accept(file) }

  protected fun handleFileTooBigException(logger: Logger, e: FilesTooBigForDiffException, psiFile: PsiFile) {
    logger.info("Error while calculating changed ranges for: " + psiFile.getVirtualFile(), e)
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      val group = NotificationGroupManager.getInstance().getNotificationGroup("Reformat changed text")
      val notification = group.createNotification(
        ApplicationBundle.message("reformat.changed.text.file.too.big.notification.title"),
        ApplicationBundle.message("reformat.changed.text.file.too.big.notification.text", psiFile.getName()),
        NotificationType.INFORMATION
      )
      notification.notify(psiFile.getProject())
    }
  }

  companion object {
    private val LOG = Logger.getInstance(AbstractLayoutCodeProcessor::class.java)

    @JvmStatic
    protected fun emptyTask(): FutureTask<Boolean> {
      return FutureTask<Boolean>(EmptyRunnable.INSTANCE, true)
    }

    private fun isFormattable(psiFile: PsiFile): Boolean {
      val formattingService = FormattingServiceUtil.findService(psiFile, true, true)
      return !(formattingService is CoreFormattingService && LanguageFormatting.INSTANCE.forContext(psiFile) == null)
    }

    private fun withStructuredAsyncDocumentFormattingIfNotEdt(block: () -> Unit) {
      if (!application.isDispatchThread && isWaitForAsyncDocumentFormattingTasks()) {
        runBlockingMaybeCancellable {
          structuredAsyncDocumentFormattingScope {
            coroutineToIndicator { block () }
          }
        }
      }
      else {
        block()
      }
    }

    @JvmStatic
    fun getPresentablePath(project: Project, psiFile: PsiFile): @NlsSafe String {
      val file = psiFile.getVirtualFile()
      return if (file != null) calcRelativeToProjectPath(file, project) else psiFile.getName()
    }

    @JvmStatic
    protected fun getSelectedRanges(selectionModel: SelectionModel): List<TextRange> {
      return if (selectionModel.hasSelection()) {
        listOf(TextRange.create(selectionModel.selectionStart, selectionModel.selectionEnd))
      }
      else {
        emptyList()
      }
    }

    /**
     * Default platform wrapping: a plain [WriteCommandAction]. Exposed so subclasses that want a different
     * behaviour in some cases (e.g. [OptimizeImportsProcessor]) can still fall back to this exact wrapping.
     */
    @JvmStatic
    @ApiStatus.Experimental
    protected fun runUnderDefaultWriteCommandAction(
      project: Project,
      commandName: @NlsContexts.Command String,
      groupId: String,
      processAllFilesAsSingleUndoStep: Boolean,
      writeTask: Runnable,
    ) {
      WriteCommandAction.writeCommandAction(project)
        .withName(commandName)
        .withGroupId(groupId)
        .shouldRecordActionForActiveDocument(processAllFilesAsSingleUndoStep)
        .run<Throwable> { writeTask.run() }
    }

    private fun isWaitForAsyncDocumentFormattingTasks(): Boolean =
      RegistryManager.getInstance().get("code.processor.wait.for.async.formatting.tasks").asBoolean()
  }
}
