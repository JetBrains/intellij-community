// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute

class InlayHintsProviderExtensionBean : CustomLoadingExtensionPointBean<InlayHintsProvider<*>>(), KeyedLazyInstance<InlayHintsProvider<*>> {
  @Attribute("implementationClass")
  @RequiredElement
  var implementationClass: String? = null

  @Attribute("language")
  @RequiredElement
  var language: String? = null

  /**
   * Make sure to provide settingsKeyId as well
   */
  @Attribute("isEnabledByDefault")
  var isEnabledByDefault: Boolean = true

  @Attribute("settingsKeyId")
  var settingsKeyId: String? = null

  override fun getImplementationClassName(): String? {
    return implementationClass
  }

  override fun getKey(): String {
    return language!!
  }
}