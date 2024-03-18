// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.InlayPayload
import com.intellij.codeInsight.hints.declarative.InlayPosition
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.util.NlsContexts


/**
 * Not thread safe!
 *
 * @param isInPreview whether the provider is collected in preview (in settings). In this mode, all the options are anyway collected.
 * @param enabledOptions an exhaustive set of options
 * @param providerClass used for diagnostics only
 */
class InlayTreeSinkImpl(
  private val providerId: String,
  private val enabledOptions: Map<String, Boolean>,
  private val isInPreview: Boolean,
  private val providerIsDisabled: Boolean,
  private val providerClass: Class<*>
) : InlayTreeSink {
  private val inlayDataToPresentation = ArrayList<InlayData>()

  // id -> isEnabled
  private val activeOptions = HashMap<String, Boolean>()

  override fun addPresentation(position: InlayPosition,
                               payloads: List<InlayPayload>?,
                               @NlsContexts.HintText tooltip: String?,
                               hasBackground: Boolean,
                               builder: PresentationTreeBuilder.() -> Unit) {
    val b = PresentationTreeBuilderImpl.createRoot()
    b.builder()
    val tree = b.complete()
    if (tree.size == 0) {
      throw PluginException.createByClass("Provider didn't provide any presentation. It is forbidden - do not try to create it in this case.", RuntimeException(
        "${providerClass.canonicalName} id: $providerId"), providerClass)
    }
    val disabled = providerIsDisabled || if (activeOptions.isNotEmpty()) {
      activeOptions.values.any { !it }
    }
    else {
      false
    }
    inlayDataToPresentation.add(InlayData(position, tooltip, hasBackground, tree, providerId, disabled, payloads, providerClass))
  }

  override fun whenOptionEnabled(optionId: String, block: () -> Unit) {
    val isEnabled = enabledOptions[optionId]
    if (isEnabled == null) {
      error("Option $optionId is not found for provider $providerId")
    }
    if (!isInPreview) {
      if (!isEnabled) return
    }
    val wasNotEnabledEarlier = activeOptions.put(optionId, isEnabled) != null
    try {
      block()
    }
    finally {
      if (!wasNotEnabledEarlier) {
        activeOptions.remove(optionId)
      }
    }
  }

  fun finish(): List<InlayData> {
    return inlayDataToPresentation
  }
}
