// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion;

import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public final class CompletionUtilCore {
  /**
   * A default string that is inserted to the file before completion to guarantee that there'll always be some non-empty element there
   */
  public static @NonNls final String DUMMY_IDENTIFIER = "IntellijIdeaRulezzz ";
  public static @NonNls final String DUMMY_IDENTIFIER_TRIMMED = "IntellijIdeaRulezzz";
}
