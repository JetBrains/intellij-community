// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * It's allowed to assign multiple actions to the same keyboard shortcut. Actions system filters them on the current
 * context basis during processing (e.g., we can have two actions assigned to the same shortcut, but one of them is
 * configured to be inapplicable in modal dialog context).
 * <p>
 * However, there is a possible case that more than one action is applicable for a particular keyboard shortcut
 * after filtering. The first one is executed then. Hence, action processing order becomes very important.
 * <p>
 * The current extension point allows specifying custom action sorter to use, if any. I.e., every component can define its custom
 * sorting rule to define priorities for target actions (classes of actions).
 * <p>
 * If {@link AnAction} implements {@link ActionPromoter}, promoter would be used to rearrange this action (no need to register additionally as extension point)
 *
 * @author Konstantin Bulenkov
 */
public interface ActionPromoter {
  ExtensionPointName<ActionPromoter> EP_NAME = ExtensionPointName.create("com.intellij.actionPromoter");

  /**
   * Override and return the list of actions to try first.
   * <p>
   * The method must be fast, must not do reference resolution, reparsing, etc.
   * To ensure that the provided {@code context} returns only the already cached UI data.
   * <p>
   * For example, {@link PlatformCoreDataKeys#CONTEXT_COMPONENT}
   * and {@link PlatformCoreDataKeys#FILE_EDITOR} keys are available,
   * but {@link CommonDataKeys#PSI_FILE} is not.
   */
  @Unmodifiable
  default @Nullable List<AnAction> promote(@NotNull List<? extends AnAction> actions, @NotNull DataContext context) {
    return null;
  }

  /**
   * Override and return the list of actions to remove from processing.
   * <p>
   * See {@link #promote(List, DataContext)} javadoc notes on performance and the provided {@link DataContext}.
   */
  default @Nullable List<AnAction> suppress(@NotNull List<? extends AnAction> actions, @NotNull DataContext context) {
    return null;
  }
}
