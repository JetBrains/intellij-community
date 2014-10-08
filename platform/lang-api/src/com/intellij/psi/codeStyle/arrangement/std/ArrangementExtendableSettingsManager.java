/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.codeStyle.arrangement.std;

import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Extension for {@link ArrangementStandardSettingsManager} for managing matching rules with custom aliases
 *
 * @author Svetlana.Zemlyanskaya
 */
public class ArrangementExtendableSettingsManager extends ArrangementStandardSettingsManager {
  @NotNull private Collection<ArrangementRuleAlias> myRuleAliases;

  @NotNull private CompositeArrangementSettingsToken myRuleAliasToken;
  @NotNull private Set<ArrangementSettingsToken> myRuleAliasMutex;


  public ArrangementExtendableSettingsManager(@NotNull ArrangementStandardSettingsAware delegate,
                                              @NotNull ArrangementColorsProvider colorsProvider,
                                              @NotNull Collection<ArrangementRuleAlias> aliases) {
    super(delegate, colorsProvider);
    final Set<ArrangementSettingsToken> tokensSet = ContainerUtil.newHashSet();
    for (ArrangementRuleAlias token : aliases) {
      tokensSet.add(token.getAliasToken());
    }

    myRuleAliases = aliases;
    myRuleAliasToken = createAliasTokenRepresentation(tokensSet);
    myRuleAliasMutex = tokensSet;
  }

  @NotNull
  private static CompositeArrangementSettingsToken createAliasTokenRepresentation(@NotNull Collection<ArrangementSettingsToken> tokens) {
    return new CompositeArrangementSettingsToken(StdArrangementTokens.General.ALIAS, tokens);
  }

  @NotNull
  public Collection<ArrangementRuleAlias> getRuleAliases() {
    return myRuleAliases;
  }

  @Nullable
  public List<CompositeArrangementSettingsToken> getSupportedMatchingTokens() {
    final List<CompositeArrangementSettingsToken> tokens = super.getSupportedMatchingTokens();
    if (tokens == null) {
      return null;
    }

    final List<CompositeArrangementSettingsToken> allTokens = ContainerUtil.newArrayList(tokens);
    allTokens.add(myRuleAliasToken);
    return allTokens;
  }

  public boolean isEnabled(@NotNull ArrangementSettingsToken token, @Nullable ArrangementMatchCondition current) {
    if (myRuleAliasMutex.contains(token)) {
      return true;
    }
    return super.isEnabled(token, current);
  }

  @NotNull
  public Collection<Set<ArrangementSettingsToken>> getMutexes() {
    final List<Set<ArrangementSettingsToken>> allMutexes = ContainerUtil.newArrayList(super.getMutexes());
    allMutexes.add(myRuleAliasMutex);
    return allMutexes;
  }
}
