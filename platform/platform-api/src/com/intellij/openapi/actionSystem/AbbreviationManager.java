// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Manages abbreviations for actions. An abbreviation is an alias for the action name
 * which the user can enter in Goto Action/Search Everywhere popups.
 *
 * @author Konstantin Bulenkov
 */
public abstract class AbbreviationManager {
  public static AbbreviationManager getInstance() {
    return ApplicationManager.getApplication().getService(AbbreviationManager.class);
  }

  public abstract @NotNull Set<String> getAbbreviations();

  public abstract @NotNull Set<String> getAbbreviations(@NotNull String actionId);

  public abstract @NotNull List<String> findActions(@NotNull String abbreviation);

  public abstract void register(@NotNull String abbreviation, @NotNull String actionId);

  public abstract void remove(@NotNull String abbreviation, @NotNull String actionId);

  public abstract void removeAllAbbreviations(@NotNull String actionId);
}
