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
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 7/17/12 11:26 AM
 */
public class CompositeArrangementEntryMatcher implements ArrangementEntryMatcher {

  @NotNull private final Set<ArrangementEntryMatcher> myMatchers = new HashSet<ArrangementEntryMatcher>();
  @NotNull private final Operator myOperator;

  public CompositeArrangementEntryMatcher(@NotNull Operator operator, @NotNull ArrangementEntryMatcher... matchers) {
    myOperator = operator;
    myMatchers.addAll(Arrays.asList(matchers));
  }

  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    for (ArrangementEntryMatcher matcher : myMatchers) {
      boolean matched = matcher.isMatched(entry);
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

  @NotNull
  public Collection<ArrangementEntryMatcher> getMatchers() {
    return myMatchers;
  }

  public void addMatcher(@NotNull ArrangementEntryMatcher rule) {
    myMatchers.add(rule);
  }

  @Override
  public int hashCode() {
    int result = myMatchers.hashCode();
    result = 31 * result + myOperator.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    CompositeArrangementEntryMatcher matcher = (CompositeArrangementEntryMatcher)o;

    if (!myMatchers.equals(matcher.myMatchers)) return false;
    if (myOperator != matcher.myOperator) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("%s of those: %s", myOperator == Operator.AND ? "all" : "any", myMatchers);
  }

  public enum Operator {AND, OR}
}
