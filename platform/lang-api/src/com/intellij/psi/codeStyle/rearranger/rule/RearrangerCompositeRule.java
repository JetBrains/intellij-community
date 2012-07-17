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
package com.intellij.psi.codeStyle.rearranger.rule;

import com.intellij.psi.codeStyle.rearranger.RearrangerEntry;
import com.intellij.psi.codeStyle.rearranger.RearrangerRule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 7/17/12 11:26 AM
 */
public class RearrangerCompositeRule implements RearrangerRule {

  @NotNull private final List<RearrangerRule> myRules = new ArrayList<RearrangerRule>();
  @NotNull private final Operator myOperator;

  public RearrangerCompositeRule(@NotNull Operator operator, @NotNull RearrangerRule ... rules) {
    myOperator = operator;
    myRules.addAll(Arrays.asList(rules));
  }

  @Override
  public boolean isMatched(@NotNull RearrangerEntry entry) {
    for (RearrangerRule rule : myRules) {
      boolean matched = rule.isMatched(entry);
      if (matched && myOperator == Operator.OR) {
        return true;
      }
      else if (!matched && myOperator == Operator.AND) {
        return false;
      }
    }
    return myOperator == Operator.AND;
  }

  @NotNull
  public Operator getOperator() {
    return myOperator;
  }

  public void addRule(@NotNull RearrangerRule rule) {
    myRules.add(rule);
  }

  public enum Operator {AND, OR}
}
