// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ObjectIntHashMap;
import com.intellij.util.containers.ObjectIntMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

/**
 * Wraps {@link ArrangementStandardSettingsAware} for the common arrangement UI managing code.
 */
public class ArrangementStandardSettingsManager {

  private final @NotNull ObjectIntMap<ArrangementSettingsToken> myWidths  = new ObjectIntHashMap<>();
  private final @NotNull ObjectIntMap<ArrangementSettingsToken> myWeights = new ObjectIntHashMap<>();

  private final @NotNull Comparator<ArrangementSettingsToken> myComparator = (t1, t2) -> {
    if (myWeights.containsKey(t1)) {
      if (myWeights.containsKey(t2)) {
        return myWeights.get(t1) - myWeights.get(t2);
      }
      else {
        return -1;
      }
    }
    else if (myWeights.containsKey(t2)) {
      return 1;
    }
    else {
      return t1.compareTo(t2);
    }
  };

  private final @NotNull ArrangementStandardSettingsAware          myDelegate;
  private final @NotNull ArrangementColorsProvider                 myColorsProvider;
  private final @NotNull Collection<Set<ArrangementSettingsToken>> myMutexes;

  private final @Nullable StdArrangementSettings                  myDefaultSettings;
  private final @Nullable List<CompositeArrangementSettingsToken> myGroupingTokens;
  private final @Nullable List<CompositeArrangementSettingsToken> myMatchingTokens;

  private final @NotNull Collection<StdArrangementRuleAliasToken> myRuleAliases;
  private final @NotNull Set<ArrangementSettingsToken> myRuleAliasMutex;
  private @Nullable CompositeArrangementSettingsToken myRuleAliasToken;

  public ArrangementStandardSettingsManager(@NotNull ArrangementStandardSettingsAware delegate,
                                            @NotNull ArrangementColorsProvider colorsProvider) {
    this(delegate, colorsProvider, ContainerUtil.emptyList());
  }

  public ArrangementStandardSettingsManager(@NotNull ArrangementStandardSettingsAware delegate,
                                            @NotNull ArrangementColorsProvider colorsProvider,
                                            @NotNull @Unmodifiable Collection<StdArrangementRuleAliasToken> aliases)
  {
    myDelegate = delegate;
    myColorsProvider = colorsProvider;
    myMutexes = delegate.getMutexes();
    myDefaultSettings = delegate.getDefaultSettings();

    SimpleColoredComponent renderer = new SimpleColoredComponent();
    myGroupingTokens = delegate.getSupportedGroupingTokens();
    if (myGroupingTokens != null) {
      parseWidths(myGroupingTokens, renderer);
      buildWeights(myGroupingTokens);
    }

    myMatchingTokens = delegate.getSupportedMatchingTokens();
    if (myMatchingTokens != null) {
      parseWidths(myMatchingTokens, renderer);
      buildWeights(myMatchingTokens);
    }

    final Set<ArrangementSettingsToken> aliasTokens = new HashSet<>(aliases);

    myRuleAliases = aliases;
    myRuleAliasMutex = aliasTokens;
    if (!myRuleAliases.isEmpty()) {
      myRuleAliasToken = new CompositeArrangementSettingsToken(StdArrangementTokens.General.ALIAS, aliasTokens);
    }
  }

  public @NotNull @Unmodifiable Collection<StdArrangementRuleAliasToken> getRuleAliases() {
    return myRuleAliases;
  }

  public @NotNull ArrangementStandardSettingsAware getDelegate() {
    return myDelegate;
  }

  private void parseWidths(@NotNull Collection<? extends CompositeArrangementSettingsToken> compositeTokens,
                           @NotNull SimpleColoredComponent renderer)
  {
    int width = 0;
    for (CompositeArrangementSettingsToken compositeToken : compositeTokens) {
      width = Math.max(width, parseWidth(compositeToken.getToken(), renderer));
    }
    for (CompositeArrangementSettingsToken compositeToken : compositeTokens) {
      myWidths.put(compositeToken.getToken(), width);
      parseWidths(compositeToken.getChildren(), renderer);
    }
  }

  private void buildWeights(@NotNull Collection<? extends CompositeArrangementSettingsToken> compositeTokens) {
    for (CompositeArrangementSettingsToken token : compositeTokens) {
      myWeights.put(token.getToken(), myWeights.size());
      buildWeights(token.getChildren());
    }
  }

  /**
   * @see ArrangementStandardSettingsAware#getDefaultSettings()
   */
  public @Nullable StdArrangementSettings getDefaultSettings() {
    return myDefaultSettings;
  }

  public boolean isSectionRulesSupported() {
    return myDelegate instanceof ArrangementSectionRuleAwareSettings;
  }

  /**
   * @see ArrangementStandardSettingsAware#getSupportedGroupingTokens()
   */
  public @Nullable List<CompositeArrangementSettingsToken> getSupportedGroupingTokens() {
    return myGroupingTokens;
  }

  /**
   * @see ArrangementStandardSettingsAware#getSupportedMatchingTokens()
   */
  public @Nullable List<CompositeArrangementSettingsToken> getSupportedMatchingTokens() {
    if (myMatchingTokens == null || myRuleAliasToken == null) {
      return myMatchingTokens;
    }

    final List<CompositeArrangementSettingsToken> allTokens = new ArrayList<>(myMatchingTokens);
    allTokens.add(myRuleAliasToken);
    return allTokens;
  }

  public boolean isEnabled(@NotNull ArrangementSettingsToken token, @Nullable ArrangementMatchCondition current) {
    if (myRuleAliasMutex.contains(token)) {
      return true;
    }
    return myDelegate.isEnabled(token, current);
  }

  public @NotNull ArrangementEntryMatcher buildMatcher(@NotNull ArrangementMatchCondition condition) throws IllegalArgumentException {
    ArrangementEntryMatcher matcher = ArrangementUtil.buildMatcher(condition);
    if (matcher == null) {
      matcher = myDelegate.buildMatcher(condition);
    }
    return matcher;
  }

  public @NotNull Collection<Set<ArrangementSettingsToken>> getMutexes() {
    if (myRuleAliasMutex.isEmpty()) {
      return myMutexes;
    }
    final List<Set<ArrangementSettingsToken>> allMutexes = new ArrayList<>(myMutexes);
    allMutexes.add(myRuleAliasMutex);
    return allMutexes;
  }

  public int getWidth(@NotNull ArrangementSettingsToken token) {
    if (myWidths.containsKey(token)) {
      return myWidths.get(token);
    }
    return parseWidth(token, new SimpleColoredComponent());
  }

  private int parseWidth(@NotNull ArrangementSettingsToken token, @NotNull SimpleColoredComponent renderer) {
    renderer.clear();
    final String value = getPresentationValue(token);
    renderer.append(value, SimpleTextAttributes.fromTextAttributes(myColorsProvider.getTextAttributes(token, true)));
    int result = renderer.getPreferredSize().width;

    renderer.clear();
    renderer.append(value, SimpleTextAttributes.fromTextAttributes(myColorsProvider.getTextAttributes(token, false)));
    return Math.max(result, renderer.getPreferredSize().width);
  }

  private static @NotNull @Nls String getPresentationValue(@NotNull ArrangementSettingsToken token) {
    if (token instanceof InvertibleArrangementSettingsToken) {
      return ((InvertibleArrangementSettingsToken)token).getInvertedRepresentationValue();
    }
    return token.getRepresentationValue();
  }

  public List<ArrangementSettingsToken> sort(@NotNull Collection<? extends ArrangementSettingsToken> tokens) {
    List<ArrangementSettingsToken> result = new ArrayList<>(tokens);
    result.sort(myComparator);
    return result;
  }
}
