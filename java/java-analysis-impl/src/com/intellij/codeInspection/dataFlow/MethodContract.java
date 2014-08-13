/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class MethodContract {
  public final ValueConstraint[] arguments;
  public final ValueConstraint returnValue;

  public MethodContract(@NotNull ValueConstraint[] arguments, @NotNull ValueConstraint returnValue) {
    this.arguments = arguments;
    this.returnValue = returnValue;
  }

  static ValueConstraint[] createConstraintArray(int paramCount) {
    ValueConstraint[] args = new ValueConstraint[paramCount];
    for (int i = 0; i < args.length; i++) {
      args[i] = ValueConstraint.ANY_VALUE;
    }
    return args;
  }

  @Override
  public String toString() {
    return StringUtil.join(arguments, new Function<ValueConstraint, String>() {
      @Override
      public String fun(ValueConstraint constraint) {
        return constraint.toString();
      }
    }, ", ") + " -> " + returnValue;
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

    @Override
    public String toString() {
      return myPresentableName;
    }
  }

  public static List<MethodContract> parseContract(String text) throws ParseException {
    List<MethodContract> result = ContainerUtil.newArrayList();
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
      result.add(new MethodContract(args, parseConstraint(clause.substring(arrowIndex + arrow.length()))));
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
