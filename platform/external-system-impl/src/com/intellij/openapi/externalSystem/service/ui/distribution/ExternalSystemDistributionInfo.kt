// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.ui.distribution

import org.jetbrains.annotations.Nls

abstract class ExternalSystemDistributionInfo {
  abstract val name: @Nls(capitalization = Nls.Capitalization.Sentence) String
  abstract val description: @Nls(capitalization = Nls.Capitalization.Sentence) String?

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ExternalSystemDistributionInfo

    if (name != other.name) return false
    if (description != other.description) return false

    return true
  }

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + (description?.hashCode() ?: 0)
    return result
  }
}