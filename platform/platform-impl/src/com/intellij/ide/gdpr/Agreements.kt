// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.gdpr

import com.intellij.idea.Main
import com.intellij.idea.SplashManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.AppUIUtil
import java.util.*

object Agreements {

  private val bundle
    get() = ResourceBundle.getBundle("messages.AgreementsBundle")

  private val isEap
    get() = ApplicationInfoImpl.getShadowInstance().isEAP

  fun showEndUserAndDataSharingAgreements(agreement: EndUserAgreement.Document) {
    val agreementUi = AgreementUi.create(agreement.text)
    val dialog = agreementUi.applyUserAgreement(agreement).pack()
    SplashManager.executeWithHiddenSplash(dialog.window) { dialog.show() }
  }

  fun showDataSharingAgreement() {
    val agreementUi = AgreementUi.create()
    val dialog = agreementUi.applyDataSharing().pack()
    SplashManager.executeWithHiddenSplash(dialog.window) { dialog.show() }
  }

  private fun AgreementUi.applyUserAgreement(agreement: EndUserAgreement.Document): AgreementUi {
    val isPrivacyPolicy = agreement.isPrivacyPolicy
    val commonUserAgreement = this
      .setTitle(
        if (isPrivacyPolicy)
          ApplicationInfoImpl.getShadowInstance().shortCompanyName + " " + bundle.getString("userAgreement.dialog.privacyPolicy.title")
        else
          ApplicationNamesInfo.getInstance().fullProductName + " " + bundle.getString("userAgreement.dialog.userAgreement.title"))
      .setDeclineButton(bundle.getString("userAgreement.dialog.exit")) {
        val application = ApplicationManager.getApplication()
        if (application == null) {
          System.exit(Main.PRIVACY_POLICY_REJECTION)
        }
        else {
          application.exit(true, true, false)
        }
      }
      .addCheckBox(bundle.getString("userAgreement.dialog.checkBox")) { checkBox ->
        this.enableAcceptButton(checkBox.isSelected)
        if (checkBox.isSelected) focusToAcceptButton()
      }
    if (!isEap) {
      commonUserAgreement
        .setAcceptButton(bundle.getString("userAgreement.dialog.continue"), false) { dialogWrapper: DialogWrapper ->
          EndUserAgreement.setAccepted(agreement)
          if (AppUIUtil.needToShowConsentsAgreement()) {
            this.applyDataSharing()
          }
          else {
            dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
          }
        }
    }
    else {
      commonUserAgreement
        .setAcceptButton(bundle.getString("userAgreement.dialog.continue"), false) { dialogWrapper: DialogWrapper ->
          EndUserAgreement.setAccepted(agreement)
          dialogWrapper.close(DialogWrapper.OK_EXIT_CODE)
        }
        .addEapPanel(isPrivacyPolicy)
    }
    return this
  }

  private fun AgreementUi.applyDataSharing(): AgreementUi {
    val dataSharingConsent = ConsentOptions.getInstance().consents.first[0]
    this.setText(prepareConsentsHtmlText(dataSharingConsent))
      .setTitle(bundle.getString("dataSharing.dialog.title"))
      .clearBottomPanel()
      .focusToText()
      .setAcceptButton(bundle.getString("dataSharing.dialog.accept")) {
        val consentToSave = dataSharingConsent.derive(true)
        AppUIUtil.saveConsents(listOf(consentToSave))
        it.close(DialogWrapper.OK_EXIT_CODE)
      }
      .setDeclineButton(bundle.getString("dataSharing.dialog.decline")) {
        val consentToSave = dataSharingConsent.derive(false)
        AppUIUtil.saveConsents(listOf(consentToSave))
        it.close(DialogWrapper.CANCEL_EXIT_CODE)
      }
    return this
  }

  private fun prepareConsentsHtmlText(consent: Consent): String {
    val allProductHint = if (!ConsentOptions.getInstance().isEAP) "<p><hint>${bundle.getString("dataSharing.applyToAll.hint")}</hint></p>".replace("{0}", ApplicationInfoImpl.getShadowInstance().shortCompanyName)
    else ""
    val preferencesHint = "<p><hint>${bundle.getString("dataSharing.revoke.hint").replace("{0}", ShowSettingsUtil.getSettingsMenuName())}</hint></p>"
    return ("<html><body> <h1>${bundle.getString("dataSharing.consents.title")}</h1>"
            + "<p>" + consent.text.replace("\n", "</p><p>") + "</p>" +
            allProductHint + preferencesHint +
            "</body></html>")
  }

}