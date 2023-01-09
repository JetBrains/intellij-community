// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute

class InlayHintsCustomSettingsProviderBean : CustomLoadingExtensionPointBean<InlayHintsCustomSettingsProvider<*>>() {
  companion object {
    val EP: ExtensionPointName<InlayHintsCustomSettingsProviderBean> = ExtensionPointName<InlayHintsCustomSettingsProviderBean>(
      "com.intellij.codeInsight.declarativeInlayProviderCustomSettingsProvider")
  }

  @Attribute
  @RequiredElement
  var implementationClass: String? = null

  @Attribute
  @RequiredElement
  var providerId: String? = null

  @Attribute
  @RequiredElement
  var language: String? = null

  override fun getImplementationClassName(): String? {
    return implementationClass
  }
}