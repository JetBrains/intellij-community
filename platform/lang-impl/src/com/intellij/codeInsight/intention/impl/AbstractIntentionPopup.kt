// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.awt.RelativePoint

interface AbstractIntentionPopup : Disposable {
  fun isVisible(): Boolean
  fun show(hintComponent: IntentionHintComponent, relativePoint: RelativePoint?, listPopupCustomization: ((ListPopup) -> Unit)?)
  fun close()
}