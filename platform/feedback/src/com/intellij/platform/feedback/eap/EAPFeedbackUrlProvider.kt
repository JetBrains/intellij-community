// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.eap

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
open class EAPFeedbackUrlProvider {
  open fun surveyUrl(): String {
    return "https://surveys.jetbrains.com/s3/${getProductName().lowercase()}-${
      getProductVersion().replace('.', '-')
    }-eap-user-survey"
  }

  private fun getProductName(): @NlsSafe String = ApplicationNamesInfo.getInstance().productName
  private fun getProductVersion(): @NlsSafe String = ApplicationInfo.getInstance().shortVersion
}