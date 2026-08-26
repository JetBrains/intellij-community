// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr

import com.intellij.ide.IdeBundle
import com.intellij.ide.gdpr.localConsents.LocalConsentOptions
import com.intellij.openapi.application.EDT
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.AppUIUtil.loadConsentsForEditing
import com.intellij.ui.AppUIUtil.loadLocalConsentsAsConsentsForEditing
import com.intellij.ui.AppUIUtil.saveConsents
import com.intellij.ui.AppUIUtil.saveConsentsAsLocalConsents
import com.intellij.ui.AppUIUtil.showConsentsDialog
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.function.Function
import java.util.function.Predicate

/**
 * The consents that the data sharing UI shows and edits.
 *
 * [allConsents] is the editable state. [actualConsents] and [localConsents] give the two parts of it.
 * The split needs the identifiers of the local consents, and a read of the local consents is an IO operation.
 * Therefore, the caller reads the identifiers one time on a background thread, and the UI code splits on the EDT.
 */
@ApiStatus.Internal
class ConsentsState(
  val allConsents: MutableList<Consent>,
  private val localConsentIds: Set<String>,
) {
  /** Creates a state that holds no local consent. */
  constructor(consents: List<Consent>) : this(consents.toMutableList(), emptySet())

  val actualConsents: List<Consent>
    get() = allConsents.filter { it.id !in localConsentIds }

  val localConsents: List<Consent>
    get() = allConsents.filter { it.id in localConsentIds }
}

/**
 * Reads every consent and every local consent from the disk.
 *
 * A read of the consents is an IO operation, so a background thread is required.
 */
@RequiresBackgroundThread
private fun loadAllConsentsForEditing(): ConsentsState {
  val consents = ArrayList<Consent>(loadConsentsForEditing())
  consents.addAll(loadLocalConsentsAsConsentsForEditing())
  consents.sortWith(Comparator.comparing(Function { it.id }))
  return ConsentsState(consents, loadLocalConsentIds())
}

/**
 * Shows the data sharing dialog for every consent of the application.
 *
 * It reads and writes the consents in a modal progress, so the EDT is not blocked.
 * It returns `true` if the user made a choice.
 */
@ApiStatus.Internal
@RequiresEdt
@RequiresBlockingContext
fun showDataSharingOptionsDialog(): Boolean {
  val consentsState = runWithModalProgressBlocking(
    ModalTaskOwner.guess(),
    IdeBundle.message("consent.configurable.loading.progress.title"),
  ) {
    ConsentsState(loadConsentsForEditing())
  }

  val result = showConsentsDialog(consentsState) ?: return false

  saveInModalProgress {
    saveConsents(result)
  }
  return true
}

/**
 * Shows the data sharing dialog if the consents that match [filter] still need a choice of the user.
 *
 * It reads and writes the consents on a background thread, so the EDT is not blocked.
 * It returns `true` if the user made a choice.
 */
@ApiStatus.Internal
suspend fun showConsentsAgreementIfNeeded(filter: Predicate<in Consent?>): Boolean {
  val (consents, confirmationNeeded) = withContext(Dispatchers.IO) {
    ConsentOptions.getInstance().getConsents(filter)
  }
  if (!confirmationNeeded) {
    return false
  }

  val result = withContext(Dispatchers.EDT) {
    showConsentsDialog(ConsentsState(consents))
  } ?: return false

  withContext(Dispatchers.IO) {
    saveConsents(result)
  }
  return true
}

@RequiresEdt
@RequiresBlockingContext
internal fun loadConsentsForConfigurable(): ConsentsState {
  return runWithModalProgressBlocking(ModalTaskOwner.guess(), IdeBundle.message("consent.configurable.loading.progress.title")) {
    loadAllConsentsForEditing()
  }
}

@RequiresEdt
@RequiresBlockingContext
internal fun applyConsentsFromConfigurable(consentsState: ConsentsState) {
  val actualConsents = consentsState.actualConsents
  val localConsents = consentsState.localConsents
  saveInModalProgress {
    saveConsents(actualConsents)
    saveConsentsAsLocalConsents(localConsents)
  }
}

/**
 * Runs [save] in a modal progress, so the EDT is not blocked.
 *
 * The user cannot cancel the progress. A cancel can write one part of the consents and lose the other part.
 */
@RequiresEdt
@RequiresBlockingContext
private fun saveInModalProgress(save: suspend () -> Unit) {
  runWithModalProgressBlocking(
    ModalTaskOwner.guess(),
    IdeBundle.message("consent.configurable.saving.progress.title"),
    TaskCancellation.nonCancellable(),
  ) {
    save()
  }
}

@RequiresBackgroundThread
private fun loadLocalConsentIds(): Set<String> {
  return LocalConsentOptions.getLocalConsents().first.mapTo(HashSet(), Consent::getId)
}
