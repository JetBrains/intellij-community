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
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchConditionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/**
 * {@link ArrangementEntryMatcher} which is based on standard match conditions like {@link ArrangementEntryType entry type}
 * or {@link ArrangementModifier modifier}.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/26/12 11:07 PM
 */
public class StandardArrangementEntryMatcher implements ArrangementEntryMatcher {

  @NotNull private final ArrangementMatchCondition myCondition;
  @NotNull private final ArrangementEntryMatcher   myDelegate;

  public StandardArrangementEntryMatcher(@NotNull ArrangementMatchCondition condition) {
    myCondition = condition;
    MyVisitor visitor = new MyVisitor();
    condition.invite(visitor);
    if (visitor.modifiers.isEmpty()) {
      myDelegate = new ByTypeArrangementEntryMatcher(visitor.types);
    }
    else if (visitor.types.isEmpty()) {
      myDelegate = new ByModifierArrangementEntryMatcher(visitor.modifiers);
    }
    else {
      myDelegate = new CompositeArrangementEntryMatcher(
        CompositeArrangementEntryMatcher.Operator.AND,
        new ByTypeArrangementEntryMatcher(visitor.types),
        new ByModifierArrangementEntryMatcher(visitor.modifiers)
      );
    }
  }

  @NotNull
  public ArrangementMatchCondition getCondition() {
    return myCondition;
  }

  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    return myDelegate.isMatched(entry);
  }

  private static class MyVisitor implements ArrangementMatchConditionVisitor {

    @NotNull Set<ArrangementEntryType> types     = EnumSet.noneOf(ArrangementEntryType.class);
    @NotNull Set<ArrangementModifier>  modifiers = EnumSet.noneOf(ArrangementModifier.class);

    @Override
    public void visit(@NotNull ArrangementAtomMatchCondition condition) {
      switch (condition.getType()) {
        case TYPE:
          types.add((ArrangementEntryType)condition.getValue());
          break;
        case MODIFIER:
          modifiers.add((ArrangementModifier)condition.getValue());
      }
    }

    @Override
    public void visit(@NotNull ArrangementCompositeMatchCondition condition) {
      for (ArrangementMatchCondition c : condition.getOperands()) {
        c.invite(this);
      } 
    }
  }
}
