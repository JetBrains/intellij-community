// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.completion.CompletionSorter;
import com.intellij.codeInsight.completion.JavaCompletionSorting;
import com.intellij.modcompletion.ModCompletionItemProvider;
import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
abstract class JavaModCompletionItemProvider implements ModCompletionItemProvider {
  @Override
  public CompletionSorter getSorter(CompletionContext context) {
    return JavaCompletionSorting.getSorter(context, context.matcher());
  }
}
