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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

/**
 * A method contract which is described by {@link MethodContract.ValueConstraint} constraints on arguments.
 * Such contract can be created from {@link org.jetbrains.annotations.Contract} annotation.
 *
 * @author peter
 */
public final class StandardMethodContract extends MethodContract {
  public final ValueConstraint[] arguments;
  public final ValueConstraint returnValue;

  public StandardMethodContract(@NotNull ValueConstraint[] arguments, @NotNull ValueConstraint returnValue) {
    this.arguments = arguments;
    this.returnValue = returnValue;
  }

  @Override
  public ValueConstraint getReturnValue() {
    return returnValue;
  }

  @NotNull
  static ValueConstraint[] createConstraintArray(int paramCount) {
    ValueConstraint[] args = new ValueConstraint[paramCount];
    for (int i = 0; i < args.length; i++) {
      args[i] = ValueConstraint.ANY_VALUE;
    }
    return args;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || o.getClass() != getClass()) return false;

    StandardMethodContract contract = (StandardMethodContract)o;

    if (!Arrays.equals(arguments, contract.arguments)) return false;
    if (returnValue != contract.returnValue) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = 0;
    for (ValueConstraint argument : arguments) {
      result = 31 * result + argument.ordinal();
    }
    result = 31 * result + returnValue.ordinal();
    return result;
  }

  @Override
  String getArgumentsPresentation() {
    return StringUtil.join(arguments, constraint -> constraint.toString(), ", ");
  }

  @Override
  public List<ContractValue> getConditions() {
    return IntStreamEx.ofIndices(arguments).mapToObj(idx -> arguments[idx].getCondition(idx)).without(ContractValue.booleanValue(true))
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
      result.add(new StandardMethodContract(args, parseConstraint(clause.substring(arrowIndex + arrow.length()))));
    }
    return result;
  }

  private static ValueConstraint parseConstraint(String name) throws ParseException {
    if (StringUtil.isEmpty(name)) throw new ParseException("Constraint should not be empty");
    for (ValueConstraint constraint : ValueConstraint.values()) {
      if (constraint.toString().equals(name)) return constraint;
    }
    throw new ParseException("Constraint should be one of: null, !null, true, false, fail, _. Found: " + name);
  }

  public static class ParseException extends Exception {
    private ParseException(String message) {
      super(message);
    }
  }
}
