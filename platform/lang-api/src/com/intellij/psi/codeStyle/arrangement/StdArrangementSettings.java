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
package com.intellij.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.group.ArrangementGroupingType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 9/17/12 11:53 AM
 */
public class StdArrangementSettings implements ArrangementSettings {

  @NotNull private final List<StdArrangementRule>      myRules     = new ArrayList<StdArrangementRule>();
  @NotNull private final List<ArrangementGroupingType> myGroupings = new ArrayList<ArrangementGroupingType>();

  public StdArrangementSettings() {
  }

  public StdArrangementSettings(@NotNull Collection<StdArrangementRule> rules) {
    myRules.addAll(rules);
  }

  @NotNull
  @Override
  public List<StdArrangementRule> getRules() {
    return myRules;
  }

  @NotNull
  public List<ArrangementGroupingType> getGroupings() {
    return myGroupings;
  }

  public void addRule(@NotNull StdArrangementRule rule) {
    myRules.add(rule);
  }

  public void addGrouping(@NotNull ArrangementGroupingType grouping) {
    myGroupings.add(grouping);
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
