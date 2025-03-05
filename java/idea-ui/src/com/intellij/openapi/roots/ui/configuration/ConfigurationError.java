// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Comparator;

public abstract class ConfigurationError implements Comparable<ConfigurationError> {
  private final String myPlainTextTitle;
  private final HtmlChunk myDescription;
  private boolean myIgnored;

  protected ConfigurationError(final String plainTextTitle, final @NotNull HtmlChunk description) {
    this(plainTextTitle, description, false);
  }

  protected ConfigurationError(final String plainTextTitle, final @NotNull HtmlChunk description, final boolean ignored) {
    myPlainTextTitle = plainTextTitle;
    myDescription = description;
    myIgnored = ignored;
  }

  public @NotNull String getPlainTextTitle() {
    return myPlainTextTitle;
  }

  public @NotNull HtmlChunk getDescription() {
    return myDescription;
  }

  /**
   * Called when user invokes "Ignore" action
   * @param b "true" if user invokes "Ignore", "false" if user wish to not ignore this error anymore
   */
  public void ignore(final boolean b) {
    if (b != myIgnored) {
      myIgnored = b;
    }
  }

  /**
   * @return "true" if this error is ignored
   */
  public boolean isIgnored() {
    return myIgnored;
  }

  /**
   * Called when user invokes "Fix" action
   */
  public void fix(JComponent contextComponent, RelativePoint relativePoint) {
  }

  public boolean canBeFixed() {
    return true;
  }

  public abstract void navigate();

  @Override
  public int compareTo(final ConfigurationError o) {
    if (myIgnored != o.isIgnored()) return -1;

    final int titleResult = getPlainTextTitle().compareTo(o.getPlainTextTitle());
    if (titleResult != 0) return titleResult;

    final int descriptionResult = Comparator.comparing(e -> getDescription().toString()).compare(this, o);
    if (descriptionResult != 0) return descriptionResult;

    return 0;
  }
}
