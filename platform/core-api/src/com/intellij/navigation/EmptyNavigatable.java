// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.navigation;

import com.intellij.pom.Navigatable;
import com.intellij.util.IncorrectOperationException;

public final class EmptyNavigatable implements Navigatable {

  public static final Navigatable INSTANCE = new EmptyNavigatable();

  private EmptyNavigatable() {}

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public void navigate(boolean requestFocus) {
    throw new IncorrectOperationException("Must not call #navigate() if #canNavigate() returns 'false'");
  }
}
