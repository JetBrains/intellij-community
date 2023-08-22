// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A command that displays a UI and allows users to select a subsequent action from the list.
 * Intention preview assumes that the first available action is selected by default.
 * In batch mode, the first option is also selected automatically.
 * 
 * @param title title to display to the user
 * @param actions actions to select from. If there's only one action, then it could be executed right away without asking the user. 
 */
public record ModChooseAction(@NotNull @NlsContexts.PopupTitle String title, 
                              @NotNull List<? extends @NotNull ModCommandAction> actions) implements ModCommand {
  @Override
  public boolean isEmpty() {
    return actions.isEmpty();
  }
}
