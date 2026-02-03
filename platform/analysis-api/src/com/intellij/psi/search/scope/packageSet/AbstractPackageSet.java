// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope.packageSet;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public abstract class AbstractPackageSet extends PackageSetBase {
  private final String myText;
  private final int myPriority;

  public AbstractPackageSet(@NotNull @NonNls String text) {
    this(text, 1);
  }

  public AbstractPackageSet(@NotNull @NonNls String text, int priority) {
    myText = text;
    myPriority = priority;
  }

  @Override
  public @NotNull AbstractPackageSet createCopy() {
    return this;
  }

  @Override
  public int getNodePriority() {
    return myPriority;
  }

  @Override
  public @NotNull String getText() {
    return myText;
  }
}
