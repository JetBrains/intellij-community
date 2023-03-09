// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Agreements")

package com.intellij.ide.gdpr

import com.intellij.diagnostic.LoadingState
import com.intellij.idea.AppExitCodes
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
  val isPrivacyPolicy = agreement.isPrivacyPolicy
  val bundle = ResourceBundle.getBundle("messages.AgreementsBundle")
  showAgreementUi {
    htmlText = agreement.text
    title = if (isPrivacyPolicy) {
      "${ApplicationInfoImpl.getShadowInstance().shortCompanyName} ${bundle.getString("userAgreement.dialog.privacyPolicy.title")}"
    }
    else {
      "${ApplicationNamesInfo.getInstance().fullProductName} ${bundle.getString("userAgreement.dialog.userAgreement.title")}"
    }

    declineButton(
      text = bundle.getString("userAgreement.dialog.exit"),
      action = {
        if (LoadingState.COMPONENTS_REGISTERED.isOccurred) {
          ApplicationManager.getApplication().exit(true, true, false)
        }
        else {
          exitProcess(AppExitCodes.PRIVACY_POLICY_REJECTION)
        }
      }
    )

    checkBox(
      text = bundle.getString("userAgreement.dialog.checkBox"),
      action = { checkBox ->
        enableAcceptButton(checkBox.isSelected)
        if (checkBox.isSelected) {
          focusToAcceptButton()
        }
      }
    )

    if (ApplicationInfoImpl.getShadowInstance().isEAP) {
      acceptButton(
        text = bundle.getString("userAgreement.dialog.continue"),
        isEnabled = false,
        action = { dialogWrapper ->
          EndUserAgreement.setAccepted(agreement)
          dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
        },
      )
      eapPanel = isPrivacyPolicy
    }
    else {
      acceptButton(
        text = bundle.getString("userAgreement.dialog.continue"),
        isEnabled = false,
        action = { dialogWrapper ->
          EndUserAgreement.setAccepted(agreement)
          if (true) {
            configureDataSharing(bundle)
          }
          else {
            dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
          }
        }
      )
    }
  }
}

internal fun showDataSharingAgreement() {
  showAgreementUi {
    configureDataSharing(bundle = ResourceBundle.getBundle("messages.AgreementsBundle"))
  }
}

private fun AgreementUiBuilder.configureDataSharing(bundle: ResourceBundle) {
  val dataSharingConsent = ConsentOptions.getInstance().getConsents(ConsentOptions.condUsageStatsConsent()).first[0]
  htmlText = prepareConsentsHtml(consent = dataSharingConsent, bundle = bundle).toString()
  title = bundle.getString("dataSharing.dialog.title")
  clearBottomPanel()
  focusToText()
  acceptButton(
    text = bundle.getString("dataSharing.dialog.accept"),
    action = {
      AppUIUtil.saveConsents(listOf(dataSharingConsent.derive(true)))
      it.close(DialogWrapper.OK_EXIT_CODE)
    },
  )
  declineButton(
    text = bundle.getString("dataSharing.dialog.decline"),
    action = {
      AppUIUtil.saveConsents(listOf(dataSharingConsent.derive(false)))
      it.close(DialogWrapper.CANCEL_EXIT_CODE)
    },
  )
}

private fun prepareConsentsHtml(consent: Consent, bundle: ResourceBundle): HtmlChunk {
  val allProductChunk = if (ConsentOptions.getInstance().isEAP) {
    HtmlChunk.empty()
  }
  else {
    val hint = bundle.getString("dataSharing.applyToAll.hint").replace("{0}", ApplicationInfoImpl.getShadowInstance().shortCompanyName)
    HtmlChunk.text(hint).wrapWith("hint").wrapWith("p")
  }
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