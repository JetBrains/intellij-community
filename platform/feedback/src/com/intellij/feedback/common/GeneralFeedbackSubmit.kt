// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.common.notification.ThanksForFeedbackNotification
import com.intellij.ide.BrowserUtil
import com.intellij.ide.feedback.ZenDeskRequests
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ZenDeskForm
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.Row
import com.intellij.util.PlatformUtils
import com.intellij.util.xml.dom.readXmlAsModel

/** Should be used inside JSON top-level keys to distinguish reports without any external information */
const val FEEDBACK_REPORT_ID_KEY: String = "feedback_id"

const val DEFAULT_NO_EMAIL_ZENDESK_REQUESTER: String = "no_mail@jetbrains.com"

private const val PATH_TO_TEST_FEEDBACK_REQUEST_FORM_XML = "forms/SimpleTestFeedbackForm.xml"
private const val PATH_TO_PRODUCTION_FEEDBACK_REQUEST_FORM_XML = "forms/SimpleProductionFeedbackForm.xml"

enum class FeedbackRequestType {
  NO_REQUEST, // can be used during feedback UI/statistics development and debug
  TEST_REQUEST,
  PRODUCTION_REQUEST
}

fun submitGeneralFeedback(project: Project?,
                          title: String,
                          description: String,
                          feedbackType: String,
                          collectedData: String,
                          email: String = DEFAULT_NO_EMAIL_ZENDESK_REQUESTER,
                          onDone: () -> Unit = {},
                          onError: () -> Unit = {},
                          feedbackRequestType: FeedbackRequestType = FeedbackRequestType.TEST_REQUEST,
                          showNotification: Boolean = true
) {
  ApplicationManager.getApplication().executeOnPooledThread {
    // Any class from this module will fit
    val pathToFeedbackFormXml = when (feedbackRequestType) {
      FeedbackRequestType.NO_REQUEST -> return@executeOnPooledThread
      FeedbackRequestType.TEST_REQUEST -> PATH_TO_TEST_FEEDBACK_REQUEST_FORM_XML
      FeedbackRequestType.PRODUCTION_REQUEST -> PATH_TO_PRODUCTION_FEEDBACK_REQUEST_FORM_XML
    }
    val stream = FeedbackRequestType::class.java.classLoader.getResourceAsStream(pathToFeedbackFormXml)
                 ?: throw RuntimeException("Resource not found: $pathToFeedbackFormXml")
    val xmlElement = readXmlAsModel(stream)
    val form = ZenDeskForm.parse(xmlElement)
    ZenDeskRequests().submit(
      form,
      email,
      title,
      description,
      mapOf(
        "product_tag" to getProductTag(),
        "feedback_type" to feedbackType,
        "collected_data" to collectedData),
      onDone,
      onError
    )
  }
  if (showNotification) {
    ApplicationManager.getApplication().invokeLater {
      ThanksForFeedbackNotification().notify(project)
    }
  }
}

fun Row.feedbackAgreement(project: Project?, systemInfo: () -> Unit) {
  comment(CommonFeedbackBundle.message("dialog.feedback.consent"), maxLineLength = MAX_LINE_LENGTH_NO_WRAP) {
    when (it.description) {
      "systemInfo" -> systemInfo()
      else -> it.url?.let { url ->
        BrowserUtil.browse(url.toExternalForm(), project)
      }
    }
  }
}

/**
 * @return product tag.
 * @see <a href="https://youtrack.jetbrains.com/issue/ZEN-1460#focus=Comments-27-5692479.0-0">ZEN-1460</a> for more information
 */
private fun getProductTag(): String {
  return when {
    PlatformUtils.isIntelliJ() -> "ij_idea"
    PlatformUtils.isPhpStorm() -> "ij_phpstorm"
    PlatformUtils.isWebStorm() -> "ij_webstorm"
    PlatformUtils.isPyCharm() -> "ij_pycharm"
    PlatformUtils.isRubyMine() -> "ij_rubymine"
    PlatformUtils.isAppCode() -> "ij_appcode"
    PlatformUtils.isCLion() -> "ij_clion"
    PlatformUtils.isDataGrip() -> "ij_datagrip"
    PlatformUtils.isPyCharmEducational() -> "ij_pycharm_edu"
    PlatformUtils.isGoIde() -> "ij_goland"
    PlatformUtils.isJetBrainsClient() -> "ij_code_with_me"
    PlatformUtils.isDataSpell() -> "ij_dataspell"
    else -> "undefined"
  }
}