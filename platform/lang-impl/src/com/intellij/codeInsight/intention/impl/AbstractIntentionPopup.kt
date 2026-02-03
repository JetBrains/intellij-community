// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl

import com.intellij.codeInsight.intention.IntentionSource
import com.intellij.openapi.Disposable
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.ui.awt.RelativePoint
import java.util.function.Consumer

/**
 * Represents an abstract popup interface that provides functionality to override
 * default popup behavior with custom logic, for example, to include intentions from different backends.
 */
interface AbstractIntentionPopup : Disposable {
  /**
   * Determines if the popup is currently visible.
   *
   * @return {@code true} if the popup is visible, {@code false} otherwise.
   */
  fun isVisible(): Boolean

  /**
   * Displays the popup with the specified customization options.
   *
   * @param hintComponent The component relative to which the popup will be displayed.
   * @param relativePoint The point relative to the {@code hintComponent} where the popup
   *                      will be shown. If {@code null}, the popup is shown at a default
   *                      position relative to the {@code hintComponent}.
   * @param listPopupCustomization An optional customization function that can be applied
   *                               to the {@link ListPopup} just before it is shown.
   *                               It can be {@code null} if no customization is needed.
   */
  fun show(
    hintComponent: IntentionHintComponent,
    relativePoint: RelativePoint?,
    listPopupCustomization: Consumer<in ListPopup>?,
    source: IntentionSource = IntentionSource.CONTEXT_ACTIONS
  )

  /**
   * Closes the popup if it is currently visible.
   */
  fun close()
}