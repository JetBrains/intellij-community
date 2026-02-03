// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.ui.model

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PlaceholderCodeVisionEntry(providerId: String) : TextCodeVisionEntry("", providerId) {
  init {
    showInMorePopup = false
  }
}