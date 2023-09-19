// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.framework;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class FrameworkType {
  private final String myId;

  protected FrameworkType(@NotNull String id) {
    myId = id;
  }

  @Contract(pure = true)
  public abstract @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getPresentableName();

  @Contract(pure = true)
  public abstract @NotNull Icon getIcon();

  public final @NotNull String getId() {
    return myId;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return myId.equals(((FrameworkType)o).myId);
  }

  @Override
  public final int hashCode() {
    return myId.hashCode();
  }
}
