// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.hints.InlayHintsProviderExtension
import com.intellij.codeInsight.hints.InlayHintsSettings
import com.intellij.codeInsight.hints.InlayParameterHintsExtension
import com.intellij.codeInsight.hints.settings.language.SingleLanguageInlayHintsConfigurable
import com.intellij.codeInsight.hints.withSettings
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor

private class InlayHintsSettingsSearchableContributor : SearchableOptionContributor() {
  override fun processOptions(processor: SearchableOptionProcessor) {
    for (providerInfo in InlayHintsProviderExtension.findProviders()) {
      val provider = providerInfo.provider
      val name = provider.name
      val id = SingleLanguageInlayHintsConfigurable.getId(providerInfo.language)
      addOption(processor, name, id)
      val providerWithSettings = provider.withSettings(providerInfo.language, InlayHintsSettings.instance())
      for (case in providerWithSettings.configurable.cases) {
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