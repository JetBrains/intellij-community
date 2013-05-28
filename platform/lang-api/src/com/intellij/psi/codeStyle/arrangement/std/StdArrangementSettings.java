/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.codeStyle.arrangement.ArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 9/17/12 11:53 AM
 */
public class StdArrangementSettings implements ArrangementSettings {

  @NotNull private final   List<ArrangementGroupingRule> myGroupings       = new ArrayList<ArrangementGroupingRule>();
  @NotNull protected final List<StdArrangementMatchRule> myRules           = new ArrayList<StdArrangementMatchRule>();

  public StdArrangementSettings() {
  }

  @SuppressWarnings("unchecked")
  public StdArrangementSettings(@NotNull List<StdArrangementMatchRule> rules) {
    this(Collections.EMPTY_LIST, rules);
  }

  public StdArrangementSettings(@NotNull List<ArrangementGroupingRule> groupingRules,
                                @NotNull List<StdArrangementMatchRule> matchRules)
  {
    myGroupings.addAll(groupingRules);
    myRules.addAll(matchRules);
  }

  @NotNull
  @Override
  public List<StdArrangementMatchRule> getRules() {
    return myRules;
  }

  @Override
  @NotNull
  public List<ArrangementGroupingRule> getGroupings() {
    return myGroupings;
  }

  public void addRule(@NotNull StdArrangementMatchRule rule) {
    myRules.add(rule);
  }

  public void addGrouping(@NotNull ArrangementGroupingRule rule) {
    myGroupings.add(rule);
  }

  @Override
  public int hashCode() {
    int result = myRules.hashCode();
    result = 31 * result + myGroupings.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    StdArrangementSettings settings = (StdArrangementSettings)o;

    if (!myGroupings.equals(settings.myGroupings)) return false;
    if (!myRules.equals(settings.myRules)) return false;

    return true;
  }
}
