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
import com.intellij.util.readXmlAsModel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.awt.GridLayout
import javax.swing.JPanel

const val DEFAULT_NO_EMAIL_ZENDESK_REQUESTER: String = "no_mail@jetbrains.com"

private const val PATH_TO_FEEDBACK_FORM_XML = "forms/ProjectCreationFeedbackForm.xml"
private const val PRIVACY_POLICY_URL: String = "https://www.jetbrains.com/legal/docs/privacy/privacy.html"
private const val PRIVACY_POLICY_THIRD_PARTIES_URL = "https://www.jetbrains.com/legal/docs/privacy/third-parties.html"

fun submitGeneralFeedback(project: Project?,
                          title: String,
                          description: String,
                          collectedData: String,
                          email: String = DEFAULT_NO_EMAIL_ZENDESK_REQUESTER,
                          onDone: () -> Unit = {}, onError: () -> Unit = {}) {
  ApplicationManager.getApplication().executeOnPooledThread {
    // Any class from this module will fit
    val stream = ThanksForFeedbackNotification::class.java.classLoader.getResourceAsStream(PATH_TO_FEEDBACK_FORM_XML)
                 ?: throw RuntimeException("Resource not found: $PATH_TO_FEEDBACK_FORM_XML")
    val xmlElement = readXmlAsModel(stream)
    val form = ZenDeskForm.parse(xmlElement)
    ZenDeskRequests().submit(
      form,
      email,
      title,
      description,
      mapOf("collected_data" to collectedData),
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
    layout = GridLayout(3, 1, 0, 0)

    add(createLineOfConsent(FeedbackBundle.message("dialog.created.project.consent.1.1"),
                            FeedbackBundle.message("dialog.created.project.consent.1.2"),
                            FeedbackBundle.message("dialog.created.project.consent.1.3"), systemInfo))

    add(createLineOfConsent(FeedbackBundle.message("dialog.created.project.consent.2.1"),
                            FeedbackBundle.message("dialog.created.project.consent.2.2"),
                            FeedbackBundle.message("dialog.created.project.consent.2.3")) {
      BrowserUtil.browse(PRIVACY_POLICY_THIRD_PARTIES_URL, project)
    })

    add(createLineOfConsent("",
                            FeedbackBundle.message("dialog.created.project.consent.3.2"),
                            FeedbackBundle.message("dialog.created.project.consent.3.3")) {
      BrowserUtil.browse(PRIVACY_POLICY_URL, project)
    })
  }

private fun createLineOfConsent(prefixTest: String, linkText: String, postfix: String, action: () -> Unit): HyperlinkLabel {
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
