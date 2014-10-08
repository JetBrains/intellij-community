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
package com.intellij.application.options.codeStyle.arrangement.match.tokens;

import com.intellij.application.options.codeStyle.arrangement.color.ArrangementColorsProvider;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.ui.NamedItemsListEditor;
import com.intellij.openapi.ui.Namer;
import com.intellij.openapi.util.Cloner;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.codeStyle.arrangement.ArrangementUtil;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementRuleAlias;
import com.intellij.psi.codeStyle.arrangement.std.ArrangementStandardSettingsManager;
import gnu.trove.Equality;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class ArrangementRuleAliasesListEditor extends NamedItemsListEditor<ArrangementRuleAlias> {
  private static final Namer<ArrangementRuleAlias> NAMER = new Namer<ArrangementRuleAlias>() {
    @Override
    public String getName(ArrangementRuleAlias token) {
      return token.getAliasToken().getRepresentationValue();
    }

    @Override
    public boolean canRename(ArrangementRuleAlias item) {
      return false;
    }

    @Override
    public void setName(ArrangementRuleAlias token, String name) {
      token.setAliasToken(ArrangementUtil.createRuleAliasToken(name.replaceAll("\\s+", "_"), name.replaceAll("\\s+", " ")));
    }
  };
  private static final Factory<ArrangementRuleAlias> FACTORY = new Factory<ArrangementRuleAlias>() {
    @Override
    public ArrangementRuleAlias create() {
      return new ArrangementRuleAlias();
    }
  };
  private static final Cloner<ArrangementRuleAlias> CLONER = new Cloner<ArrangementRuleAlias>() {
    @Override
    public ArrangementRuleAlias cloneOf(ArrangementRuleAlias original) {
      final ArrangementRuleAlias token = copyOf(original);
      token.setAliasToken(original.getAliasToken());
      return token;
    }

    @Override
    public ArrangementRuleAlias copyOf(ArrangementRuleAlias original) {
      final ArrangementRuleAlias token = new ArrangementRuleAlias();
      token.setDefinitionRules(original.getDefinitionRules());
      return token;
    }
  };
  private static final Equality<ArrangementRuleAlias> COMPARER = new Equality<ArrangementRuleAlias>() {
    @Override
    public boolean equals(ArrangementRuleAlias o1, ArrangementRuleAlias o2) {
      return Comparing.equal(o1.getAliasToken().getId(), o2.getAliasToken().getId());
    }
  };

  @NotNull private Set<String> myUsedTokenIds;
  @NotNull private ArrangementStandardSettingsManager mySettingsManager;
  @NotNull private ArrangementColorsProvider myColorsProvider;

  protected ArrangementRuleAliasesListEditor(@NotNull ArrangementStandardSettingsManager settingsManager,
                                             @NotNull ArrangementColorsProvider colorsProvider,
                                             @NotNull List<ArrangementRuleAlias> items,
                                             @NotNull Set<String> usedTokenIds) {
    super(NAMER, FACTORY, CLONER, COMPARER, items, false);
    mySettingsManager = settingsManager;
    myColorsProvider = colorsProvider;
    myUsedTokenIds = usedTokenIds;
    reset();
    initTree();
  }

  @Override
  protected UnnamedConfigurable createConfigurable(ArrangementRuleAlias item) {
    return new ArrangementRuleAliasConfigurable(mySettingsManager, myColorsProvider, item);
  }

  @Override
  protected boolean canDelete(ArrangementRuleAlias item) {
    return !myUsedTokenIds.contains(item.getAliasToken().getId());
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Custom Composite Tokens";
  }
}
