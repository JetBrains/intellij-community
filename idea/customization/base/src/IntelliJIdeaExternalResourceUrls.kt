// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea.customization.base

import com.intellij.platform.ide.impl.customization.BaseJetBrainsExternalProductResourceUrls
import com.intellij.platform.ide.impl.customization.ZenDeskFeedbackFormData
import com.intellij.platform.ide.impl.customization.ZenDeskFeedbackFormFieldIds

class IntelliJIdeaExternalResourceUrls : BaseJetBrainsExternalProductResourceUrls() {
  override val basePatchDownloadUrl: String
    get() = "https://download.jetbrains.com/idea/"
  
  override val productPageUrl: String
    get() = "https://www.jetbrains.com/idea/"
  
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
}