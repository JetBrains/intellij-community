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
package com.intellij.codeInspection.dataFlow.inliner;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiVariable;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.dataFlow.SpecialField.COLLECTION_SIZE;
import static com.intellij.codeInspection.dataFlow.SpecialField.MAP_SIZE;
import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public class CollectionFactoryInliner implements CallInliner {
  static final class FactoryInfo {
    final boolean myNotNull;
    final int mySize;
    final SpecialField mySizeField;

    public FactoryInfo(int size, SpecialField sizeField) {
      this(size, sizeField, false);
    }

    public FactoryInfo(int size, SpecialField sizeField, boolean notNull) {
      mySize = size;
      mySizeField = sizeField;
      myNotNull = notNull;
    }
  }

  private static final CallMapper<FactoryInfo> STATIC_FACTORIES = new CallMapper<FactoryInfo>()
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "emptyList", "emptySet").parameterCount(0), new FactoryInfo(0, COLLECTION_SIZE))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "singletonList", "singleton").parameterCount(1), new FactoryInfo(1, COLLECTION_SIZE))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "emptyMap").parameterCount(0), new FactoryInfo(0, MAP_SIZE))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "singletonMap").parameterCount(2), new FactoryInfo(1, MAP_SIZE));

  private static final CallMatcher JDK9_MAP_FACTORIES =
    staticCall(JAVA_UTIL_MAP, "of", "ofEntries");

  private static final CallMatcher JDK9_FACTORIES = anyOf(
    staticCall(JAVA_UTIL_LIST, "of"),
    staticCall(JAVA_UTIL_SET, "of")
  );

  private static final CallMatcher JDK9_ARRAY_FACTORIES = anyOf(
    staticCall(JAVA_UTIL_LIST, "of").parameterTypes("E..."),
    staticCall(JAVA_UTIL_SET, "of").parameterTypes("E...")
  );

  private static FactoryInfo getFactoryInfo(@NotNull PsiMethodCallExpression call) {
    FactoryInfo info = STATIC_FACTORIES.mapFirst(call);
    if (info != null) return info;
    if (JDK9_FACTORIES.test(call)) {
      int size =
        JDK9_ARRAY_FACTORIES.test(call) && !MethodCallUtils.isVarArgCall(call) ? -1 : call.getArgumentList().getExpressions().length;
      return new FactoryInfo(size, COLLECTION_SIZE, true);
    }
    if (JDK9_MAP_FACTORIES.test(call)) {
      boolean ofEntries = "ofEntries".equals(call.getMethodExpression().getReferenceName());
      int size =
        ofEntries && !MethodCallUtils.isVarArgCall(call) ? -1 : call.getArgumentList().getExpressions().length / (ofEntries ? 1 : 2);
      return new FactoryInfo(size, MAP_SIZE, true);
    }
    return null;
  }

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    FactoryInfo factoryInfo = getFactoryInfo(call);
    if (factoryInfo == null) return false;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    for (PsiExpression arg : args) {
      builder.pushExpression(arg);
      if (factoryInfo.myNotNull) {
        builder.checkNotNull(arg, NullabilityProblemKind.passingNullableToNotNullParameter);
      }
      builder.pop();
    }
    DfaValueFactory factory = builder.getFactory();
    DfaValue result =
      factory.withFact(factory.createTypeValue(call.getType(), Nullness.NOT_NULL), DfaFactType.MUTABILITY, Mutability.UNMODIFIABLE);
    if (factoryInfo.mySize == -1) {
      builder.push(result);
    } else {
      PsiVariable variable = builder.createTempVariable(call.getType());
      DfaVariableValue variableValue = factory.getVarFactory().createVariableValue(variable, false);
      builder.pushVariable(variable) // tmpVar = <Value of collection type>
        .push(result)
        .assign() // leave tmpVar on stack: it's result of method call
        .push(factoryInfo.mySizeField.createValue(factory, variableValue)) // tmpVar.size = <size>
        .push(factory.getInt(factoryInfo.mySize))
        .assign()
        .pop();
    }
    return true;
  }
}
