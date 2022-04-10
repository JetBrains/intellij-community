// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Agreements")
package com.intellij.ide.gdpr

import com.intellij.idea.Main
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.AppUIUtil
import java.util.*
import kotlin.system.exitProcess

fun showEndUserAndDataSharingAgreements(agreement: EndUserAgreement.Document) {
  val ui = AgreementUi.create(agreement.text)
  applyUserAgreement(ui, agreement).pack().show()
}

internal fun showDataSharingAgreement() {
  val ui = AgreementUi.create()
  applyDataSharing(ui, ResourceBundle.getBundle("messages.AgreementsBundle")).pack().show()
}

private fun applyUserAgreement(ui: AgreementUi, agreement: EndUserAgreement.Document): AgreementUi {
  val isPrivacyPolicy = agreement.isPrivacyPolicy
  val bundle = ResourceBundle.getBundle("messages.AgreementsBundle")
  val commonUserAgreement = ui
    .setTitle(
      if (isPrivacyPolicy)
        ApplicationInfoImpl.getShadowInstance().shortCompanyName + " " + bundle.getString("userAgreement.dialog.privacyPolicy.title")
      else
        ApplicationNamesInfo.getInstance().fullProductName + " " + bundle.getString("userAgreement.dialog.userAgreement.title"))
    .setDeclineButton(bundle.getString("userAgreement.dialog.exit")) {
      val application = ApplicationManager.getApplication()
      if (application == null) {
        exitProcess(Main.PRIVACY_POLICY_REJECTION)
      }
      else {
        application.exit(true, true, false)
      }
    }
    .addCheckBox(bundle.getString("userAgreement.dialog.checkBox")) { checkBox ->
      ui.enableAcceptButton(checkBox.isSelected)
      if (checkBox.isSelected) ui.focusToAcceptButton()
    }
  if (ApplicationInfoImpl.getShadowInstance().isEAP) {
    commonUserAgreement
      .setAcceptButton(bundle.getString("userAgreement.dialog.continue"), false) { dialogWrapper: DialogWrapper ->
        EndUserAgreement.setAccepted(agreement)
        dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
      }
      .addEapPanel(isPrivacyPolicy)
  }
  else {
    commonUserAgreement
      .setAcceptButton(bundle.getString("userAgreement.dialog.continue"), false) { dialogWrapper: DialogWrapper ->
        EndUserAgreement.setAccepted(agreement)
        if (ConsentOptions.needToShowUsageStatsConsent()) {
          applyDataSharing(ui, bundle)
        }
        else {
          dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
        }
      }
  }
  return ui
}

private fun applyDataSharing(ui: AgreementUi, bundle: ResourceBundle): AgreementUi {
  val dataSharingConsent = ConsentOptions.getInstance().getConsents(ConsentOptions.condUsageStatsConsent()).first[0]
  ui.setContent(prepareConsentsHtml(dataSharingConsent, bundle))
    .setTitle(bundle.getString("dataSharing.dialog.title"))
    .clearBottomPanel()
    .focusToText()
    .setAcceptButton(bundle.getString("dataSharing.dialog.accept")) {
      AppUIUtil.saveConsents(listOf(dataSharingConsent.derive(true)))
      it.close(DialogWrapper.OK_EXIT_CODE)
    }
    .setDeclineButton(bundle.getString("dataSharing.dialog.decline")) {
      AppUIUtil.saveConsents(listOf(dataSharingConsent.derive(false)))
      it.close(DialogWrapper.CANCEL_EXIT_CODE)
    }
  return ui
}

private fun prepareConsentsHtml(consent: Consent, bundle: ResourceBundle): HtmlChunk {
  val allProductChunk = if (!ConsentOptions.getInstance().isEAP) {
    val hint = bundle.getString("dataSharing.applyToAll.hint").replace("{0}", ApplicationInfoImpl.getShadowInstance().shortCompanyName)
    HtmlChunk.text(hint).wrapWith("hint").wrapWith("p")
  }
  else HtmlChunk.empty()
  val preferencesHint = bundle.getString("dataSharing.revoke.hint").replace("{0}", ShowSettingsUtil.getSettingsMenuName())
  val preferencesChunk = HtmlChunk.text(preferencesHint).wrapWith("hint").wrapWith("p")
  val title = HtmlChunk.text(bundle.getString("dataSharing.consents.title")).wrapWith("h1")
  return HtmlBuilder()
    .append(title)
    .append(HtmlChunk.p().addRaw(consent.text))
    .append(allProductChunk)
    .append(preferencesChunk)
    .wrapWithHtmlBody()
}