// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.codeInspection.util.IntentionFamilyName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * A common interface for two ways to define a registered intention action
 * (visible in Editor | Intentions): either legacy {@link IntentionAction},
 * or new {@link com.intellij.modcommand.ModCommandAction}
 * 
 * @see IntentionAction
 * @see com.intellij.modcommand.ModCommandAction
 */
@ApiStatus.NonExtendable
public interface CommonIntentionAction {
  /**
   * Returns the name of the family of intentions.
   * It is used to externalize the "auto-show" state of intentions.
   * When the user clicks on a light bulb in the intention list,
   * all intentions with the same family name get enabled/disabled.
   * The name is also shown in the Settings tree.
   *
   * @return the intention family name.
   */
  @NotNull
  @IntentionFamilyName
  @Contract(pure = true)
  String getFamilyName();

  /**
   * @return this action adapted to {@link IntentionAction} interface
   */
  @NotNull IntentionAction asIntention();
}
