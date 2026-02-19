// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.command;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Covers element help: text and external url (if exists)
 *
 * @author Ilya.Kazakevich
 */
public final class Help {
  private final @NotNull @NlsSafe String myHelpString;
  private final @Nullable @NlsSafe String myExternalHelpUrl;

  /**
   * @param helpString help text (no external url provided)
   */
  public Help(@NotNull @NlsSafe String helpString) {
    this(helpString, null);
  }

  /**
   * @param helpString      help text
   * @param externalHelpUrl external help url (if any)
   */
  public Help(@NotNull @NlsSafe String helpString, @Nullable @NlsSafe String externalHelpUrl) {
    myHelpString = helpString;
    myExternalHelpUrl = externalHelpUrl;
  }

  /**
   * @return help text
   */
  public @NotNull @NlsSafe String getHelpString() {
    return myHelpString;
  }

  /**
   * @return external help url (if any)
   */
  public @Nullable @NlsSafe String getExternalHelpUrl() {
    return myExternalHelpUrl;
  }
}
