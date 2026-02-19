// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push;

import org.jetbrains.annotations.NotNull;

/**
 * For a single repository, specifies what is pushed and where.
 */
public class PushSpec<S extends PushSource, T extends PushTarget> {

  private final @NotNull S mySource;
  private final @NotNull T myTarget;

  public PushSpec(@NotNull S source, @NotNull T target) {
    mySource = source;
    myTarget = target;
  }

  public @NotNull S getSource() {
    return mySource;
  }

  public @NotNull T getTarget() {
    return myTarget;
  }

  @Override
  public String toString() {
    return mySource + "->" + myTarget;
  }
}
