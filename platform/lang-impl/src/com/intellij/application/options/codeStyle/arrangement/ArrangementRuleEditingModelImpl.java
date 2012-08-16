/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsAtomNode;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsCompositeNode;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNodeVisitor;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 8/15/12 2:40 PM
 */
public class ArrangementRuleEditingModelImpl implements ArrangementRuleEditingModel {

  @NotNull private final Set<Object> myConditions = new HashSet<Object>();
  @NotNull private ArrangementSettingsNode mySettingsNode;

  public ArrangementRuleEditingModelImpl(@NotNull ArrangementSettingsNode node) {
    mySettingsNode = node;
    node.invite(new ArrangementSettingsNodeVisitor() {
      @Override
      public void visit(@NotNull ArrangementSettingsAtomNode node) {
        myConditions.add(node.getValue());
      }

      @Override
      public void visit(@NotNull ArrangementSettingsCompositeNode node) {
        for (ArrangementSettingsNode operand : node.getOperands()) {
          operand.invite(this);
        } 
      }
    });
  }

  @NotNull
  @Override
  public ArrangementSettingsNode getSettingsNode() {
    return mySettingsNode;
  }

  @Override
  public boolean hasCondition(@NotNull Object key) {
    return myConditions.contains(key);
  }

  @Override
  public void addAndCondition(@NotNull ArrangementSettingsAtomNode node) {
    // TODO den implement 
  }

  @Override
  public void removeAndCondition(@NotNull ArrangementSettingsNode node) {
    // TODO den implement 
  }
}
