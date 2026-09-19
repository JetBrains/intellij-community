// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileChooser.universal

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.util.progress.createProgressPipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds

private val LOG = logger<NioPathCompletion>()

/**
 * Returns the [text] up to the start of its last path element, or null when the text is not an absolute path.
 */
internal fun directoryTextOf(text: String, pathParser: (String) -> Path?): String? {
  // The probe name makes the parser report the typed name, and an empty name after a trailing separator.
  val probe = "x"
  val name = pathParser(text + probe)?.takeIf { it.isAbsolute }?.fileName?.toString() ?: return null
  if (!name.endsWith(probe)) return null
  val directoryText = text.dropLast(name.length - probe.length)
  return directoryText.takeIf { it.isNotEmpty() }
}

/** Returns true when the [text] starts with `~`, that is, it is relative to the home directory. */
internal fun isHomeRelative(text: String): Boolean = text.startsWith('~')

/**
 * The completion of a path text with the children of the typed directory.
 * The completion does not access the text field. The field gives it the completion base, that is the text before the caret.
 * The completion reports its [State] to [onStateChanged], and returns an [Edit] for a chosen child.
 * It reports the failure of the [prepareCompletion] to [onPreparationFailed], and null when a new preparation starts.
 * After a failure, the suggestions hide, and the edits do not start the completion until the completion shortcut.
 * It lists the children off the EDT. The other calls, the [onStateChanged], and the [onPreparationFailed] run on the EDT.
 * See [NioPathTextField] for the other parameters.
 */
internal class NioPathCompletion(
  private val scope: CoroutineScope,
  private val chooseFiles: Boolean,
  private val chooseArchives: Boolean,
  private val showHidden: () -> Boolean,
  private val pathParser: (String) -> Path?,
  private val prepareCompletion: suspend () -> Unit,
  private val homeDirectory: suspend () -> String?,
  private val onStateChanged: (State) -> Unit,
  private val onPreparationFailed: (Throwable?) -> Unit = {},
) {

  /** The event that starts a completion update. */
  enum class Trigger {
    /** An edit or a chosen directory. */
    TYPING,

    /** The completion shortcut. The directory is listed again at once. */
    SHORTCUT,
  }

  /** What the completion popup shows. */
  sealed interface State {
    data object Hidden : State

    /** The listing runs. The [progressText] is the progress that the listing reports, or null for the default loading text. */
    data class Loading(val progressText: @Nls String? = null) : State

    /** No child matches. The popup shows the no-results text. */
    data object NoResults : State

    /** The children of the directory with the [directoryText] that match the typed prefix. */
    data class Suggestions(val directoryText: String, val matching: List<NioFileChooserUtil.DirectoryEntry>) : State
  }

  /** A text edit for a chosen child. The text from the [offset] to the end becomes the [text]. */
  data class Edit(val offset: Int, val text: String, val descends: Boolean)

  /** The children of the directory with the [directoryText], as the user typed it. The text can start with `~`. */
  private class DirectoryListing(
    val directoryText: String,
    val showHidden: Boolean,
    val children: List<NioFileChooserUtil.DirectoryEntry>,
  )

  /** A running listing of the directory with the [directoryText]. */
  private class ListingJob(val directoryText: String, val job: Job)

  /** The resolved home directory. The [text] is null when the home is unknown. */
  private class Home(val text: String?)

  private var state: State = State.Hidden

  // The completion base of the last update.
  private var base = ""

  // Each update that moves the base out of the listed directory cancels the job.
  // So when the job ends, the base is still in its directory. The job clears the field at its end.
  private var listingJob: ListingJob? = null

  // The home directory for the `~` expansion. It is null until the completion resolves it.
  // The home does not change in a session, so the completion resolves it once.
  private var home: Home? = null
  // An update with a base out of the home cancels the job. The job clears the field at its end.
  private var homeJob: Job? = null

  // The last listing. It serves the prefix filtering.
  private var listing: DirectoryListing? = null

  // Cleared by [dismiss] and by a failed preparation, so an edit does not show the suggestions again.
  // The completion shortcut sets it again.
  private var autoPopup = true

  /**
   * Returns the [base] up to the start of its last path element, or null when the base is not a directory path.
   * A base with an expanded `~` has a directory text only when the home directory is resolved.
   */
  fun directoryTextOf(base: String): String? {
    if (!isHomeRelative(base)) return directoryTextOf(base, pathParser)
    val homeText = home?.text ?: return null
    val expandedText = directoryTextOf(expandHome(base, homeText), pathParser) ?: return null
    // Without a separator after the `~`, the name joins the home name. So `~src/` and `~` are not in the home.
    val homePath = pathParser(homeText) ?: return null
    if (pathParser(expandedText)?.startsWith(homePath) != true) return null
    return base.take(expandedText.length - homeText.length + 1)
  }

  /** Returns the [text] with the [homeText] in place of the leading `~`. */
  private fun expandHome(text: String, homeText: String): String = homeText + text.removePrefix("~")

  /**
   * Filters the [listing] with the typed prefix when the listing covers the directory of the [base].
   * Otherwise lists the directory. The state stays until the new listing is ready.
   * A base with `~` first resolves the home directory, and then updates again.
   */
  fun update(base: String, trigger: Trigger) {
    this.base = base
    if (trigger == Trigger.SHORTCUT) autoPopup = true
    refresh(trigger)
  }

  private fun refresh(trigger: Trigger) {
    if (!autoPopup) return
    if (isHomeRelative(base) && home == null) {
      resolveHome(trigger)
      return
    }
    cancelHome()
    val directoryText = directoryTextOf(base)
    if (directoryText == null) {
      cancel()
      return
    }
    if (listingJob?.directoryText != directoryText) cancelListing()
    val cached = listing?.takeIf { it.directoryText == directoryText && it.showHidden == showHidden() }
    if (cached != null) showMatching(directoryText, cached.children)
    // The shortcut lists the directory again, so the popup shows the changes on the disk.
    // A running listing of this directory shows the matches for the base at its end.
    if (trigger == Trigger.SHORTCUT || (cached == null && listingJob == null)) startListing(directoryText)
  }

  /** Cancels the running listing and hides the suggestions. The listing stays. */
  fun cancel() {
    cancelListing()
    cancelHome()
    setState(State.Hidden)
  }

  /** Cancels the completion, and stops the next edits from showing the suggestions until the completion shortcut. */
  fun dismiss() {
    autoPopup = false
    cancel()
  }

  /** Returns true when the shown suggestions belong to the directory of the [base]. */
  fun hasSuggestionsFor(base: String): Boolean {
    val suggestions = state as? State.Suggestions ?: return false
    return directoryTextOf(base) == suggestions.directoryText
  }

  /**
   * Returns the edit that replaces the text after the listed directory with the name of the [chosen] child.
   * A chosen directory gets the separator, so the completion continues inside it. A chosen file ends the completion.
   * Returns null when the [base] is not in the listed directory.
   */
  fun complete(base: String, chosen: NioFileChooserUtil.DirectoryEntry): Edit? {
    val current = listing ?: return null
    val directoryText = current.directoryText
    if (directoryTextOf(base) != directoryText) return null
    val descends = chosen.isDirectory
    if (!descends) cancel()
    return Edit(directoryText.length, if (descends) chosen.name + directoryText.last() else chosen.name, descends)
  }

  private fun setState(newState: State) {
    state = newState
    onStateChanged(newState)
  }

  private fun startListing(directoryText: String) {
    cancelListing()
    val showHidden = showHidden()
    // The directory text of a base with `~` exists only when the home is known.
    val pathText = if (isHomeRelative(directoryText)) expandHome(directoryText, checkNotNull(home?.text)) else directoryText
    onPreparationFailed(null)
    // The results apply in the modality of the update, so the popup does not change under a nested modal dialog.
    val job = scope.launch(ModalityState.current().asContextElement()) {
      listChildren(directoryText, pathText, showHidden)
    }
    listingJob = ListingJob(directoryText, job)
  }

  /** Resolves the home directory off the EDT, and then updates the completion for the current base. */
  private fun resolveHome(trigger: Trigger) {
    if (homeJob != null) return
    cancelListing()
    onPreparationFailed(null)
    homeJob = scope.launch(ModalityState.current().asContextElement()) {
      var preparationError: Throwable? = null
      val resolved = try {
        withLoadingState {
          withContext(Dispatchers.IO) {
            preparationError = prepare()
            if (preparationError != null) null else homeDirectory()
          }
        }
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        LOG.debug("Cannot get the home directory", e)
        null
      }
      withContext(Dispatchers.EDT) {
        homeJob = null
        val failure = preparationError
        if (failure != null) {
          // A failed preparation does not tell the home, so the next update resolves it again.
          reportPreparationFailure(failure)
          return@withContext
        }
        home = Home(resolved)
        if (resolved != null) refresh(trigger) else setState(State.NoResults)
      }
    }
  }

  /** Hides the suggestions and reports the [error]. Only the completion shortcut starts the completion again. */
  private fun reportPreparationFailure(error: Throwable) {
    autoPopup = false
    setState(State.Hidden)
    onPreparationFailed(error)
  }

  /** Runs the [prepareCompletion]. Returns its failure, or null when it succeeds. */
  private suspend fun prepare(): Throwable? {
    try {
      prepareCompletion()
      return null
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.debug("Cannot prepare the completion", e)
      return e
    }
  }

  private fun cancelHome() {
    homeJob?.cancel()
    homeJob = null
  }

  /**
   * Runs the [block], and shows the loading text after [LOADING_POPUP_DELAY] when [showLoading] returns true then.
   * The progress text that the [block] reports, for example the IJent provisioning, shows at once in the loading state.
   */
  private suspend fun <T> withLoadingState(showLoading: () -> Boolean = { true }, block: suspend () -> T): T = coroutineScope {
    val progressPipe = createProgressPipe()
    val loadingJob = launch(Dispatchers.EDT) {
      var delayPassed = false
      var progressText: @Nls String? = null
      fun showLoadingState() {
        if (progressText != null || (delayPassed && showLoading())) setState(State.Loading(progressText))
      }
      launch {
        delay(LOADING_POPUP_DELAY)
        delayPassed = true
        showLoadingState()
      }
      progressPipe.progressUpdates().collect { progress ->
        if (progress.text == progressText) return@collect
        progressText = progress.text
        showLoadingState()
      }
    }
    try {
      progressPipe.collectProgressUpdates { block() }
    }
    finally {
      loadingJob.cancel()
    }
  }

  private fun cancelListing() {
    listingJob?.job?.cancel()
    listingJob = null
  }

  /**
   * Lists the children off the EDT. The cancellation of the job drops the result.
   * The [pathText] is the [directoryText] with the home directory in place of the `~`.
   */
  private suspend fun listChildren(directoryText: String, pathText: String, showHidden: Boolean) {
    // A new listing of the shown directory keeps the current suggestions.
    val showLoading = { (state as? State.Suggestions)?.directoryText != directoryText }
    var preparationError: Throwable? = null
    val listed = try {
      withLoadingState(showLoading) {
        // The attributes and the icons touch the file system, so keep them off the EDT.
        withContext(Dispatchers.IO) {
          preparationError = prepare()
          if (preparationError != null) return@withContext null
          val directory = pathParser(pathText) ?: return@withContext null
          val children = NioFileChooserUtil.safeGetChildren(directory, showHidden, showFiles = chooseFiles, showArchives = chooseArchives)
          DirectoryListing(directoryText, showHidden, children)
        }
      }
    }
    catch (e: Throwable) {
      rethrowControlFlowException(e)
      LOG.debug("Cannot list $directoryText", e)
      null
    }
    withContext(Dispatchers.EDT) {
      listingJob = null
      val failure = preparationError
      if (failure != null) {
        reportPreparationFailure(failure)
        return@withContext
      }
      listing = listed
      showMatching(directoryText, listed?.children.orEmpty())
    }
  }

  /**
   * Shows the [children] that match the typed prefix. The base must be in the directory with the [directoryText].
   * When none match, the popup shows the no-results text.
   */
  private fun showMatching(directoryText: String, children: List<NioFileChooserUtil.DirectoryEntry>) {
    val prefix = base.substring(directoryText.length)
    val matching = children.filter { it.name.startsWith(prefix, ignoreCase = true) }
    setState(if (matching.isNotEmpty()) State.Suggestions(directoryText, matching) else State.NoResults)
  }

  private companion object {
    val LOADING_POPUP_DELAY = 300.milliseconds
  }
}
