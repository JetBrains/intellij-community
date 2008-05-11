/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion;

/**
 * @author peter
 */
public class JavaSmartCompletionParameters extends CompletionParameters{
  public JavaSmartCompletionParameters(CompletionParameters parameters) {
    super(parameters.getPosition(), parameters.getOriginalFile(), parameters.getCompletionType(), parameters.getOffset(), parameters.getInvocationCount());
  }
}
