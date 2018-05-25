/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;

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
        return "(any)";
      }

      @Override
      public List<ContractValue> getConditions() {
        return Collections.emptyList();
      }
    };
  }

  public static MethodContract singleConditionContract(ContractValue left,
                                                       RelationType relationType,
                                                       ContractValue right,
                                                       ContractReturnValue returnValue) {
    ContractValue condition = ContractValue.condition(left, relationType, right);
    return new MethodContract(returnValue) {
      @Override
      String getArgumentsPresentation() {
        return condition.toString();
      }

      @Override
      public List<ContractValue> getConditions() {
        return Collections.singletonList(condition);
      }
    };
  }
}
