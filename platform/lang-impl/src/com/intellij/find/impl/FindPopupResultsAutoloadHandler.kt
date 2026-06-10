// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.find.FindBundle
import com.intellij.find.FindModel
import com.intellij.find.FindUsagesCollector
import com.intellij.find.actions.ShowUsagesAction
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsSafe
import com.intellij.reference.SoftReference
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageInfoAdapter
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import java.util.Vector
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.swing.table.DefaultTableModel

/**
 * Owns the Find-in-Files results-loading chain: one fresh search plus its autoload-on-scroll
 * passes. Not to be confused with [FindAndReplaceExecutor] (the lower-level search engine
 * that emits results into this executor).
 *
 * Responsibilities:
 * - Drive [FindAndReplaceExecutor.findUsages] on demand ([start], [tryLoadMore]).
 * - Cancel the in-flight chain ([cancel]) and reset paging state on a fresh search.
 * - Maintain the [FindPopupSearchState] paging fields, the in-flight progress indicator,
 *   and the "first row of fresh search clears the table" flag.
 * - Decide row dedup + frozen-prefix layout in [handleIncomingRow], invoked by the table
 *   model's `addRow` override.
 *
 * Communicates with [FindPopupPanel] (and any future host) exclusively through the [Host]
 * interface — no Swing imports beyond [DefaultTableModel], which is the only Swing surface
 * the executor touches directly.
 */
@ApiStatus.Internal
internal class FindPopupResultsAutoloadHandler(private val host: Host) {

  /** Session-scoped paging state. */
  private val state = FindPopupSearchState()

  /** In-flight progress indicator (null when no pass is running). Read off-EDT in [Host.onSearchStarted]'s task lambdas. */
  @Volatile private var progress: ProgressIndicatorBase? = null

  /** Identity hash of the currently running progress indicator; 0 when no pass is running. */
  @Volatile private var loadingHash: Int = 0

  /**
   * Row comparator used to insert new rows in sorted order within the tail of the table model.
   * The dedup against the frozen prefix lives in [FindPopupSearchState.containsRowKey] and is order-independent.
   */
  private val rowComparator: Comparator<Vector<FindPopupItem>> = Comparator { v1, v2 ->
    val u1 = v1.first()
    val u2 = v2.first()
    val first = state.firstResultPath
    val u1Path = u1.path
    val u2Path = u2.path
    when {
      u1Path == first && u2Path != first -> -1
      u1Path != first && u2Path == first -> 1
      else -> {
        val pathCmp = u1Path.compareTo(u2Path)
        when {
          pathCmp != 0 -> pathCmp
          u1.line != u2.line -> u1.line.compareTo(u2.line)
          else -> u1.navigationOffset.compareTo(u2.navigationOffset)
        }
      }
    }
  }

  // ===== Public API =====================================================================

  val isInProgress: Boolean
    get() = loadingHash != 0

  /** Start a fresh search session (cancels any running chain and rebuilds the paging state). */
  fun start(checkModel: Boolean) {
    runPass(checkModel, loadMore = false)
  }

  /** Cancel the in-flight pass, if any. The chain ends; no autoload follow-up. */
  fun cancel() {
    progress?.takeIf { !it.isCanceled }?.cancel()
  }

  /** Extend the current chain with one more paging pass, if conditions allow. */
  fun tryLoadMore() {
    if (!host.isDialogVisible) return
    if (state.isExhausted) return
    if (isInProgress) return
    if (host.resultsRowCount == 0) return

    state.beginLoadMorePass(host.resultsRowCount, ShowUsagesAction.getUsagesPageSize())
    runPass(checkModel = false, loadMore = true)
  }

  /**
   * Backend-validator hook: apply a freshly produced [ValidationInfo] without restarting the search.
   */
  fun applyBackendValidationResult(info: ValidationInfo?) {
    host.applyValidationResult(info)
    if (info != null && !host.isValidationOnReplaceComponent(info)) {
      cancel()
      onStop(loadingHash, info.message)
      host.resetUi()
    }
  }

  // ===== Table-model bridge =============================================================

  /**
   * Invoked from the table model's `addRow(Object[])` override. Performs the full row-routing
   * algorithm: first-row clearing, sorted insertion in the tail, dedup against the frozen
   * prefix, and selection of the very first row in an empty table.
   */
  @Suppress("UNCHECKED_CAST")
  fun handleIncomingRow(model: DefaultTableModel, rowData: Array<Any?>) {
    if (state.consumeNeedReset()) {
      // First row of a fresh search — clear any leftover rows. The session state was already
      // reset by runPass; nothing more to reset here.
      model.dataVector.clear()
      model.fireTableDataChanged()
    }
    val v = convertToVector(rowData) as Vector<FindPopupItem>
    val key = rowKey(v.first())
    val data = model.dataVector as Vector<Vector<FindPopupItem>>

    if (data.isEmpty()) {
      // Vector overload bypasses the override and just appends.
      model.addRow(v as Vector<*>)
      host.selectFirstRow()
      state.firstResultPath = v.first().path
      state.recordRowKey(key)
      return
    }

    val frozen = state.frozenRowCount
    if (frozen > 0 && frozen <= data.size) {
      // Load-more pass: rows in [0, frozen) must keep their exact positions.
      // Dedup against the cumulative row-key set rather than against the frozen
      // slice — after the first load-more, the slice is the concatenation of two
      // independently sorted segments and is not globally sorted, so
      // Collections.binarySearch over it returns undefined results and lets real
      // duplicates leak through.
      if (state.containsRowKey(key)) {
        return
      }
      val tail = data.subList(frozen, data.size)
      val pt = tail.binarySearch(v, rowComparator)
      if (pt < 0) {
        model.insertRow(frozen + (-(pt + 1)), v)
        state.recordRowKey(key)
      }
      // The pt >= 0 branch was previously used to refresh tail rows with new presentations.
      // The key set already vetoed dups; hitting that branch would mean the set drifted out
      // of sync — skip silently.
      return
    }

    val p = data.binarySearch(v, rowComparator)
    if (p < 0) {
      val row = -(p + 1)
      model.insertRow(row, v)
      state.recordRowKey(key)
    }
    else {
      model.removeRow(p)
      model.insertRow(p, v)
      if (p == 0) {
        host.selectFirstRow()
      }
    }
  }

  /** Replace-action hook: remove the dedup key when a row is removed from the table outside of a search. */
  fun onRowRemoved(item: FindPopupItem) {
    state.forgetRowKey(rowKey(item))
  }

  // ===== Internal pass orchestration ====================================================

  private fun runPass(checkModel: Boolean, loadMore: Boolean) {
    val helper = host.helper
    val previousModel = host.previousModel
    // A load-more pass must keep searching the same query that produced the frozen prefix.
    // If we sampled `helper.model` here, any text the user has typed since the initial search
    // (but hasn't yet committed via the scheduleResultsUpdate alarm) would silently change the
    // query for the load-more pass, mixing two different searches in the same table.
    val findModel = FindModel()
    if (loadMore && previousModel != null) {
      findModel.copyFrom(previousModel)
    }
    else {
      host.applyCurrentSettingsTo(helper.model)
      findModel.copyFrom(helper.model)
    }
    if (findModel.stringToFind.contains("\n")) {
      findModel.isMultiline = true
    }

    if (checkModel && previousModel != null && findModel.noRestartSearchNeeded(previousModel)) {
      return
    }

    if (!loadMore) {
      state.resetForFreshSearch(ShowUsagesAction.getUsagesPageSize())
      host.setLoadingRowVisible(false)
    }

    // For load-more, preserve the user's current scroll position: the previously visible
    // rows are frozen via state.frozenRowCount, and forcing the selected row (typically
    // row 0 from the initial search) into view would snap the viewport back to the top.
    if (!loadMore && host.isShowing) {
      host.ensureSelectionExists()
    }
    cancel()
    if (!loadMore) {
      // Don't discard the user's pending typed-text alarm during a load-more — let that fresh
      // search supersede the load-more naturally when it fires.
      host.cancelPendingScheduleRequests()
    }

    val validation = host.validate(findModel)
    host.applyValidationResult(validation)

    val progressIndicatorWhenSearchStarted = object : ProgressIndicatorBase() {
      override fun stop() {
        super.stop()
        if (FindKey.isEnabled) return
        val hashCode = System.identityHashCode(this)
        searchStoppedProcessing(hashCode)
      }
    }
    progress = progressIndicatorWhenSearchStarted
    val hash = System.identityHashCode(progressIndicatorWhenSearchStarted)

    // Use previously shown usage files as hint for faster search and better usage-preview
    // performance if the pattern length increased.
    val previousUsages = getPreviousUsages(loadMore)
    if (!loadMore) {
      // Preserve the frozen-prefix model as `previousModel` so a follow-up load-more keeps
      // searching the same query.
      host.rememberPreviousModel(helper.model.clone())
    }

    onStart(hash, loadMore)
    if (validation != null && !host.isValidationOnReplaceComponent(validation)) {
      onStop(hash, validation.message)
      host.resetUi()
      return
    }

    host.refreshTableRenderer()

    // Reconfigure the shared UsageViewPresentation for this query — without this, the
    // presentation can carry stale scope/tab descriptors from a previous search.
    FindInProjectUtil.setupViewPresentation(host.usageViewPresentation, findModel)

    val resultsCount = AtomicInteger()
    val startTime = AtomicLong()
    val maxUsages = state.currentMaxUsages
    val modality = ModalityState.current()
    val project = host.project
    val onFinishCalled = AtomicBoolean(false)

    // Matches already counted by previous passes. Each pass re-scans the query from the start,
    // so any match whose raw-emission ordinal is below this was already counted.
    val countedBeforePass = state.cumulativeUsageCount()

    if (loadMore) {
      host.setLoadingRowVisible(true)
    }

    ProgressManager.getInstance().runProcessWithProgressAsynchronously(
      object : Task.Backgroundable(project, FindBundle.message("find.usages.progress.title")) {
        override fun run(indicator: ProgressIndicator) {
          startTime.set(System.currentTimeMillis())
          val timeToFirstResult = AtomicLong(-1)
          val scope = if (FindKey.isEnabled) null
          else ReadAction.nonBlocking<com.intellij.psi.search.GlobalSearchScope> {
            FindInProjectUtil.getGlobalSearchScope(project, findModel)
          }.wrapProgress(indicator).executeSynchronously()
          val processPresentation = FindInProjectUtil.setupProcessPresentation(host.usageViewPresentation)
          val recentItemRef = ThreadLocal<java.lang.ref.Reference<FindPopupItem>>()

          FindAndReplaceExecutor.getInstance().findUsages(
            project,
            progressIndicatorWhenSearchStarted,
            processPresentation,
            findModel,
            previousUsages,
            host.resultsRowCount > 0,
            host.disposable,
            { adapter ->
              ApplicationManager.getApplication().invokeLater {
                host.schedulePreviewUpdateIfSelected(adapter)
              }
            },
            { usage ->
              if (isCancelled()) {
                onStop(hash)
                return@findUsages false
              }
              val ordinal = resultsCount.getAndIncrement()
              if (ordinal >= maxUsages) {
                onStop(hash)
                return@findUsages false
              }

              val recentItem = SoftReference.dereference(recentItemRef.get())
              val newItem: FindPopupItem
              val merged = !helper.isReplaceState && recentItem != null && recentItem.usage.merge(usage)
              if (!merged) {
                if (usage is UsageInfo2UsageAdapter && !FindKey.isEnabled) {
                  usage.updateCachedPresentation()
                }
                val usagePresentation = UsagePresentationProvider.getPresentation(usage, project, scope)
                newItem = FindPopupItem(usage, usagePresentation)
              }
              else {
                val recentItemUsage = recentItem.usage
                if (recentItemUsage is UsageInfo2UsageAdapter && !FindKey.isEnabled) {
                  recentItemUsage.updateCachedPresentation()
                }
                val recentUsagePresentation = UsagePresentationProvider.getPresentation(recentItemUsage, project, scope)
                newItem = recentItem.withPresentation(recentUsagePresentation)
              }
              recentItemRef.set(WeakReference(newItem))

              if (!loadMore && timeToFirstResult.get() == -1L) {
                val firstResultTime = System.currentTimeMillis() - startTime.get()
                timeToFirstResult.set(firstResultTime)
                FindUsagesCollector.recordFirstResultTime(firstResultTime)
              }

              ApplicationManager.getApplication().invokeLater({
                if (isCancelled()) {
                  onStop(hash)
                  return@invokeLater
                }
                host.appendIncomingRow(newItem)
                state.recordFilePath(newItem.path)
                if (ordinal >= countedBeforePass) {
                  state.incrementUsageCount()
                }
                val displayOccurrences = state.cumulativeUsageCount()
                val displayFiles = state.cumulativeFileCount()
                if (displayOccurrences > 0) {
                  host.updateInfoLabel(displayOccurrences, displayFiles, loadingMore = false)
                }
                else {
                  host.updateInfoLabel(0, 0, loadingMore = false)
                }
              }, modality)
              true
            },
            {
              // Use the captured indicator-identity hash, not the mutable field: if `stop()`
              // already ran and zeroed `loadingHash`, the guard in `onStop` would otherwise
              // see `0 != 0` and let `host.onPassStopped` fire a second time.
              searchStoppedProcessing(hash)
              if (onFinishCalled.compareAndSet(false, true)) {
                onFinish(progressIndicatorWhenSearchStarted, resultsCount, maxUsages, loadMore, startTime, modality)
              }
              null
            },
            maxUsages,
          )
        }

        override fun onCancel() {
          if (host.isShowing &&
              progressIndicatorWhenSearchStarted === progress &&
              !progressIndicatorWhenSearchStarted.isCanceled) {
            host.scheduleResultsUpdate()
          }
        }

        private fun isCancelled(): Boolean {
          return progressIndicatorWhenSearchStarted !== progress || progressIndicatorWhenSearchStarted.isCanceled
        }

        override fun onFinished() {
          if (FindKey.isEnabled) return
          if (onFinishCalled.compareAndSet(false, true)) {
            onFinish(progressIndicatorWhenSearchStarted, resultsCount, maxUsages, loadMore, startTime, modality)
          }
        }
      },
      progressIndicatorWhenSearchStarted,
    )
  }

  private fun onFinish(
    progressIndicatorWhenSearchStarted: ProgressIndicatorBase,
    resultsCount: AtomicInteger,
    maxUsages: Int,
    loadMore: Boolean,
    startTime: AtomicLong,
    modality: ModalityState,
  ) {
    val hash = System.identityHashCode(progressIndicatorWhenSearchStarted)
    ApplicationManager.getApplication().invokeLater({
      val isEmpty = resultsCount.get() == 0

      if (!isCancelled(progressIndicatorWhenSearchStarted)) {
        if (isEmpty && !loadMore) {
          host.showEmptyText(FindBundle.message("message.nothingFound"), isFinish = true)
        }

        // Update paging state after the search finishes naturally.
        val occurrences = resultsCount.get()
        val reachedCap = occurrences >= maxUsages
        state.isExhausted = !reachedCap

        // Defer the autoload / finalization decision so the table layout (scrollbar bounds,
        // viewport extent) has had a chance to stabilize after the last row was added.
        ApplicationManager.getApplication().invokeLater({
          if (isCancelled(progressIndicatorWhenSearchStarted)) return@invokeLater

          val autoLoadMore = !state.isExhausted &&
                             loadingHash == 0 &&
                             (host.isContentFullyVisible || host.isUserAtBottom) &&
                             host.resultsRowCount > 0

          val displayOccurrences = state.cumulativeUsageCount()
          val displayFiles = state.cumulativeFileCount()
          if (autoLoadMore) {
            // Guarantee the counter reflects this pass before chaining into the next.
            if (displayOccurrences > 0) {
              host.updateInfoLabel(displayOccurrences, displayFiles, loadingMore = true)
            }
            tryLoadMore()
          }
          else {
            host.setLoadingRowVisible(false)
            if (displayOccurrences > 0) {
              host.updateInfoLabel(displayOccurrences, displayFiles, loadingMore = !state.isExhausted)
            }
          }
        }, modality)
      }
      if (!loadMore) {
        // Report search finished only for the initial request
        FindUsagesCollector.recordSearchFinished(
          System.currentTimeMillis() - startTime.get(),
          resultsCount.get(),
          maxUsages,
        )
      }
      onStop(hash)

      if (FindKey.isEnabled) {
        host.helper.onSearchFinish(if (isEmpty) 0 else host.resultsRowCount)
      }
    }, modality)
  }

  private fun isCancelled(progressIndicatorWhenSearchStarted: ProgressIndicatorBase): Boolean {
    return progressIndicatorWhenSearchStarted !== progress || progressIndicatorWhenSearchStarted.isCanceled
  }

  private fun onStart(hash: Int, loadMore: Boolean) {
    // `needReset` is already set by `state.resetForFreshSearch(...)` in `runPass` for the
    // fresh-search path; load-more passes intentionally leave it untouched.
    loadingHash = hash
    host.onSearchStarted(loadMore)
  }

  private fun onStop(hash: Int) {
    onStop(hash, FindBundle.message("message.nothingFound"))
  }

  private fun onStop(hash: Int, @NlsSafe message: String) {
    if (hash != loadingHash) return
    UIUtil.invokeLaterIfNeeded {
      if (hash != loadingHash) return@invokeLaterIfNeeded
      loadingHash = 0
      host.onPassStopped(message)
    }
  }

  private fun searchStoppedProcessing(hashCode: Int) {
    onStop(hashCode)
    ApplicationManager.getApplication().invokeLater {
      // Nothing is found, let's clear previous results.
      if (state.consumeNeedReset()) {
        host.resetUi()
      }
    }
  }

  private fun getPreviousUsages(loadMore: Boolean): Set<UsageInfoAdapter> {
    val previousUsages: MutableSet<UsageInfoAdapter> = LinkedHashSet()
    val prev = host.previousModel
    val usePreviousAsHint = loadMore ||
                            (prev != null &&
                             prev.stringToFind.length < host.helper.model.stringToFind.length)
    if (usePreviousAsHint) {
      val results = host.getItems()
      results.forEach { item -> previousUsages.add(item.usage) }
    }
    return previousUsages
  }

  // ===== Helpers ========================================================================

  private fun convertToVector(rowData: Array<Any?>): Vector<Any?> {
    val v = Vector<Any?>(rowData.size)
    rowData.forEach { v.addElement(it) }
    return v
  }

  /**
   * Stable identity for a [FindPopupItem] used by the dedup set. Two emissions with the same
   * `path|line|navigationOffset` represent the same logical match and should produce only one row.
   */
  private fun rowKey(item: FindPopupItem): String =
    "${item.path}|${item.line}|${item.navigationOffset}"

  // ===== Host interface =================================================================

  /**
   * Coupling point between [FindPopupResultsAutoloadHandler] and its UI host (today [FindPopupPanel]).
   * The executor reads via the property getters and pushes UI changes through the methods.
   */
  interface Host {
    // -- Context --
    val project: Project
    val helper: FindUIHelper
    val disposable: Disposable

    // -- Read-only state --
    val resultsRowCount: Int
    val isShowing: Boolean
    val isDialogVisible: Boolean
    val isContentFullyVisible: Boolean
    val isUserAtBottom: Boolean

    // -- Model bridging --
    val previousModel: FindModel?
    fun rememberPreviousModel(model: FindModel)
    fun applyCurrentSettingsTo(model: FindModel)
    fun validate(model: FindModel): ValidationInfo?
    fun applyValidationResult(info: ValidationInfo?)
    fun isValidationOnReplaceComponent(info: ValidationInfo): Boolean
    fun refreshTableRenderer()
    val usageViewPresentation: com.intellij.usages.UsageViewPresentation

    // -- Coarse lifecycle events --
    fun onSearchStarted(loadMore: Boolean)
    fun onPassStopped(@NlsSafe message: String)

    // -- Result routing --
    fun appendIncomingRow(item: FindPopupItem)
    fun selectFirstRow()
    fun schedulePreviewUpdateIfSelected(adapter: UsageInfoAdapter)
    fun getItems(): List<FindPopupItem>

    // -- Label / empty text --
    fun updateInfoLabel(occurrences: Int, files: Int, loadingMore: Boolean)
    fun showEmptyText(@NlsSafe message: String, isFinish: Boolean)

    // -- Reset / loading-row visibility --
    fun resetUi()
    fun setLoadingRowVisible(visible: Boolean)

    // -- Debouncer / scheduler bridges --
    fun cancelPendingScheduleRequests()
    fun scheduleResultsUpdate()
    fun ensureSelectionExists()
  }
}
