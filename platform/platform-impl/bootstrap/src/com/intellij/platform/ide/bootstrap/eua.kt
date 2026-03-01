// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.bootstrap

import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.ide.gdpr.EndUserAgreement
import com.intellij.ide.gdpr.showDataSharingAgreement
import com.intellij.ide.gdpr.showEndUserAndDataSharingAgreements
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.util.ui.RawSwingDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// On startup 2 dialogs must be shown:
// - gdpr agreement
// - eu(l)a
internal suspend fun loadEuaDocument(appInfoDeferred: Deferred<ApplicationInfoEx>): EndUserAgreementStatus {
  val vendorAsProperty = System.getProperty("idea.vendor.name", "")
  val isVendorJetBrains = if (vendorAsProperty.isNotEmpty()) {
    vendorAsProperty == "JetBrains"
  }
  else {
    appInfoDeferred.await().isVendorJetBrains
  }
  if (!isVendorJetBrains) {
    return EndUserAgreementStatus.NonJbVendor
  }

  if (AppMode.isRemoteDevHost()) {
    return EndUserAgreementStatus.RemoteDev
  }

  val document = span("eua getting") {
    EndUserAgreement.getLatestDocument()
  }
  val isAccepted = span("eua is accepted checking") {
    document.isAccepted
  }
  if (isAccepted) {
    return EndUserAgreementStatus.Accepted
  }
  return EndUserAgreementStatus.Required(document)
}

internal sealed interface EndUserAgreementStatus {
  class Required(val document: EndUserAgreement.Document) : EndUserAgreementStatus
  object RemoteDev : EndUserAgreementStatus
  object Accepted : EndUserAgreementStatus
  object NonJbVendor : EndUserAgreementStatus
}

internal suspend fun prepareShowEuaIfNeededTask(
  documentStatus: EndUserAgreementStatus,
  appInfoDeferred: Deferred<ApplicationInfoEx>,
  asyncScope: CoroutineScope,
): (suspend () -> Boolean)? {
  val updateCached = asyncScope.launch(CoroutineName("eua cache updating") + Dispatchers.IO) {
    EndUserAgreement.updateCachedContentToLatestBundledVersion()
  }

  suspend fun prepareAndExecuteInEdt(task: () -> Unit) {
    span("eua showing") {
      updateCached.join()
      withContext(RawSwingDispatcher) {
        task()
      }
    }
  }

  if (documentStatus is EndUserAgreementStatus.Required) {
    return {
      prepareAndExecuteInEdt {
        showEndUserAndDataSharingAgreements(documentStatus.document)
      }
      true
    }
  }

  // ConsentOptions uses ApplicationInfo inside, so we need to load it first
  appInfoDeferred.await()
  if (ConsentOptions.needToShowUsageStatsConsent()) {
    return {
      prepareAndExecuteInEdt {
        showDataSharingAgreement()
      }
      false
    }
  }
  return null
}
