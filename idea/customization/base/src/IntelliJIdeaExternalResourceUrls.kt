// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea.customization.base

import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.impl.customization.BaseJetBrainsExternalProductResourceUrls
import com.intellij.platform.ide.impl.customization.ZenDeskFeedbackFormData
import com.intellij.platform.ide.impl.customization.ZenDeskFeedbackFormFieldIds
import com.intellij.util.Url
import com.intellij.util.Urls

class IntelliJIdeaExternalResourceUrls : BaseJetBrainsExternalProductResourceUrls() {
  override val basePatchDownloadUrl: Url
    get() = Urls.newFromEncoded("https://download.jetbrains.com/idea/")
  
  override val productPageUrl: Url
    get() = Urls.newFromEncoded("https://www.jetbrains.com/idea/")
  
  override val youtrackProjectId: String
    get() = "IDEA"

  override val shortProductNameUsedInForms: String
    get() = "IDEA"

  override val zenDeskFeedbackFormData: ZenDeskFeedbackFormData
    get() = object : ZenDeskFeedbackFormData {
      override val formUrl: String = "https://jbsintellij.zendesk.com"
      override val formId: Long = 360001912739
      override val productId: String = "ij_idea"
      override val fieldIds = object : ZenDeskFeedbackFormFieldIds {
        override val product: Long = 28147552
        override val country: Long = 28102551
        override val rating: Long = 29444529
        override val build: Long = 28500325
        override val os: Long = 28151042
        override val timezone: Long = 28500645
        override val eval: Long = 28351649
        override val systemInfo: Long = 360021010939
        override val needSupport: Long = 22996310
        override val topic: Long = 28116681
      }
    }

  override val youTubeChannelUrl: Url
    get() = Urls.newFromEncoded("https://www.youtube.com/user/intellijideavideo")

  override val keyboardShortcutsPdfUrl: Url
    get() {
      val suffix = if (SystemInfo.isMac) "_Mac" else ""
      return Urls.newFromEncoded("https://www.jetbrains.com/idea/docs/IntelliJIDEA_ReferenceCard$suffix.pdf")
    }

  override val gettingStartedPageUrl: Url
    get() = Urls.newFromEncoded("https://www.jetbrains.com/idea/resources/")
  
  override val baseWebHelpUrl: Url
    get() = Urls.newFromEncoded("https://www.jetbrains.com/help/idea/")
}