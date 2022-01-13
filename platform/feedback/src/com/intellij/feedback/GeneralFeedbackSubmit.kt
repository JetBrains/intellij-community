// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.feedback

import com.intellij.feedback.bundle.FeedbackBundle
import com.intellij.feedback.notification.ThanksForFeedbackNotification
import com.intellij.ide.BrowserUtil
import com.intellij.ide.feedback.ZenDeskRequests
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ZenDeskForm
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.HyperlinkLabel
import com.intellij.util.PlatformUtils
import com.intellij.util.readXmlAsModel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.JPanel

/** Should be used inside JSON top-level keys to distinguish reports without any external information */
const val FEEDBACK_REPORT_ID_KEY: String = "feedback_id"

const val DEFAULT_NO_EMAIL_ZENDESK_REQUESTER: String = "no_mail@jetbrains.com"

private const val PATH_TO_TEST_FEEDBACK_REQUEST_FORM_XML = "forms/SimpleTestFeedbackForm.xml"
private const val PATH_TO_PRODUCTION_FEEDBACK_REQUEST_FORM_XML = "forms/SimpleProductionFeedbackForm.xml"
private const val PRIVACY_POLICY_URL: String = "https://www.jetbrains.com/legal/docs/privacy/privacy.html"
private const val PRIVACY_POLICY_THIRD_PARTIES_URL = "https://www.jetbrains.com/legal/docs/privacy/third-parties.html"

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
                          feedbackRequestType: FeedbackRequestType = FeedbackRequestType.TEST_REQUEST
) {
  ApplicationManager.getApplication().executeOnPooledThread {
    // Any class from this module will fit
    val pathToFeedbackFormXml = when(feedbackRequestType) {
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
  ApplicationManager.getApplication().invokeLater {
    ThanksForFeedbackNotification().notify(project)
  }
}

fun createFeedbackAgreementComponent(project: Project?, systemInfo: () -> Unit) =
  JPanel().apply {
    layout = GridLayout(4, 1, 0, 0)

    add(createLineOfConsent(FeedbackBundle.message("dialog.created.project.consent.1.1"),
                            FeedbackBundle.message("dialog.created.project.consent.1.2"),
                            FeedbackBundle.message("dialog.created.project.consent.1.3"), systemInfo))

    add(createLineOfConsent(FeedbackBundle.message("dialog.created.project.consent.2")))

    add(createLineOfConsent(FeedbackBundle.message("dialog.created.project.consent.3.1"),
                            FeedbackBundle.message("dialog.created.project.consent.3.2"),
                            FeedbackBundle.message("dialog.created.project.consent.3.3")) {
      BrowserUtil.browse(PRIVACY_POLICY_THIRD_PARTIES_URL, project)
    })

    add(createLineOfConsent(linkText = FeedbackBundle.message("dialog.created.project.consent.4.2"),
                            postfix = FeedbackBundle.message("dialog.created.project.consent.4.3")) {
      BrowserUtil.browse(PRIVACY_POLICY_URL, project)
    })
  }

private fun createLineOfConsent(prefixTest: String = "",
                                linkText: String = "",
                                postfix: String = "",
                                action: () -> Unit = {}): HyperlinkLabel {
  val text = HtmlBuilder()
    .append(prefixTest) //NON-NLS
    .append(HtmlChunk.tag("hyperlink")
              .addText(linkText)) //NON-NLS
    .append(postfix) //NON-NLS
  val label = HyperlinkLabel().apply {
    setTextWithHyperlink(text.toString())
    addHyperlinkListener {
      action()
    }
    foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
    minimumSize = Dimension(preferredSize.width, minimumSize.height)
  }
  UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, label)

  return label
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