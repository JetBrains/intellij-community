// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
class ReusedLocalVariable {
  @NotNull private final String myName;
  @Nullable private final String myTempName;
  @NotNull private final String myType;
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
