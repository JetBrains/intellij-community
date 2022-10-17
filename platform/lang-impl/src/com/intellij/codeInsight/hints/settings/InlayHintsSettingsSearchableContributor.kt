// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.codeInsight.hints.*
import com.intellij.codeInsight.hints.settings.language.SingleLanguageInlayHintsConfigurable
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor

private class InlayHintsSettingsSearchableContributor : SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    for (inlayGroup in InlayGroup.values()) {
      addOption(processor, inlayGroup.toString(), null)
    }
    for (settingsProvider in CodeVisionGroupSettingProvider.EP.EXTENSION_POINT_NAME.extensionList) {
      addOption(processor, settingsProvider.description, null)
      addOption(processor, settingsProvider.groupName, null)
    }
    for (codeVisionProvider in CodeVisionProvider.providersExtensionPoint.extensionList) {
      addOption(processor, codeVisionProvider.name, null)
    }
    for (providerInfo in InlayHintsProviderFactory.EP.extensionList.flatMap(InlayHintsProviderFactory::getProvidersInfo)) {
      val provider = providerInfo.provider
      val name = provider.name
      val id = SingleLanguageInlayHintsConfigurable.getId(providerInfo.language)
      addOption(processor, name, id)
      val providerWithSettings = provider.withSettings(providerInfo.language, InlayHintsSettings.instance())
      val configurable = providerWithSettings.configurable
      @Suppress("SENSELESS_COMPARISON") // for some reason (kotlin bug?) there is no check between kotlin and java and sometimes here comes null
      require (configurable != null) { "Configurable must not be null, provider: ${provider.key.id}" }
      for (case in configurable.cases) {
        addOption(processor, case.name, id)
      }
    }
    InlayParameterHintsExtension.point?.extensions?.flatMap { it.instance.supportedOptions }?.forEach { addOption(processor, it.name, null) }
  }

  private fun addOption(processor: SearchableOptionProcessor, name: String, id: String?) {
    if (id != null) {
      processor.addOptions(name, null, null, id, null, false)
    }
    processor.addOptions(name, null, null, INLAY_ID, null, false)
  }
}