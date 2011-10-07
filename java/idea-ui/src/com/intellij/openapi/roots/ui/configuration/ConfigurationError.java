/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: spLeaner
 */
public abstract class ConfigurationError implements Comparable<ConfigurationError> {
  private final String myPlainTextTitle;
  private final String myDescription;
  private boolean myIgnored;

  protected ConfigurationError(final String plainTextTitle, final String description) {
    this(plainTextTitle, description, false);
  }

  protected ConfigurationError(final String plainTextTitle, final String description, final boolean ignored) {
    myPlainTextTitle = plainTextTitle;
    myDescription = description;
    myIgnored = ignored;
  }

  @NotNull
  public String getPlainTextTitle() {
    return myPlainTextTitle;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  /**
   * Called when user invokes "Ignore" action
   * @param "true" if user invokes "Ignore", "false" if user wish to not ignore this error anymore
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

    final int descriptionResult = getDescription().compareTo(o.getDescription());
    if (descriptionResult != 0) return descriptionResult;

    return 0;
  }
}
