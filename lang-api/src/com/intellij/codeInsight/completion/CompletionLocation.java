/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;

/**
 * @author peter
 */
public class CompletionLocation extends UserDataHolderBase {
  private String myPrefix;
  private final CompletionParameters myCompletionParameters;

  public CompletionLocation(final String prefix, final CompletionParameters completionParameters) {
    myPrefix = prefix;
    myCompletionParameters = completionParameters;
  }

  public String getPrefix() {
    return myPrefix;
  }

  public void setPrefix(final String prefix) {
    myPrefix = prefix;
  }

  public CompletionParameters getCompletionParameters() {
    return myCompletionParameters;
  }

  public CompletionType getCompletionType() {
    return myCompletionParameters.getCompletionType();
  }

  public Project getProject() {
    return myCompletionParameters.getPosition().getProject();
  }
}
