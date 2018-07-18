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
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

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

  public static StandardMethodContract trivialContract(int paramCount, @NotNull ContractReturnValue returnValue) {
    return new StandardMethodContract(createConstraintArray(paramCount), returnValue);
  }

  /**
   * Creates a new contract which is an intersection of this and supplied contracts
   *
   * @param contract a contract to intersect with
   * @return intersection contract or null if no intersection is possible
   */
  @Nullable
  StandardMethodContract intersect(StandardMethodContract contract) {
    ValueConstraint[] result = myParameters.clone();
    assert contract.getParameterCount() == result.length;
    for (int i = 0; i < result.length; i++) {
      ValueConstraint condition = result[i];
      ValueConstraint constraint = contract.getParameterConstraint(i);
      if (condition == constraint || condition == ValueConstraint.ANY_VALUE) {
        result[i] = constraint;
      } else if (constraint == ValueConstraint.ANY_VALUE) {
        result[i] = condition;
      }
      else {
        return null;
      }
    }
    return new StandardMethodContract(result, getReturnValue().intersect(contract.getReturnValue()));
  }

  /**
   * Creates a stream of contracts which describe all states covered by this contract but not covered by
   * supplied contract.
   *
   * @param contract contract to exclude
   * @return a stream of exclusion contracts (could be empty)
   */
  @NotNull
  Stream<StandardMethodContract> excludeContract(StandardMethodContract contract) {
    assert contract.getParameterCount() == myParameters.length;
    List<ValueConstraint> constraints = contract.getConstraints();
    List<ValueConstraint> template = StreamEx.constant(ValueConstraint.ANY_VALUE, myParameters.length).toList();
    List<StandardMethodContract> antiContracts = new ArrayList<>();
    for (int i = 0; i < constraints.size(); i++) {
      ValueConstraint constraint = constraints.get(i);
      if (constraint == ValueConstraint.ANY_VALUE) continue;
      template.set(i, constraint.negate());
      antiContracts.add(new StandardMethodContract(template.toArray(new ValueConstraint[0]), getReturnValue()));
      template.set(i, constraint);
    }
    return StreamEx.of(antiContracts).map(this::intersect).nonNull();
  }

  /**
   * Converts list of contracts which are equivalent to the passed list, but independent on the order
   * (e.g. {@code "null -> null, _ -> !null"} will be converted to {@code "null -> null, !null -> !null"}). Also removes unreachable
   * contracts if any.
   *
   * @param contracts list of input contracts to process (assumed that they are applied in the specified order)
   * @return list of equivalent non-intersecting contracts or null if the result is too big or the input list contains errors
   * (e.g. contracts with different parameter count)
   */
  @Nullable("When result is too big or contracts are erroneous")
  public static List<StandardMethodContract> toNonIntersectingContracts(List<StandardMethodContract> contracts) {
    if (contracts.isEmpty()) return contracts;
    int paramCount = contracts.get(0).getParameterCount();
    List<StandardMethodContract> result = new ArrayList<>();
    List<StandardMethodContract> leftovers = Collections.singletonList(trivialContract(paramCount, ContractReturnValue.returnAny()));
    for (StandardMethodContract contract : contracts) {
      if (contract.getParameterCount() != paramCount) return null;
      StreamEx.of(leftovers).map(c -> c.intersect(contract)).nonNull().into(result);
      if (result.size() >= DataFlowRunner.MAX_STATES_PER_BRANCH) return null;
      leftovers = StreamEx.of(leftovers).flatMap(c -> c.excludeContract(contract)).toList();
      if (leftovers.isEmpty()) break;
    }
    return result;
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
    String[] split = StringUtil.replace(text, " ", "").split(";");
    for (int clauseIndex = 0; clauseIndex < split.length; clauseIndex++) {
      String clause = split[clauseIndex];
      String arrow = "->";
      int arrowIndex = clause.indexOf(arrow);
      if (arrowIndex < 0) {
        throw ParseException.forClause("A contract clause must be in form arg1, ..., argN -> return-value", text, clauseIndex);
      }

      String beforeArrow = clause.substring(0, arrowIndex);
      ValueConstraint[] args;
      if (StringUtil.isNotEmpty(beforeArrow)) {
        String[] argStrings = beforeArrow.split(",");
        args = new ValueConstraint[argStrings.length];
        for (int i = 0; i < args.length; i++) {
          args[i] = parseConstraint(argStrings[i], text, clauseIndex, i);
        }
      }
      else {
        args = new ValueConstraint[0];
      }
      String returnValueString = clause.substring(arrowIndex + arrow.length());
      ContractReturnValue returnValue = ContractReturnValue.valueOf(returnValueString);
      if (returnValue == null) {
        throw ParseException.forReturnValue(
          "Return value should be one of: null, !null, true, false, this, new, paramN, fail, _. Found: " + returnValueString,
          text, clauseIndex);
      }
      result.add(new StandardMethodContract(args, returnValue));
    }
    return result;
  }

  private static ValueConstraint parseConstraint(String name, String text, int clauseIndex, int constraintIndex) throws ParseException {
    if (StringUtil.isEmpty(name)) throw new ParseException("Constraint should not be empty");
    for (ValueConstraint constraint : ValueConstraint.values()) {
      if (constraint.toString().equals(name)) return constraint;
    }
    throw ParseException
      .forConstraint("Constraint should be one of: null, !null, true, false, _. Found: " + name, text, clauseIndex, constraintIndex);
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
    private final @Nullable TextRange myRange;

    ParseException(String message) {
      this(message, null);
    }

    ParseException(String message, @Nullable TextRange range) {
      super(message);
      myRange = range != null && range.isEmpty() ? null : range;
    }

    @Nullable
    public TextRange getRange() {
      return myRange;
    }

    static ParseException forConstraint(String message, String text, int clauseNumber, int constraintNumber) {
      TextRange range = findClauseRange(text, clauseNumber);
      if (range == null) {
        return new ParseException(message);
      }
      int start = range.getStartOffset();
      while (constraintNumber > 0) {
        start = text.indexOf(',', start);
        if (start == -1) return new ParseException(message, range);
        start++;
        constraintNumber--;
      }
      int end = text.indexOf(',', start);
      if (end == -1 || end > range.getEndOffset()) {
        end = text.indexOf("->", start);
        if (end == -1 || end > range.getEndOffset()) {
          end = range.getEndOffset();
        }
      }
      if (!text.substring(start, end).trim().isEmpty()) {
        while (text.charAt(start) == ' ') start++;
        while (end > start && text.charAt(end - 1) == ' ') end--;
      }
      return new ParseException(message, new TextRange(start, end));
    }

    static ParseException forReturnValue(String message, String text, int clauseNumber) {
      TextRange range = findClauseRange(text, clauseNumber);
      if (range == null) {
        return new ParseException(message);
      }
      int index = text.indexOf("->", range.getStartOffset());
      if (index == -1 || index > range.getEndOffset()) {
        return new ParseException(message, range);
      }
      index += "->".length();
      while (index < range.getEndOffset() && text.charAt(index) == ' ') index++;
      if (index == range.getEndOffset()) {
        return new ParseException(message, range);
      }
      return new ParseException(message, new TextRange(index, range.getEndOffset()));
    }

    static ParseException forClause(String message, String text, int clauseNumber) {
      TextRange range = findClauseRange(text, clauseNumber);
      return range == null ? new ParseException(message) : new ParseException(message, range);
    }

    private static TextRange findClauseRange(String text, int clauseNumber) {
      int start = 0;
      while (clauseNumber > 0) {
        start = text.indexOf(';', start);
        if (start == -1) return null;
        start++;
        clauseNumber--;
      }
      int end = text.indexOf(';', start);
      if (end == -1) {
        end = text.length();
      }
      if (text.substring(start, end).trim().isEmpty()) return new TextRange(start, end);

      while (text.charAt(start) == ' ') start++;
      while (end > start && text.charAt(end - 1) == ' ') end--;

      return new TextRange(start, end);
    }
  }
}
