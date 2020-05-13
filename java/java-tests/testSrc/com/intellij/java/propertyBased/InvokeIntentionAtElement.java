// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.propertyBased;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.propertyBased.IntentionPolicy;
import com.intellij.testFramework.propertyBased.InvokeIntention;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jetCheck.Generator;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public class InvokeIntentionAtElement extends InvokeIntention {
  private final Function<? super PsiFile, ? extends Collection<PsiElement>> myExtractor;

  public <T extends PsiElement> InvokeIntentionAtElement(@NotNull PsiFile file,
                                  @NotNull IntentionPolicy policy,
                                  @NotNull Class<T> elementClass,
                                  @NotNull Function<? super T, ? extends PsiElement> adjuster) {
    this(file, policy, f -> ContainerUtil.map(PsiTreeUtil.findChildrenOfType(f, elementClass), adjuster::apply));
  }

  public InvokeIntentionAtElement(@NotNull PsiFile file,
                                  @NotNull IntentionPolicy policy,
                                  @NotNull Function<? super PsiFile, ? extends Collection<PsiElement>> extractor) {
    super(file, policy);
    myExtractor = extractor;
  }

  @Override
  protected int generateDocOffset(@NotNull Environment env, @Nullable String logMessage) {
    Collection<PsiElement> children = myExtractor.apply(getFile());
    if (children.isEmpty()) {
      return super.generateDocOffset(env, logMessage);
    }

    List<Generator<Integer>> generators =
      ContainerUtil.map(children, stmt -> {
        TextRange range = stmt.getTextRange();
        return Generator.integers(range.getStartOffset(),
                                  range.getEndOffset()).noShrink();
      });
    return env.generateValue(Generator.anyOf(generators), logMessage);
  }

}
