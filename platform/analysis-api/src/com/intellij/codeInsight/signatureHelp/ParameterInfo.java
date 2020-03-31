// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.signatureHelp;

import org.jetbrains.annotations.Nullable;

public final class ParameterInfo {

  @Nullable
  private final String myDocumentation;
  private final String myLabel;

  public ParameterInfo(@Nullable String documentation, String label) {
    myDocumentation = documentation;
    myLabel = label;
  }

  @Nullable
  public String getDocumentation() {
    return myDocumentation;
  }

  public String getLabel() {
    return myLabel;
  }

  @Override
  public String toString() {
    return "Param{" +
           "doc='" + myDocumentation + '\'' +
           ", label='" + myLabel + '\'' +
           '}';
  }
}
