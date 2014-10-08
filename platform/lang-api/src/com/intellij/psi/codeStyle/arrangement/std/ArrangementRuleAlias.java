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

import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class ArrangementRuleAlias implements Cloneable {
  /**
   * Token to be used in matching rules ui
   */
  private ArrangementSettingsToken myAliasToken;

  /**
   * All usages of alias token will be replaced by this sequence of rules
   */
  private List<StdArrangementMatchRule> myDefinitionRules;

  public ArrangementRuleAlias() {
  }

  public ArrangementRuleAlias(@NotNull ArrangementSettingsToken aliasToken,
                              @NotNull List<StdArrangementMatchRule> definitionRules) {
    myAliasToken = aliasToken;
    myDefinitionRules = definitionRules;
  }

  public ArrangementSettingsToken getAliasToken() {
    return myAliasToken;
  }

  public void setAliasToken(@NotNull ArrangementSettingsToken aliasToken) {
    myAliasToken = aliasToken;
  }

  public List<StdArrangementMatchRule> getDefinitionRules() {
    return myDefinitionRules;
  }

  public void setDefinitionRules(List<StdArrangementMatchRule> definitionRules) {
    myDefinitionRules = definitionRules;
  }

  @Override
  protected ArrangementRuleAlias clone() {
    final List<StdArrangementMatchRule> newValue = new ArrayList<StdArrangementMatchRule>(myDefinitionRules.size());
    for (StdArrangementMatchRule rule : myDefinitionRules) {
      newValue.add(rule.clone());
    }
    return new ArrangementRuleAlias(myAliasToken, newValue);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ArrangementRuleAlias token = (ArrangementRuleAlias)o;

    if (myAliasToken != null ? !myAliasToken.equals(token.myAliasToken) : token.myAliasToken != null) return false;
    if (myDefinitionRules != null ? !myDefinitionRules.equals(token.myDefinitionRules) : token.myDefinitionRules != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myAliasToken != null ? myAliasToken.hashCode() : 0;
    result = 31 * result + (myDefinitionRules != null ? myDefinitionRules.hashCode() : 0);
    return result;
  }
}
