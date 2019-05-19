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
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.siyeh.ig.callMatcher.CallMapper;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.dataFlow.SpecialField.COLLECTION_SIZE;
import static com.intellij.psi.CommonClassNames.*;
import static com.siyeh.ig.callMatcher.CallMatcher.anyOf;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public class CollectionFactoryInliner implements CallInliner {
  static final class FactoryInfo {
    final boolean myNotNull;
    final int mySize;

    FactoryInfo(int size) {
      this(size, false);
    }

    FactoryInfo(int size, boolean notNull) {
      mySize = size;
      myNotNull = notNull;
    }
  }

  private static final CallMapper<FactoryInfo> STATIC_FACTORIES = new CallMapper<FactoryInfo>()
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "emptyList", "emptySet").parameterCount(0), new FactoryInfo(0))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "singletonList", "singleton").parameterCount(1), new FactoryInfo(1))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "emptyMap").parameterCount(0), new FactoryInfo(0))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "singletonMap").parameterCount(2), new FactoryInfo(1));

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
        JDK9_ARRAY_FACTORIES.test(call) && !MethodCallUtils.isVarArgCall(call) ? -1 : call.getArgumentList().getExpressionCount();
      return new FactoryInfo(size, true);
    }
    if (JDK9_MAP_FACTORIES.test(call)) {
      boolean ofEntries = "ofEntries".equals(call.getMethodExpression().getReferenceName());
      int size =
        ofEntries && !MethodCallUtils.isVarArgCall(call) ? -1 : call.getArgumentList().getExpressionCount() / (ofEntries ? 1 : 2);
      return new FactoryInfo(size, true);
    }
    return null;
  }

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    PsiType callType = call.getType();
    if (callType == null) return false;
    FactoryInfo factoryInfo = getFactoryInfo(call);
    if (factoryInfo == null) return false;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    for (PsiExpression arg : args) {
      builder.pushExpression(arg, factoryInfo.myNotNull ? NullabilityProblemKind.passingToNotNullParameter : null);
      builder.pop();
    }
    DfaValueFactory factory = builder.getFactory();
    SpecialFieldValue sizeConstraint =
      factoryInfo.mySize == -1 ? null : COLLECTION_SIZE.withValue(factory.getInt(factoryInfo.mySize));
    DfaFactMap facts = DfaFactMap.EMPTY
      .with(DfaFactType.TYPE_CONSTRAINT, factory.createDfaType(callType).asConstraint())
      .with(DfaFactType.NULLABILITY, DfaNullability.NOT_NULL)
      .with(DfaFactType.MUTABILITY, Mutability.UNMODIFIABLE)
      .with(DfaFactType.SPECIAL_FIELD_VALUE, sizeConstraint);
    builder.push(factory.getFactFactory().createValue(facts), call);
    return true;
  }
}
