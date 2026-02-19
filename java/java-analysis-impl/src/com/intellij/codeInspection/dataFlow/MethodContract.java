// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.RelationType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A method contract which states that method will have a concrete return value
 * if arguments fulfill some constraint.
 *
 * @author Tagir Valeev
 */
public abstract class MethodContract {
  private final ContractReturnValue myReturnValue;

  // package private to avoid uncontrolled implementations
  MethodContract(ContractReturnValue returnValue) {
    myReturnValue = returnValue;
  }

  /**
   * @return a value the method will return if the contract conditions fulfill
   */
  public ContractReturnValue getReturnValue() {
    return myReturnValue;
  }

  /**
   * @return true if this contract result does not depend on arguments
   */
  public boolean isTrivial() {
    return getConditions().isEmpty();
  }

  abstract String getArgumentsPresentation();

  public abstract List<ContractValue> getConditions();

  @Override
  public String toString() {
    return getArgumentsPresentation() + " -> " + getReturnValue();
  }

  public static MethodContract trivialContract(ContractReturnValue value) {
    return new MethodContract(value) {
      @Override
      String getArgumentsPresentation() {
        return "()";
      }

      @Override
      public List<ContractValue> getConditions() {
        return Collections.emptyList();
      }
    };
  }

  public static @NotNull MethodContract singleConditionContract(ContractValue left,
                                                                RelationType relationType,
                                                                ContractValue right,
                                                                ContractReturnValue returnValue) {
    return singleConditionContract(ContractValue.condition(left, relationType, right), returnValue);
  }

  private static @NotNull MethodContract singleConditionContract(ContractValue condition, ContractReturnValue returnValue) {
    return new MethodContract(returnValue) {
      @Override
      String getArgumentsPresentation() {
        return "("+condition.toString()+")";
      }

      @Override
      public List<ContractValue> getConditions() {
        return Collections.singletonList(condition);
      }
    };
  }

  public static List<? extends MethodContract> toNonIntersectingContracts(List<? extends MethodContract> contracts) {
    if (contracts.size() == 1) return contracts;
    if (contracts.stream().allMatch(StandardMethodContract.class::isInstance)) {
      @SuppressWarnings("unchecked") List<StandardMethodContract> standardContracts = (List<StandardMethodContract>)contracts;
      return StandardMethodContract.toNonIntersectingStandardContracts(standardContracts);
    }
    if (contracts.size() == 2 && contracts.get(1).isTrivial()) {
      List<MethodContract> result = new ArrayList<>();
      result.add(contracts.get(0));
      List<ContractValue> conditions = contracts.get(0).getConditions();
      for (ContractValue condition : conditions) {
        ContractValue inverted = condition.invert();
        if (inverted == null) {
          return null;
        }
        result.add(singleConditionContract(inverted, contracts.get(1).getReturnValue()));
      }
      return result;
    }
    return null;
  }
}
