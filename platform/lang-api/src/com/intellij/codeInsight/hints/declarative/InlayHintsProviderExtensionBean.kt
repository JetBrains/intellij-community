// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import com.intellij.codeInsight.hints.InlayGroup
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.CustomLoadingExtensionPointBean
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.KeyedLazyInstance
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.Nls

class InlayHintsProviderExtensionBean : CustomLoadingExtensionPointBean<InlayHintsProvider>(), KeyedLazyInstance<InlayHintsProvider> {
  companion object {
    val EP: ExtensionPointName<InlayHintsProviderExtensionBean> = ExtensionPointName("com.intellij.codeInsight.declarativeInlayProvider")

    private val LOG = logger<InlayHintsProviderExtensionBean>()
  }

  /**
   * Inheritor of [com.intellij.codeInsight.hints.declarative.InlayHintsProvider]
   */
  @Attribute
  @RequiredElement
  var implementationClass: String? = null

  /**
   * Language ID
   */
  @RequiredElement
  @Attribute
  var language: String? = null

  /**
   * Whether it will be enabled by default in settings.
   */
  @RequiredElement
  @Attribute
  var isEnabledByDefault: Boolean = true

  /**
   * key of the group, one of [com.intellij.codeInsight.hints.InlayGroup] values
   */
  @RequiredElement
  @Attribute
  var group: String? = null

  /**
   * Provider id, which must uniquely identify the pair (provider, language),
   * usually meaning that provider id somehow contains the id of the language.
   */
  @RequiredElement
  @Attribute
  var providerId: String? = null

  @get:Property(surroundWithTag = false)
  @get:XCollection(elementName = "option")
  var options: List<InlayProviderOption> = ArrayList()

  @RequiredElement
  @Attribute
  var bundle: String? = null

  @RequiredElement
  @Attribute
  var nameKey: String? = null

  @Attribute
  var descriptionKey: String? = null

  override fun getImplementationClassName(): String? {
    return implementationClass
  }

  override fun getKey(): String {
    return language!!
  }

  fun requiredGroup() : InlayGroup {
    return InlayGroup.valueOf(group!!)
  }

  /**
   * Must not contain #
   */
  fun requiredProviderId() : String {
    return providerId!!
  }

  fun getProviderName() : @Nls String {
    return getLocalizedString(bundle!!, nameKey!!)!!
  }

  fun getDescription() : @Nls String? {
    val bundleName = bundle ?: return null
    val descriptionKey = descriptionKey ?: return null
    return getLocalizedString(bundleName, descriptionKey)!!
  }

  internal fun getLocalizedString(bundleName: String?, key: String?): @Nls String? {
    val descriptor = pluginDescriptor
    val baseName = bundleName ?: descriptor.resourceBundleBaseName
    if (baseName == null || key == null) {
      if (bundleName != null) {
        LOG.warn(implementationClass)
      }
      return null
    }
    val resourceBundle = DynamicBundle.getResourceBundle(descriptor.classLoader, baseName)
    return AbstractBundle.message(resourceBundle, key)
  }
}