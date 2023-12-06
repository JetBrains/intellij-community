// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NonNls;


public final class CompletionUtilCore {
  /**
   * A default string that is inserted to the file before completion to guarantee that there'll always be some non-empty element there
   */
  public static final @NonNls String DUMMY_IDENTIFIER = "IntellijIdeaRulezzz ";
  public static final @NonNls String DUMMY_IDENTIFIER_TRIMMED = "IntellijIdeaRulezzz";
}
