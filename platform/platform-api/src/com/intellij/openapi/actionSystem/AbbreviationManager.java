// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * Manages abbreviations for actions. An abbreviation is an alias for the action name
 * which the user can enter in the Goto Action/Search Everywhere popups.
 *
 * @author Konstantin Bulenkov
 */
public abstract class AbbreviationManager {
  public static AbbreviationManager getInstance() {
    return ApplicationManager.getApplication().getService(AbbreviationManager.class);
  }

  @NotNull
  public abstract Set<String> getAbbreviations();

  @NotNull
  public abstract Set<String> getAbbreviations(@NotNull String actionId);

  @NotNull
  public abstract List<String> findActions(@NotNull String abbreviation);

  public abstract void register(@NotNull String abbreviation, @NotNull String actionId);

  public abstract void remove(@NotNull String abbreviation, @NotNull String actionId);

  public abstract void removeAllAbbreviations(@NotNull String actionId);
}
