// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr.trace

import com.intellij.ide.IdeBundle
import com.intellij.ide.Region
import com.intellij.ide.RegionSettings
import com.intellij.ide.gdpr.ConsentOptions
import com.intellij.ide.gdpr.DataCollectionAgreement
import com.intellij.ide.util.RunOnceUtil.runOnceForApp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.ui.LicensingFacade
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

internal class TraceDataSharingActivity(private val coroutineScope: CoroutineScope) : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (!canAskForTraceDataCollection()) {
      return
    }
    enableTraceDataSharingForNonCommercialIfNeeded()
    notifyAboutInternalTraceDataCollectionIfNeeded(project)
  }

  private fun enableTraceDataSharingForNonCommercialIfNeeded() {
    if (!ApplicationInfoEx.getInstanceEx().isVendorJetBrains) {
      return
    }
    if (isCommercialLicense()) return
    val context = if (ApplicationManager.getApplication().isUnitTestMode) Dispatchers.Default else Dispatchers.EDT
    coroutineScope.launch(context) {
      runOnceForApp(TRACE_NON_COMMERCIAL_POPUP_ID) {
        Messages.showInfoMessage(IdeBundle.message("notification.trace.data.sharing.non.commercial.text"),
                                 IdeBundle.message("notification.trace.data.sharing.non.commercial.title"))
        ConsentOptions.getInstance().setTraceDataCollectionPermission(true)
      }
    }
  }

  @RequiresEdt
  private fun askForDataCollection(project: Project) {
    val consent = MessageDialogBuilder.yesNo(
      IdeBundle.message("notification.trace.data.sharing.internal.title"),
      IdeBundle.message("notification.trace.data.sharing.internal.text")
    )

    consent.yesText(IdeBundle.message("notification.trace.data.sharing.internal.yes"))
    ConsentOptions.getInstance().setTraceDataCollectionPermission(consent.ask(project))
  }

  private fun notifyAboutInternalTraceDataCollectionIfNeeded(project: Project) {
    if (!isInternalUser()) {
      return
    }
    coroutineScope.launch {
      delay(getInternalUserPopupDelay())
      runOnceForApp(TRACE_INTERNAL_POPUP_ID) {
        val context = if (ApplicationManager.getApplication().isUnitTestMode) Dispatchers.Default else Dispatchers.EDT
        coroutineScope.launch(context) {
          askForDataCollection(project)
        }
      }
    }
  }

  internal suspend fun testExecute(project: Project) {
    execute(project)
  }
}

private const val TRACE_NON_COMMERCIAL_POPUP_ID = "trace.non.commercial.popup.id"
private const val TRACE_INTERNAL_POPUP_ID = "trace.internal.popup.id"
private val INTERNAL_USER_POPUP_DELAY = 30.seconds

private fun getInternalUserPopupDelay() = if (ApplicationManager.getApplication().isUnitTestMode) 0.seconds else INTERNAL_USER_POPUP_DELAY

private fun isCommercialLicense(): Boolean {
  val metadata = LicensingFacade.getInstance()?.metadata ?: return true
  if (metadata.length > 10) {
    val isCommercial = metadata[10] == 'C'
    if (isCommercial) {
      return true
    }
  }
  return false
}

private fun isInternalUser(): Boolean {
  val facade = LicensingFacade.getInstance() ?: return false
  if (facade.licenseeEmail?.endsWith("@jetbrains.com") == true) {
    return true
  }
  return facade.licensedTo?.contains("JetBrains Team") == true
}

private fun canAskForTraceDataCollection(): Boolean =
  !isChinaRegion() && DataCollectionAgreement.getInstance() in listOf(DataCollectionAgreement.NOT_SET, null)

private fun isChinaRegion(): Boolean {
  return when (RegionSettings.getRegion()) {
    Region.CHINA -> true
    Region.NOT_SET -> Locale.CHINA.country == Locale.getDefault().country
    else -> false
  }
}