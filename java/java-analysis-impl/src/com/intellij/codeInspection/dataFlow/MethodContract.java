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

import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * A method contract which states that method will have a concrete return value
 * if arguments fulfill some constraint.
 *
 * @author Tagir Valeev
 */
public abstract class MethodContract {
  // package private to avoid uncontrolled implementations
  MethodContract() {

  }

  /**
   * @return a value the method will return if the contract conditions fulfill
   */
  public abstract ValueConstraint getReturnValue();

  /**
   * Returns DfaValue describing the return value of this contract
   *
   * @param factory factory to create values
   * @param defaultResult default result value for the called method
   * @return a DfaValue describing the return value of this contract
   */
  @NotNull
  DfaValue getDfaReturnValue(DfaValueFactory factory, DfaValue defaultResult) {
    switch (getReturnValue()) {
      case NULL_VALUE: return factory.getConstFactory().getNull();
      case NOT_NULL_VALUE:
        return defaultResult instanceof DfaFactMapValue
               ? ((DfaFactMapValue)defaultResult).withFact(DfaFactType.CAN_BE_NULL, false)
               : DfaUnknownValue.getInstance();
      case TRUE_VALUE: return factory.getConstFactory().getTrue();
      case FALSE_VALUE: return factory.getConstFactory().getFalse();
      case THROW_EXCEPTION: return factory.getConstFactory().getContractFail();
      default: return defaultResult;
    }
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

  public static MethodContract trivialContract(ValueConstraint value) {
    return new MethodContract() {
      @Override
      public ValueConstraint getReturnValue() {
        return value;
      }

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
                                                       ValueConstraint returnValue) {
    ContractValue condition = ContractValue.condition(left, relationType, right);
    return new MethodContract() {
      @Override
      public ValueConstraint getReturnValue() {
        return returnValue;
      }

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

  public enum ValueConstraint {
    ANY_VALUE("_"), NULL_VALUE("null"), NOT_NULL_VALUE("!null"), TRUE_VALUE("true"), FALSE_VALUE("false"), THROW_EXCEPTION("fail");
    private final String myPresentableName;

    ValueConstraint(String presentableName) {
      myPresentableName = presentableName;
    }

    @Nullable
    DfaConstValue getComparisonValue(DfaValueFactory factory) {
      if (this == NULL_VALUE || this == NOT_NULL_VALUE) return factory.getConstFactory().getNull();
      if (this == TRUE_VALUE || this == FALSE_VALUE) return factory.getConstFactory().getTrue();
      return null;
    }

    boolean shouldUseNonEqComparison() {
      return this == NOT_NULL_VALUE || this == FALSE_VALUE;
    }

    /**
     * Returns a condition value which should be applied to memory state to satisfy this constraint
     *
     * @param argumentIndex argument number to test
     * @return a condition
     */
    public ContractValue getCondition(int argumentIndex) {
      ContractValue left;
      if (this == NULL_VALUE || this == NOT_NULL_VALUE) {
        left = ContractValue.nullValue();
      }
      else if (this == TRUE_VALUE || this == FALSE_VALUE) {
        left = ContractValue.booleanValue(true);
      }
      else {
        return ContractValue.booleanValue(true);
      }
      return ContractValue.condition(left, RelationType.equivalence(!shouldUseNonEqComparison()), ContractValue.argument(argumentIndex));
    }

    /**
     * @return true if constraint can be negated
     * @see #negate()
     */
    public boolean canBeNegated() {
      return this != ANY_VALUE && this != THROW_EXCEPTION;
    }

    /**
     * @return negated constraint
     * @throws IllegalStateException if constraint cannot be negated
     * @see #canBeNegated()
     */
    public ValueConstraint negate() {
      switch (this) {
        case NULL_VALUE: return NOT_NULL_VALUE;
        case NOT_NULL_VALUE: return NULL_VALUE;
        case TRUE_VALUE: return FALSE_VALUE;
        case FALSE_VALUE: return TRUE_VALUE;
        default:
          throw new IllegalStateException("ValueConstraint = " + this);
      }
    }

    @Override
    public String toString() {
      return myPresentableName;
    }
  }
}
