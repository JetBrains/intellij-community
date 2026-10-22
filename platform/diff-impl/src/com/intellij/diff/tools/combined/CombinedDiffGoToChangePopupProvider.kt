// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Supplies the "Go to Changed File" action of a combined diff when it's not set explicitly by the [CombinedDiffManager.createProcessor] caller.
 *
 * The VCS module uses it to show its changes tree popup. Without a provider the combined diff shows a plain block list.
 */
@ApiStatus.Internal
interface CombinedDiffGoToChangePopupProvider {
  /** Returns `null` when this provider has no popup for the blocks of [model]. */
  fun createGoToChangeAction(model: CombinedDiffModel): AnAction?

  companion object {
    val EP_NAME: ExtensionPointName<CombinedDiffGoToChangePopupProvider> =
      ExtensionPointName.create("com.intellij.diff.combinedGoToChangePopupProvider")
  }
}
