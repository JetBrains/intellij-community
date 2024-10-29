// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;

/**
 * A container for intention actions to be shown in intention popup
 */
public interface IntentionContainer {
  /**
   * @return the title of the container; null if no title should be displayed
   */
  @Nullable @NlsContexts.PopupTitle String getTitle();

  /**
   * @return all the actions inside container sorted in the display order
   */
  @NotNull List<IntentionActionWithTextCaching> getAllActions();

  /**
   * @return error fixes (top-priority actions)
   */
  default @NotNull Set<IntentionActionWithTextCaching> getErrorFixes() {
    return Set.of();
  }

  /**
   * @return inspection (warning) fixes
   */
  default @NotNull Set<IntentionActionWithTextCaching> getInspectionFixes() {
    return Set.of();
  }

  /**
   * @param action action that belongs to this container (returned previously by {@link #getAllActions()})
   * @return action group; actions belonging to the same group can be visually grouped in UI
   */
  @NotNull IntentionGroup getGroup(@NotNull IntentionActionWithTextCaching action);

  /**
   * @param action action that belongs to this container (returned previously by {@link #getAllActions()})
   * @return an icon for the action; null if action has no custom icon
   */
  @Nullable Icon getIcon(@NotNull IntentionActionWithTextCaching action);
}
