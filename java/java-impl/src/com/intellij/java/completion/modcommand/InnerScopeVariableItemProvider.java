// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.daemon.impl.quickfix.BringVariableIntoScopeFix;
import com.intellij.java.JavaBundle;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
final class InnerScopeVariableItemProvider extends JavaModCompletionItemProvider {
  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    if (context.invocationCount() < 1) return;
    PsiElement position = context.getPosition();
    for (PsiLocalVariable variable : BringVariableIntoScopeFix.findInnerScopeVariables(position)) {
      sink.accept(new VariableCompletionItem(
        variable, JavaBundle.message("completion.inner.scope.tail.text", BringVariableIntoScopeFix.getVariableDeclarationPlace(variable))));
    }
  }
}
