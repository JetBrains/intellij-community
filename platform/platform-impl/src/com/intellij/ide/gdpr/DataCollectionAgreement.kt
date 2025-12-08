// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.gdpr

import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.ui.LicensingFacade
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@IntellijInternalApi
enum class DataCollectionAgreement(private val code: Char) {
  YES('Y'),
  NOT_SET('X'),
  NO('N');

  companion object {
    private const val METADATA_LICENSE_POSITION = 27

    @JvmStatic
    fun getInstance(): DataCollectionAgreement? {
      val metadata = LicensingFacade.getInstance()?.metadata ?: return null
      if (metadata.length <= METADATA_LICENSE_POSITION) return null
      return entries.firstOrNull { it.code == metadata[METADATA_LICENSE_POSITION] }
    }
  }
}