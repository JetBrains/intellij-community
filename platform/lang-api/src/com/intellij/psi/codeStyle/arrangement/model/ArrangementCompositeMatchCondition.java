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
package com.intellij.psi.codeStyle.arrangement.model;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Encapsulates composite match condition, e.g. "an entry has type 'field' and modifier 'static'".
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/8/12 1:18 PM
 */
public class ArrangementCompositeMatchCondition implements ArrangementMatchCondition {

  @NotNull private final Set<ArrangementMatchCondition> myOperands = new HashSet<ArrangementMatchCondition>();
  @NotNull private final Operator myOperator;

  public ArrangementCompositeMatchCondition(@NotNull Operator operator) {
    myOperator = operator;
  }

  @NotNull
  public Set<ArrangementMatchCondition> getOperands() {
    return myOperands;
  }

  public ArrangementCompositeMatchCondition addOperand(@NotNull ArrangementMatchCondition node) {
    myOperands.add(node);
    return this;
  }

  @NotNull
  public Operator getOperator() {
    return myOperator;
  }

  @Override
  public void invite(@NotNull ArrangementSettingsNodeVisitor visitor) {
    visitor.visit(this);
  }

  @NotNull
  @Override
  public ArrangementCompositeMatchCondition clone() {
    ArrangementCompositeMatchCondition result = new ArrangementCompositeMatchCondition(myOperator);
    for (ArrangementMatchCondition operand : myOperands) {
      result.addOperand(operand.clone());
    }
    return result;
  }

  @Override
  public int hashCode() {
    int result = myOperands.hashCode();
    result = 31 * result + myOperator.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    ArrangementCompositeMatchCondition setting = (ArrangementCompositeMatchCondition)o;

    if (!myOperands.equals(setting.myOperands)) {
      return false;
    }
    if (myOperator != setting.myOperator) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    return String.format("(%s)", StringUtil.join(myOperands, myOperator == Operator.AND ? " and " : " or "));
  }

  public enum Operator {
    AND, OR
  }
}
