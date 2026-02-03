// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.push

import com.intellij.dvcs.push.ui.PushActionBase
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

/**
 * Provides additional actions to the "Push" combo-button in the Push Dialog.
 *
 * If there are several extensions implementing this interface, the order of groups of actions is defined by the order of extensions.
 *
 * @see PushDialogCustomizer
 */
@ApiStatus.Internal
interface PushDialogActionsProvider {

  /**
   * Returns the list of actions which will be located above the default "Push" action.
   * The action which is first in the list will be the default action of the combo-button.
   */
  fun getCustomActionsAboveDefault(project: Project): List<PushActionBase>

}