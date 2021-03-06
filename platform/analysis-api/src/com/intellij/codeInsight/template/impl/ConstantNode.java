// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class ConstantNode extends Expression {
  private final Result myValue;
  private final LookupElement[] myLookupElements;

  public ConstantNode(@NotNull String value) {
    this(new TextResult(value));
  }

  public ConstantNode(@Nullable Result value) {
    this(value, LookupElement.EMPTY_ARRAY);
  }

  private ConstantNode(@Nullable Result value, LookupElement @NotNull ... lookupElements) {
    myValue = value;
    myLookupElements = lookupElements;
  }

  public ConstantNode withLookupItems(LookupElement @NotNull ... lookupElements) {
    return new ConstantNode(myValue, lookupElements);
  }

  public ConstantNode withLookupItems(@NotNull Collection<? extends LookupElement> lookupElements) {
    return new ConstantNode(myValue, lookupElements.toArray(LookupElement.EMPTY_ARRAY));
  }

  public ConstantNode withLookupStrings(String @NotNull ... lookupElements) {
    return new ConstantNode(myValue, ContainerUtil.map2Array(lookupElements, LookupElement.class, LookupElementBuilder::create));
  }

  public ConstantNode withLookupStrings(@NotNull Collection<String> lookupElements) {
    return new ConstantNode(myValue, ContainerUtil.map2Array(lookupElements, LookupElement.class, LookupElementBuilder::create));
  }

  @Override
  public Result calculateResult(ExpressionContext context) {
    return myValue;
  }

  @Override
  public boolean requiresCommittedPSI() {
    return false;
  }

  @Override
  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    return myLookupElements;
  }

}
