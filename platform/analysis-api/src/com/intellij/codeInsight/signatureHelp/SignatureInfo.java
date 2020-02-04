// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.signatureHelp;

import org.jetbrains.annotations.Nullable;

public final class SignatureInfo {

  @Nullable
  private final String myDocumentation;
  private final String myLabel;
  private final ParameterInfo[] myParameterInfos;

  public SignatureInfo(@Nullable String documentation,
                       String label,
                       ParameterInfo[] infos) {
    myDocumentation = documentation;
    myLabel = label;
    myParameterInfos = infos;
  }

  @Nullable
  public String getDocumentation() {
    return myDocumentation;
  }

  public String getLabel() {
    return myLabel;
  }

  public ParameterInfo[] getParameterInformation() {
    return myParameterInfos;
  }
}
