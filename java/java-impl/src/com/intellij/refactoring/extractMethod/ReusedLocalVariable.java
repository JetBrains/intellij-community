// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod;

import com.intellij.psi.PsiKeyword;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public class ReusedLocalVariable {
  @NotNull private final String myName;
  @Nullable private final String myTempName;
  @NotNull private final String myType;
  private final boolean myReuseValue;

  public ReusedLocalVariable(@NotNull String name, @Nullable String tempName, @NotNull String type, boolean reuseValue) {
    assert reuseValue == (tempName != null);
    myName = name;
    myTempName = tempName;
    myType = type;
    myReuseValue = reuseValue;
  }

  public String getDeclarationText() {
    String initText = myReuseValue ? " = " + myTempName : "";
    return myType + " " + myName + initText + ";";
  }

  public String getAssignmentText() {
    return myTempName + " = " + myName + ";";
  }

  public String getTempDeclarationText() {
    return myType + " " + myTempName + ";";
  }

  public boolean reuseValue() {
    return myReuseValue;
  }
}
