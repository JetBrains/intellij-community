// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.completion.scope;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;

/**
 * @author yole
 */
public final class JavaCompletionHints {
  public static final Key<Condition<String>> NAME_FILTER = Key.create("NAME_FILTER");

  private JavaCompletionHints() {
  }
}
