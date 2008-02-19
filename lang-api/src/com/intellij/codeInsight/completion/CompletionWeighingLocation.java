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
public class CompletionWeighingLocation extends UserDataHolderBase{
  private final CompletionType myCompletionType;
  private final String myPrefix;
  private final CompletionParameters myCompletionParameters;

  public CompletionWeighingLocation(final CompletionType completionType, final String prefix,
                                    final CompletionParameters completionParameters) {
    myCompletionType = completionType;
    myPrefix = prefix;
    myCompletionParameters = completionParameters;
  }

  public String getPrefix() {
    return myPrefix;
  }

  public CompletionParameters getCompletionParameters() {
    return myCompletionParameters;
  }

  public CompletionType getCompletionType() {
    return myCompletionType;
  }

  public Project getProject() {
    return myCompletionParameters.getPosition().getProject();
  }
}
