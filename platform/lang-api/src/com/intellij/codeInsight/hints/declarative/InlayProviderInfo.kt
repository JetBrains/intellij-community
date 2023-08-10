// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import org.jetbrains.annotations.Nls

class InlayProviderInfo(
  val provider: InlayHintsProvider,
  val providerId: String,
  val options: Set<InlayOptionInfo>,
  val isEnabledByDefault: Boolean,
  val providerName: @Nls String
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as InlayProviderInfo

    if (provider != other.provider) return false
    if (providerId != other.providerId) return false
    if (options != other.options) return false
    if (isEnabledByDefault != other.isEnabledByDefault) return false
    if (providerName != other.providerName) return false

    return true
  }

  override fun hashCode(): Int {
    var result = provider.hashCode()
    result = 31 * result + providerId.hashCode()
    result = 31 * result + options.hashCode()
    result = 31 * result + isEnabledByDefault.hashCode()
    result = 31 * result + providerName.hashCode()
    return result
  }
}

/**
 * @param optionToEnabled exhaustive set of options for a given provider
 */
class InlayProviderPassInfo(
  val provider: InlayHintsProvider,
  val providerId: String,
  val optionToEnabled: Map<String, Boolean>
)

class InlayOptionInfo(
  val id: String,
  val isEnabledByDefault: Boolean,
  val name: @Nls String
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as InlayOptionInfo

    if (id != other.id) return false
    if (isEnabledByDefault != other.isEnabledByDefault) return false
    if (name != other.name) return false

    return true
  }

  override fun hashCode(): Int {
    var result = id.hashCode()
    result = 31 * result + isEnabledByDefault.hashCode()
    result = 31 * result + name.hashCode()
    return result
  }
}