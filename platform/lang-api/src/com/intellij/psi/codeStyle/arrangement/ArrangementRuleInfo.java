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

import com.intellij.psi.codeStyle.arrangement.match.ArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.order.ArrangementEntryOrderType;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 10/31/12 12:30 PM
 */
public class ArrangementRuleInfo {

  @NotNull private final Set<ArrangementAtomMatchCondition> myAtomConditions = ContainerUtilRt.newHashSet();
  @NotNull private final Set<Object>                        myConditions     = ContainerUtilRt.newHashSet();

  @NotNull private ArrangementEntryOrderType myOrderType = ArrangementMatchRule.DEFAULT_ORDER_TYPE;

  @Nullable private String myNamePattern;

  @Nullable
  public String getNamePattern() {
    return myNamePattern;
  }

  public void setNamePattern(@Nullable String namePattern) {
    myNamePattern = namePattern;
  }

  @NotNull
  public ArrangementEntryOrderType getOrderType() {
    return myOrderType;
  }

  public void setOrderType(@NotNull ArrangementEntryOrderType orderType) {
    myOrderType = orderType;
  }

  public void addAtomCondition(@NotNull ArrangementAtomMatchCondition condition) {
    switch (condition.getType()) {
      case NAME: myNamePattern = condition.getValue().toString(); break;
      default:
        myAtomConditions.add(condition);
        myConditions.add(condition.getValue());
    }
  }

  public boolean hasCondition(@NotNull Object condition) {
    return myConditions.contains(condition);
  }

  public void removeCondition(@NotNull Object condition) {
    if (!myConditions.remove(condition)) {
      return;
    }
    for (Iterator<ArrangementAtomMatchCondition> iterator = myAtomConditions.iterator(); iterator.hasNext(); ) {
      ArrangementAtomMatchCondition c = iterator.next();
      if (c.getValue().equals(condition)) {
        iterator.remove();
        break;
      }
    }
  }

  public void copyConditionsFrom(@NotNull ArrangementRuleInfo info) {
    clearConditions();
    myConditions.addAll(info.myConditions);
    myAtomConditions.addAll(info.myAtomConditions);
  }

  public void clear() {
    clearConditions();
    myOrderType = ArrangementMatchRule.DEFAULT_ORDER_TYPE;
    myNamePattern = null;
  }

  public void clearConditions() {
    myConditions.clear();
    myAtomConditions.clear();
  }

  @Nullable
  public StdArrangementMatchRule buildRule() {
    ArrangementMatchCondition condition = buildCondition();
    if (condition == null) {
      return null;
    }
    return new StdArrangementMatchRule(new StdArrangementEntryMatcher(condition), myOrderType);
  }

  @Nullable
  public ArrangementMatchCondition buildCondition() {
    if (myAtomConditions.isEmpty()) {
      return myNamePattern == null ? null : new ArrangementAtomMatchCondition(ArrangementSettingType.NAME, myNamePattern);
    }
    else if (myAtomConditions.size() == 1 && myNamePattern == null) {
      return myAtomConditions.iterator().next();
    }
    else {
      ArrangementCompositeMatchCondition result = new ArrangementCompositeMatchCondition(myAtomConditions);
      if (myNamePattern != null) {
        result.addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.NAME, myNamePattern));
      }
      return result;
    }
  }
}
