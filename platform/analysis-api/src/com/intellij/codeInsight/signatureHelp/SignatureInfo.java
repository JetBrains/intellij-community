// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.signatureHelp;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SignatureInfo {

  @Nullable
  private final String myDocumentation;
  private final String myLabel;
  private final List<ParameterInfo> myParameterInfos;
  private final int myHighlightedParam;

  public SignatureInfo(@Nullable String documentation, String label, List<ParameterInfo> infos, int highlightedParamIndex) {
    myDocumentation = documentation;
    myLabel = label;
    myParameterInfos = Collections.unmodifiableList(new ArrayList<>(infos));
    myHighlightedParam = highlightedParamIndex;
  }

  @Nullable
  public String getDocumentation() {
    return myDocumentation;
  }

  public String getLabel() {
    return myLabel;
  }

  public List<ParameterInfo> getParameterInformation() {
    return myParameterInfos;
  }

  public int getHighlightedParam() {
    return myHighlightedParam;
  }

  @Override
  public String toString() {
    return "SignatureInfo{" +
           "doc='" + myDocumentation + '\'' +
           ", label='" + myLabel + '\'' +
           ", paramInfo=" + myParameterInfos +
           ", highlighted=" + myHighlightedParam +
           '}';
  }
}
