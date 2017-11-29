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

import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMapper;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.*;

/**
 * @author Tagir Valeev
 */
public class CustomMethodHandlers {
  interface CustomMethodHandler {
    List<DfaMemoryState> handle(DfaCallArguments callArguments, DfaMemoryState memState, DfaValueFactory factory);
  }

  private static final CallMapper<CustomMethodHandler> CUSTOM_METHOD_HANDLERS = new CallMapper<CustomMethodHandler>()
    .register(instanceCall(JAVA_LANG_STRING, "indexOf", "lastIndexOf"),
              (args, memState, factory) -> indexOf(args.myQualifier, memState, factory, SpecialField.STRING_LENGTH))
    .register(instanceCall(JAVA_UTIL_LIST, "indexOf", "lastIndexOf"),
              (args, memState, factory) -> indexOf(args.myQualifier, memState, factory, SpecialField.COLLECTION_SIZE))
    .register(instanceCall(JAVA_LANG_STRING, "equals").parameterCount(1),
              (args, memState, factory) -> stringEquals(args, memState, factory, false))
    .register(instanceCall(JAVA_LANG_STRING, "equalsIgnoreCase").parameterCount(1),
              (args, memState, factory) -> stringEquals(args, memState, factory, true))
    .register(instanceCall(JAVA_LANG_STRING, "startsWith").parameterCount(1),
              (args, memState, factory) -> stringStartsEnds(args, memState, factory, false))
    .register(instanceCall(JAVA_LANG_STRING, "endsWith").parameterCount(1),
              (args, memState, factory) -> stringStartsEnds(args, memState, factory, true))
    .register(anyOf(staticCall(JAVA_LANG_MATH, "max").parameterTypes("int", "int"),
                    staticCall(JAVA_LANG_MATH, "max").parameterTypes("long", "long"),
                    staticCall(JAVA_LANG_INTEGER, "max").parameterTypes("int", "int"),
                    staticCall(JAVA_LANG_LONG, "max").parameterTypes("long", "long")),
              (args, memState, factory) -> mathMinMax(args.myArguments, memState, factory, true))
    .register(anyOf(staticCall(JAVA_LANG_MATH, "min").parameterTypes("int", "int"),
                    staticCall(JAVA_LANG_MATH, "min").parameterTypes("long", "long"),
                    staticCall(JAVA_LANG_INTEGER, "min").parameterTypes("int", "int"),
                    staticCall(JAVA_LANG_LONG, "min").parameterTypes("long", "long")),
              (args, memState, factory) -> mathMinMax(args.myArguments, memState, factory, false))
    .register(staticCall(JAVA_LANG_MATH, "abs").parameterTypes("int"),
              (args, memState, factory) -> mathAbs(args.myArguments, memState, factory, false))
    .register(staticCall(JAVA_LANG_MATH, "abs").parameterTypes("long"),
              (args, memState, factory) -> mathAbs(args.myArguments, memState, factory, true));

  public static CustomMethodHandler find(MethodCallInstruction instruction) {
    PsiElement context = instruction.getContext();
    if(context instanceof PsiMethodCallExpression) {
      return CUSTOM_METHOD_HANDLERS.mapFirst((PsiMethodCallExpression)context);
    } else if(context instanceof PsiMethodReferenceExpression) {
      return CUSTOM_METHOD_HANDLERS.mapFirst((PsiMethodReferenceExpression)context);
    }
    return null;
  }

  private static List<DfaMemoryState> stringStartsEnds(DfaCallArguments args,
                                                       DfaMemoryState memState,
                                                       DfaValueFactory factory,
                                                       boolean ends) {
    DfaValue arg = ArrayUtil.getFirstElement(args.myArguments);
    if (arg == null) return Collections.emptyList();
    String leftConst = ObjectUtils.tryCast(getConstantValue(memState, args.myQualifier), String.class);
    String rightConst = ObjectUtils.tryCast(getConstantValue(memState, arg), String.class);
    if (leftConst != null && rightConst != null) {
      return singleResult(memState, factory.getBoolean(ends ? leftConst.endsWith(rightConst) : leftConst.startsWith(rightConst)));
    }
    DfaValue leftLength = SpecialField.STRING_LENGTH.createValue(factory, args.myQualifier);
    DfaValue rightLength = SpecialField.STRING_LENGTH.createValue(factory, arg);
    DfaValue trueRelation = factory.createCondition(leftLength, RelationType.GE, rightLength);
    DfaValue falseRelation = factory.createCondition(leftLength, RelationType.LT, rightLength);
    return applyCondition(memState, trueRelation, DfaUnknownValue.getInstance(), falseRelation, factory.getBoolean(false));
  }

  private static List<DfaMemoryState> stringEquals(DfaCallArguments args,
                                                   DfaMemoryState memState,
                                                   DfaValueFactory factory,
                                                   boolean ignoreCase) {
    DfaValue arg = ArrayUtil.getFirstElement(args.myArguments);
    if (arg == null) return Collections.emptyList();
    String leftConst = ObjectUtils.tryCast(getConstantValue(memState, args.myQualifier), String.class);
    String rightConst = ObjectUtils.tryCast(getConstantValue(memState, arg), String.class);
    if (leftConst != null && rightConst != null) {
      return singleResult(memState, factory.getBoolean(ignoreCase ? leftConst.equalsIgnoreCase(rightConst) : leftConst.equals(rightConst)));
    }
    DfaValue leftLength = SpecialField.STRING_LENGTH.createValue(factory, args.myQualifier);
    DfaValue rightLength = SpecialField.STRING_LENGTH.createValue(factory, arg);
    DfaValue trueRelation = factory.createCondition(leftLength, RelationType.EQ, rightLength);
    DfaValue falseRelation = factory.createCondition(leftLength, RelationType.NE, rightLength);
    return applyCondition(memState, trueRelation, DfaUnknownValue.getInstance(), falseRelation, factory.getBoolean(false));
  }

  private static List<DfaMemoryState> indexOf(DfaValue qualifier,
                                              DfaMemoryState memState,
                                              DfaValueFactory factory,
                                              SpecialField specialField) {
    DfaValue length = specialField.createValue(factory, qualifier);
    LongRangeSet range = memState.getValueFact(DfaFactType.RANGE, length);
    long maxLen = range == null || range.isEmpty() ? Integer.MAX_VALUE : range.max();
    return singleResult(memState, factory.getRangeFactory().create(LongRangeSet.range(-1, maxLen - 1)));
  }

  private static List<DfaMemoryState> mathMinMax(DfaValue[] args, DfaMemoryState memState, DfaValueFactory factory, boolean max) {
    if(args == null || args.length != 2) return Collections.emptyList();
    LongRangeSet first = memState.getValueFact(DfaFactType.RANGE, args[0]);
    LongRangeSet second = memState.getValueFact(DfaFactType.RANGE, args[1]);
    if (first == null || second == null || first.isEmpty() || second.isEmpty()) return Collections.emptyList();
    LongRangeSet domain = max ? LongRangeSet.range(Math.max(first.min(), second.min()), Long.MAX_VALUE)
                          : LongRangeSet.range(Long.MIN_VALUE, Math.min(first.max(), second.max()));
    LongRangeSet result = first.union(second).intersect(domain);
    return singleResult(memState, factory.getRangeFactory().create(result));
  }

  private static List<DfaMemoryState> mathAbs(DfaValue[] args, DfaMemoryState memState, DfaValueFactory factory, boolean isLong) {
    DfaValue arg = ArrayUtil.getFirstElement(args);
    if(arg == null) return Collections.emptyList();
    LongRangeSet range = memState.getValueFact(DfaFactType.RANGE, arg);
    if (range == null) return Collections.emptyList();
    return singleResult(memState, factory.getRangeFactory().create(range.abs(isLong)));
  }

  private static List<DfaMemoryState> singleResult(DfaMemoryState state, DfaValue value) {
    state.push(value);
    return Collections.singletonList(state);
  }

  @NotNull
  private static List<DfaMemoryState> applyCondition(DfaMemoryState memState,
                                                     DfaValue trueCondition,
                                                     DfaValue trueResult,
                                                     DfaValue falseCondition,
                                                     DfaValue falseResult) {
    DfaMemoryState falseState = memState.createCopy();
    List<DfaMemoryState> result = new ArrayList<>(2);
    if (memState.applyCondition(trueCondition)) {
      memState.push(trueResult);
      result.add(memState);
    }
    if (falseState.applyCondition(falseCondition)) {
      falseState.push(falseResult);
      result.add(falseState);
    }
    return result;
  }

  private static Object getConstantValue(DfaMemoryState memoryState, DfaValue value) {
    if (value instanceof DfaVariableValue) {
      value = memoryState.getConstantValue((DfaVariableValue)value);
    }
    if (value instanceof DfaConstValue) {
      return ((DfaConstValue)value).getValue();
    }
    return null;
  }
}
