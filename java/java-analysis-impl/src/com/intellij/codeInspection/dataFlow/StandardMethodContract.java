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

import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A method contract which is described by {@link ValueConstraint} constraints on arguments.
 * Such contract can be created from {@link org.jetbrains.annotations.Contract} annotation.
 *
 * @author peter
 */
public final class StandardMethodContract extends MethodContract {
  private final @NotNull ValueConstraint[] myParameters;

  public StandardMethodContract(@NotNull ValueConstraint[] parameters, @NotNull ContractReturnValue returnValue) {
    super(returnValue);
    myParameters = parameters;
  }

  public int getParameterCount() {
    return myParameters.length;
  }

  public ValueConstraint getParameterConstraint(int parameterIndex) {
    return myParameters[parameterIndex];
  }

  public List<ValueConstraint> getConstraints() {
    return Collections.unmodifiableList(Arrays.asList(myParameters));
  }

  @NotNull
  public StandardMethodContract withReturnValue(@NotNull ContractReturnValue returnValue) {
    return returnValue.equals(getReturnValue()) ? this : new StandardMethodContract(myParameters, returnValue);
  }

  @NotNull
  public static ValueConstraint[] createConstraintArray(int paramCount) {
    ValueConstraint[] args = new ValueConstraint[paramCount];
    Arrays.fill(args, ValueConstraint.ANY_VALUE);
    return args;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || o.getClass() != getClass()) return false;

    StandardMethodContract contract = (StandardMethodContract)o;
    return Arrays.equals(myParameters, contract.myParameters) && getReturnValue().equals(contract.getReturnValue());
  }

  @Override
  public int hashCode() {
    int result = 0;
    for (ValueConstraint argument : myParameters) {
      result = 31 * result + argument.ordinal();
    }
    result = 31 * result + getReturnValue().hashCode();
    return result;
  }

  @Override
  String getArgumentsPresentation() {
    return StringUtil.join(myParameters, ValueConstraint::toString, ", ");
  }

  @Override
  public List<ContractValue> getConditions() {
    return IntStreamEx.ofIndices(myParameters).mapToObj(idx -> myParameters[idx].getCondition(idx)).without(ContractValue.booleanValue(true))
                      .toList();
  }

  public static List<StandardMethodContract> parseContract(String text) throws ParseException {
    List<StandardMethodContract> result = ContainerUtil.newArrayList();
    for (String clause : StringUtil.replace(text, " ", "").split(";")) {
      String arrow = "->";
      int arrowIndex = clause.indexOf(arrow);
      if (arrowIndex < 0) {
        throw new ParseException("A contract clause must be in form arg1, ..., argN -> return-value");
      }

      String beforeArrow = clause.substring(0, arrowIndex);
      ValueConstraint[] args;
      if (StringUtil.isNotEmpty(beforeArrow)) {
        String[] argStrings = beforeArrow.split(",");
        args = new ValueConstraint[argStrings.length];
        for (int i = 0; i < args.length; i++) {
          args[i] = parseConstraint(argStrings[i]);
        }
      } else {
        args = new ValueConstraint[0];
      }
      result.add(new StandardMethodContract(args, parseReturnValue(clause.substring(arrowIndex + arrow.length()))));
    }
    return result;
  }

  @NotNull
  private static ContractReturnValue parseReturnValue(String returnValueString) throws ParseException {
    ContractReturnValue returnValue = ContractReturnValue.valueOf(returnValueString);
    if (returnValue == null) {
      throw new ParseException(
        "Return value should be one of: null, !null, true, false, this, new, paramN, fail, _. Found: " + returnValueString);
    }
    return returnValue;
  }

  private static ValueConstraint parseConstraint(String name) throws ParseException {
    if (StringUtil.isEmpty(name)) throw new ParseException("Constraint should not be empty");
    for (ValueConstraint constraint : ValueConstraint.values()) {
      if (constraint.toString().equals(name)) return constraint;
    }
    throw new ParseException("Constraint should be one of: null, !null, true, false, _. Found: " + name);
  }

  public enum ValueConstraint {
    ANY_VALUE("_", ContractReturnValue.returnAny()),
    NULL_VALUE("null", ContractReturnValue.returnNull()),
    NOT_NULL_VALUE("!null", ContractReturnValue.returnNotNull()),
    TRUE_VALUE("true", ContractReturnValue.returnTrue()),
    FALSE_VALUE("false", ContractReturnValue.returnFalse());

    private final String myPresentableName;
    private final ContractReturnValue myCorrespondingReturnValue;

    ValueConstraint(String presentableName, ContractReturnValue correspondingReturnValue) {
      myPresentableName = presentableName;
      myCorrespondingReturnValue = correspondingReturnValue;
    }

    public ContractReturnValue asReturnValue() {
      return myCorrespondingReturnValue;
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
      return ContractValue.condition(left, DfaRelationValue.RelationType.equivalence(!shouldUseNonEqComparison()), ContractValue.argument(argumentIndex));
    }

    /**
     * @return true if constraint can be negated
     * @see #negate()
     */
    public boolean canBeNegated() {
      return this != ANY_VALUE;
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

  public static class ParseException extends Exception {
    private ParseException(String message) {
      super(message);
    }
  }
}
