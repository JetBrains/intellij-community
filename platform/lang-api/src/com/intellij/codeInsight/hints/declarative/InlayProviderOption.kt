// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import org.jetbrains.annotations.Nls

@Tag("option")
class InlayProviderOption {
  companion object {
    private val LOG = logger<InlayProviderOption>()
  }

  /**
   * Must not contain #
   */
  @Attribute("optionId")
  @RequiredElement
  var myOptionId: String? = null

  @Attribute("enabledByDefault")
  @RequiredElement
  var enabledByDefault: Boolean = false

  @Attribute("bundle")
  @RequiredElement
  var bundle: String? = null

  @Attribute("descriptionKey")
  @RequiredElement
  var descriptionBundleKey: String? = null

  @Attribute("nameKey")
  @RequiredElement
  var nameKey: String? = null

  fun getDescription(bean: InlayHintsProviderExtensionBean): @Nls String? {
    return bean.getLocalizedString(bundle, descriptionBundleKey)
  }

  fun getName(bean: InlayHintsProviderExtensionBean): @Nls String {
    val localizedString = bean.getLocalizedString(bundle, nameKey)
    return if (localizedString != null) localizedString else {
      LOG.warn("Not found key in bundle for option $myOptionId in ${bean.requiredProviderId()}")
      nameKey!!
    }
  }

  fun getOptionId() : String {
    return myOptionId!!
  }
}