// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public final class ConstantNode extends Expression {
  private final Result myValue;
  private final LookupElement[] myLookupElements;

  private final @Nullable @NlsContexts.PopupAdvertisement String myPopupAdvertisement;
  private final @NotNull LookupFocusDegree myLookupFocusDegree;

  public ConstantNode(@NotNull String value) {
    this(new TextResult(value));
  }

  public ConstantNode(@Nullable Result value) {
    this(value, LookupElement.EMPTY_ARRAY);
  }

  private ConstantNode(@Nullable Result value, LookupElement @NotNull ... lookupElements) {
    this(value, null, lookupElements);
  }

  private ConstantNode(@Nullable Result value, @Nullable @NlsContexts.PopupAdvertisement String popupAdvertisement, LookupElement @NotNull ... lookupElements) {
    this(value, popupAdvertisement, LookupFocusDegree.FOCUSED, lookupElements);
  }

  private ConstantNode(@Nullable Result value,
                       @Nullable @NlsContexts.PopupAdvertisement String popupAdvertisement,
                       @NotNull LookupFocusDegree lookupFocusDegree,
                       LookupElement @NotNull ... lookupElements) {
    myValue = value;
    myPopupAdvertisement = popupAdvertisement;
    myLookupFocusDegree = lookupFocusDegree;
    myLookupElements = lookupElements;
  }

  public ConstantNode withLookupItems(LookupElement @NotNull ... lookupElements) {
    return new ConstantNode(myValue, myPopupAdvertisement, myLookupFocusDegree, lookupElements);
  }

  public ConstantNode withLookupItems(@NotNull Collection<? extends LookupElement> lookupElements) {
    return new ConstantNode(myValue, myPopupAdvertisement, myLookupFocusDegree, lookupElements.toArray(LookupElement.EMPTY_ARRAY));
  }

  public ConstantNode withLookupStrings(String @NotNull ... lookupElements) {
    return new ConstantNode(myValue, myPopupAdvertisement, myLookupFocusDegree,
                            ContainerUtil.map2Array(lookupElements, LookupElement.class, LookupElementBuilder::create));
  }

  public ConstantNode withLookupStrings(@NotNull Collection<String> lookupElements) {
    return new ConstantNode(myValue, myPopupAdvertisement, myLookupFocusDegree,
                            ContainerUtil.map2Array(lookupElements, LookupElement.class, LookupElementBuilder::create));
  }

  public ConstantNode withPopupAdvertisement(@Nullable @NlsContexts.PopupAdvertisement String popupAdvertisement) {
    return new ConstantNode(myValue, popupAdvertisement, myLookupFocusDegree, myLookupElements);
  }

  public ConstantNode withLookupFocusDegree(@NotNull LookupFocusDegree lookupFocusDegree) {
    return new ConstantNode(myValue, myPopupAdvertisement, lookupFocusDegree, myLookupElements);
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

  @Override
  public @Nullable @NlsContexts.PopupAdvertisement String getAdvertisingText() {
    return myPopupAdvertisement;
  }

  @Override
  public @NotNull LookupFocusDegree getLookupFocusDegree() {
    return myLookupFocusDegree;
  }
}
