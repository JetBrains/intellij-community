// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionResult;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.impl.source.PsiLabelReference;
import org.jetbrains.annotations.NotNullByDefault;

@NotNullByDefault
final class LabelReferenceItemProvider extends JavaModCompletionItemProvider {
  @Override
  public void provideItems(CompletionContext context, ModCompletionResult sink) {
    if (context.getPosition() instanceof PsiIdentifier id && id.getParent().getReference() instanceof PsiLabelReference labelRef) {
      for (String variant : labelRef.getVariants()) {
        sink.accept(new CommonCompletionItem(variant).withTail(ModNavigatorTailType.semicolonType()));
      }
    }
  }
}
