// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push

import com.intellij.dvcs.push.ui.VcsPushUi
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Implement this extension point to customize look and behaviour of the Push Dialog.
 * Note that only one PushDialogCustomizer is allowed. Otherwise if there are several customizers, none will be enabled.
 *
 * @see PushDialogActionsProvider
 */
@ApiStatus.Internal
interface PushDialogCustomizer {

  /**
   * Use another name for the default plain push action instead of "Push".
   */
  @Nls
  fun getNameForSimplePushAction(dialog: VcsPushUi): String
}