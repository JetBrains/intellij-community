// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractMethod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ReusedLocalVariable {
  private final @NotNull String myName;
  private final @Nullable String myTempName;
  private final @NotNull String myType;
  private final boolean myReuseValue;

  ReusedLocalVariable(@NotNull String name, @Nullable String tempName, @NotNull String type, boolean reuseValue) {
    assert reuseValue == (tempName != null);
    myName = name;
    myTempName = tempName;
    myType = type;
    myReuseValue = reuseValue;
  }

  String getDeclarationText() {
    String initText = myReuseValue ? " = " + myTempName : "";
    return myType + " " + myName + initText + ";";
  }

  String getAssignmentText() {
    return myTempName + " = " + myName + ";";
  }

  String getTempDeclarationText() {
    return myType + " " + myTempName + ";";
  }

  boolean reuseValue() {
    return myReuseValue;
  }
}
