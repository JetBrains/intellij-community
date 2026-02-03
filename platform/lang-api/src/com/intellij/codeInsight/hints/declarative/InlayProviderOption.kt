// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import org.jetbrains.annotations.ApiStatus
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
  var optionId: String? = null

  @Attribute("enabledByDefault")
  @RequiredElement
  var enabledByDefault: Boolean = false

  @Attribute("bundle")
  @RequiredElement
  var bundle: String? = null

  @Attribute("descriptionKey")
  @RequiredElement
  @Nls
  var descriptionBundleKey: String? = null

  @Attribute("nameKey")
  @RequiredElement
  @Nls(capitalization = Nls.Capitalization.Title)
  var nameKey: String? = null

  /**
   * The subset of options where `showInTree == true` should be exhaustive:
   * if all such options are disabled, the provider must not collect any inlay hints.
   *
   * If set to `false`, the option will be displayed outside the inlay hints checkbox tree in settings, in the right pane.
   */
  @ApiStatus.Experimental
  @Attribute("showInTree")
  var showInTree: Boolean = true

  fun getDescription(bean: InlayHintsProviderExtensionBean): @Nls String? {
    return bean.getLocalizedString(bundle, descriptionBundleKey)
  }

  fun getName(bean: InlayHintsProviderExtensionBean): @Nls String {
    val localizedString = bean.getLocalizedString(bundle, nameKey)
    return if (localizedString != null) localizedString else {
      LOG.warn("Not found key in bundle for option $optionId in ${bean.requiredProviderId()}")
      nameKey!!
    }
  }

  fun requireOptionId() : String {
    return optionId!!
  }
}