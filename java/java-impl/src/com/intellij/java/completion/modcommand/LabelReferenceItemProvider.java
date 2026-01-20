// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.completion.modcommand;

import com.intellij.codeInsight.ModNavigatorTailType;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.impl.source.PsiLabelReference;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.function.Consumer;

@NotNullByDefault
final class LabelReferenceItemProvider extends JavaModCompletionItemProvider {
  @Override
  public void provideItems(CompletionContext context, Consumer<ModCompletionItem> sink) {
    if (context.getPosition() instanceof PsiIdentifier id && id.getParent().getReference() instanceof PsiLabelReference labelRef) {
      for (String variant : labelRef.getVariants()) {
        sink.accept(new CommonCompletionItem(variant).withTail(ModNavigatorTailType.semicolonType()));
      }
    }
  }
}
