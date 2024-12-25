// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.model;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates composite match condition, e.g. "an entry has type 'field' and modifier 'static'".
 * <p/>
 * Not thread-safe.
 */
public class ArrangementCompositeMatchCondition implements ArrangementMatchCondition {

  private final @NotNull Set<ArrangementMatchCondition> myOperands = new HashSet<>();

  public ArrangementCompositeMatchCondition() {
  }

  public ArrangementCompositeMatchCondition(@NotNull Collection<? extends ArrangementMatchCondition> conditions) {
    myOperands.addAll(conditions);
  }

  public @NotNull Set<ArrangementMatchCondition> getOperands() {
    return myOperands;
  }

  public @NotNull ArrangementCompositeMatchCondition addOperand(@NotNull ArrangementMatchCondition condition) {
    myOperands.add(condition);
    return this;
  }

  public void removeOperand(@NotNull ArrangementMatchCondition condition) {
    myOperands.remove(condition);
  }
  
  @Override
  public void invite(@NotNull ArrangementMatchConditionVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public @NotNull ArrangementCompositeMatchCondition clone() {
    ArrangementCompositeMatchCondition result = new ArrangementCompositeMatchCondition();
    for (ArrangementMatchCondition operand : myOperands) {
      result.addOperand(operand.clone());
    }
    return result;
  }

  @Override
  public int hashCode() {
    return myOperands.hashCode();
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

    return myOperands.equals(setting.myOperands);
  }

  @Override
  public String toString() {
    return String.format("(%s)", StringUtil.join(myOperands, " and "));
  }
}
