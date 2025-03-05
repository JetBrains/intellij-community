// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.util.duplicates;

import org.jetbrains.annotations.NonNls;

public class ContinueReturnValue extends GotoReturnValue {
  @Override
  public boolean isEquivalent(final ReturnValue other) {
    return other instanceof ContinueReturnValue;
  }


  @Override
  public @NonNls String getGotoStatement() {
    return "if (a) continue;";
  }
}