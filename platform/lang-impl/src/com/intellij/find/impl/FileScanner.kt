// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindBundle
import com.intellij.find.FindModel
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.TooManyUsagesStatus
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isProjectOrWorkspaceFile
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.DiskQueryRelay
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isTooLarge
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.AstLoadingFilter
import com.intellij.usageView.UsageInfo
import com.intellij.usages.FindUsagesProcessPresentation
import com.intellij.usages.impl.UsageViewManagerImpl
import com.intellij.util.Processor
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.text.StringSearcher
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

private val LOG = logger<FileScanner>()

/** Total size of processed files before asking the user 'too many files, should we continue?' */
private const val TOTAL_FILES_SIZE_LIMIT_BEFORE_ASKING = 70 * 1024 * 1024 // megabytes.

/** The progress text formatting is too costly for every file, so the text updates on a time budget. */
private val PROGRESS_TEXT_UPDATE_INTERVAL_NS = TimeUnit.MILLISECONDS.toNanos(100)

/**
 * Checks one candidate file of a "Find in Path" search for the pattern and delivers its usages to the usage processor.
 * Also does the counting (occurrences found, total size of the files with usages) and the progress text updates.
 * One instance serves the whole search; [process] is called from many workers, each under its read action and under the
 * ClientId of the search, which [FindWorkQueue] sets on its workers.
 * The scan reads the text, never the AST: [FindInProjectUtil.processUsagesInFile] matches the pattern under
 * [AstLoadingFilter.disallowTreeLoading]; the check of a `LocalSearchScope` and the usage processor run outside the guard,
 * as a scope element may need its tree for its range and the usage processor may need the tree of any file.
 * A restarted work item (a write action interrupted its read action) scans its file again; the usages delivered before
 * are not delivered twice: they are recorded on the item ([WorkItem.Candidate.delivered]).
 * The disk-loaded text of a scanned file goes to [SearcherSet.fileScanned].
 * The PSI of a file comes from the project that holds the file, see [psiManagerFor], so a usage belongs to that project.
 */
internal class FileScanner(
  private val findModel: FindModel,
  private val project: Project,
  private val searchers: SearcherSet,
  private val progressIndicator: ProgressIndicator,
  private val processPresentation: FindUsagesProcessPresentation,
  private val usageConsumer: Processor<in UsageInfo>,
  private val searchStartedAtNs: Long,
  /** The PSI manager of the project that holds a file, see `FindInProjectTask.psiManagerFor`. */
  private val psiManagerFor: (VirtualFile) -> PsiManager,
) {
  private val stringSearcher: StringSearcher? =
    if (findModel.isRegularExpressions || StringUtil.isEmpty(findModel.stringToFind)) null
    else StringSearcher(findModel.stringToFind, findModel.isCaseSensitive, true)

  /** The usages found so far; read only by the progress text, so a striped counter. */
  private val occurrenceCount = LongAdder()

  /** Set once by the first usage, only when the debug log is on; two workers may both log it, which is harmless. */
  @Volatile
  private var reportedFirst = false

  /** The files skipped because they are too large. */
  val largeFiles: MutableSet<VirtualFile> = Collections.synchronizedSet(HashSet())

  /** The total size of the files with usages; the result of the add drives the 70 MB prompt at once. */
  private val totalFilesSize = AtomicLong()

  /**
   * A racy check-then-write, no CAS per file: two workers that pass the check inside one interval both set the progress text,
   * which is harmless.
   */
  @Volatile
  private var lastProgressTextUpdateNs = 0L

  /**
   * Looks up the search pattern in [virtualFile], the file that [item] claimed, and delivers all the usages found (if any)
   * to the usage processor. A rerun of the same item skips the usages recorded in [WorkItem.Candidate.delivered].
   *
   * @return false if the usage processor returns false for any of the occurrences found, true otherwise
   */
  @RequiresReadLock
  fun process(item: WorkItem.Candidate, virtualFile: VirtualFile): Boolean {
    if (!virtualFile.isValid) return true

    val fileLength = UsageViewManagerImpl.getFileLength(virtualFile)
    if (fileLength == -1L) return true

    val skipProjectFile = isProjectOrWorkspaceFile(virtualFile) && !findModel.isSearchInProjectFiles
    if (skipProjectFile && !Registry.`is`("find.search.in.project.files")) return true

    if (isTooLarge(virtualFile)) return true

    progressIndicator.checkCanceled()
    val nowNs = System.nanoTime()
    if (nowNs - lastProgressTextUpdateNs >= PROGRESS_TEXT_UPDATE_INTERVAL_NS) {
      lastProgressTextUpdateNs = nowNs
      progressIndicator.text = FindBundle.message("find.searching.for.string.in.file.progress",
                                                  findModel.stringToFind, virtualFile.presentableUrl)
      progressIndicator.text2 = FindBundle.message("find.searching.for.string.in.file.occurrences.progress", occurrenceCount.sum())
    }

    //the text scan is much cheaper than the PSI lookup in findFile, so it runs first;
    // an admitted file is not binary (CandidateFilter maps a binary file to its source), the binary check is a guard:
    if (stringSearcher != null && !virtualFile.fileType.isBinary && !containsPattern(virtualFile, stringSearcher)) {
      return true
    }

    val pair = findFile(psiManagerFor(virtualFile), virtualFile) ?: return true

    val psiFile = pair.first
    val sourceVirtualFile = pair.second

    if (sourceVirtualFile != virtualFile && isTooLarge(sourceVirtualFile)) return true
    if (stringSearcher != null && sourceVirtualFile != virtualFile && !containsPattern(sourceVirtualFile, stringSearcher)) {
      return true
    }
    //the callback runs synchronously on this thread, so a plain local flag is enough:
    var projectFileUsagesFound = false
    //the matching runs under the AST guard, the callback outside it (a usage processor may need the tree of any file):
    val processedSuccessfully = FindInProjectUtil.processUsagesInFile(psiFile, sourceVirtualFile, findModel) { info ->
      if (skipProjectFile) {
        projectFileUsagesFound = true
        return@processUsagesInFile true
      }
      if (!reportedFirst && LOG.isDebugEnabled) {
        reportedFirst = true
        LOG.debug("First usage found in ${TimeoutUtil.getDurationMillis(searchStartedAtNs)} ms")
      }
      //one item runs on one worker at a time, and a rerun runs the same item object, so a plain set on the item is enough:
      val delivered = item.delivered ?: HashSet<UsageInfo>().also { item.delivered = it }
      if (delivered.contains(info)) {
        return@processUsagesInFile true
      }
      val success = usageConsumer.process(info)
      delivered.add(info)
      success
    }
    if (!processedSuccessfully) {
      return false
    }

    if (projectFileUsagesFound) {
      processPresentation.projectFileUsagesFound {
        val model = findModel.clone()
        model.isSearchInProjectFiles = true
        FindInProjectManager.getInstance(project).startFindInProject(model)
      }
      return true
    }

    //the total changes only with a file with usages; the worker that crossed the limit shows the prompt and pauses itself,
    // and a stop cancels the indicator that every worker checks:
    val usageCount = item.delivered?.size ?: 0
    if (usageCount == 0) return true
    occurrenceCount.add(usageCount.toLong())
    if (totalFilesSize.addAndGet(fileLength) > TOTAL_FILES_SIZE_LIMIT_BEFORE_ASKING) {
      val tooManyUsagesStatus = TooManyUsagesStatus.getFrom(progressIndicator)
      if (tooManyUsagesStatus.switchTooManyUsagesStatus()) {
        UsageViewManagerImpl.showTooManyUsagesWarningLater(project, tooManyUsagesStatus, progressIndicator, null, {
          FindBundle.message("find.excessive.total.size.prompt",
                             UsageViewManagerImpl.presentableSize(totalFilesSize.get()),
                             ApplicationNamesInfo.getInstance().productName)
        }, null)
      }
      tooManyUsagesStatus.pauseProcessingIfTooManyUsages()
      progressIndicator.checkCanceled()
    }
    return true
  }

  /**
   * @return true when the file is too large to scan; it is recorded in [largeFiles], so the user learns it was skipped.
   * This check is the size limit of the scan: [containsPattern] then loads the whole text, as a cut text could miss usages.
   */
  private fun isTooLarge(file: VirtualFile): Boolean {
    if (!file.isTooLarge()) return false
    largeFiles.add(file)
    return true
  }

  /** @return true when the file text contains the pattern. The cached document text wins over the disk content. */
  private fun containsPattern(file: VirtualFile, searcher: StringSearcher): Boolean {
    val document = FileDocumentManager.getInstance().getCachedDocument(file)
    val text = if (document != null) {
      document.charsSequence
    }
    else {
      DiskQueryRelay.compute<CharSequence, RuntimeException> { LoadTextUtil.loadText(file, -1) }
        .also { searchers.fileScanned(file, it) }
    }
    return text.isNotEmpty() && searcher.scan(text) >= 0
  }

  companion object {
    /** @return [psiFile, sourceFile] corresponding to the virtualFile; null when either is binary */
    @JvmStatic
    @RequiresReadLock
    fun findFile(psiManager: PsiManager, virtualFile: VirtualFile): Pair.NonNull<PsiFile, VirtualFile>? {
      var psiFile = psiManager.findFile(virtualFile)
      if (psiFile != null) {
        val sourceFile = psiFile.navigationElement
        if (sourceFile is PsiFile) psiFile = sourceFile
        if (psiFile.fileType.isBinary) {
          psiFile = null
        }
      }
      val sourceVirtualFile = PsiUtilCore.getVirtualFile(psiFile)
      if (psiFile == null || psiFile.fileType.isBinary
          || sourceVirtualFile == null || sourceVirtualFile.fileType.isBinary) {
        return null
      }

      return Pair.createNonNull(psiFile, sourceVirtualFile)
    }
  }
}
