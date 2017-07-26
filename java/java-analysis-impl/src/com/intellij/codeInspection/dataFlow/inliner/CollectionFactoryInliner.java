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

import com.intellij.codeInspection.dataFlow.CFGBuilder;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.siyeh.ig.callMatcher.CallMapper;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.dataFlow.SpecialField.COLLECTION_SIZE;
import static com.intellij.codeInspection.dataFlow.SpecialField.MAP_SIZE;
import static com.intellij.psi.CommonClassNames.JAVA_UTIL_COLLECTIONS;
import static com.siyeh.ig.callMatcher.CallMatcher.staticCall;

public class CollectionFactoryInliner implements CallInliner {
  static final class FactoryInfo {
    int mySize;
    SpecialField mySizeField;

    public FactoryInfo(int size, SpecialField sizeField) {
      mySize = size;
      mySizeField = sizeField;
    }
  }

  private static final CallMapper<FactoryInfo> STATIC_FACTORIES = new CallMapper<FactoryInfo>()
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "emptyList", "emptySet").parameterCount(0), new FactoryInfo(0, COLLECTION_SIZE))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "singletonList", "singleton").parameterCount(1), new FactoryInfo(1, COLLECTION_SIZE))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "emptyMap").parameterCount(0), new FactoryInfo(0, MAP_SIZE))
    .register(staticCall(JAVA_UTIL_COLLECTIONS, "singletonMap").parameterCount(2), new FactoryInfo(1, MAP_SIZE));

  @Override
  public boolean tryInlineCall(@NotNull CFGBuilder builder, @NotNull PsiMethodCallExpression call) {
    FactoryInfo factoryInfo = STATIC_FACTORIES.mapFirst(call);
    if (factoryInfo == null) return false;
    PsiExpression[] args = call.getArgumentList().getExpressions();
    for (PsiExpression arg : args) {
      builder.pushExpression(arg).pop();
    }
    PsiVariable variable = builder.createTempVariable(call.getType());
    DfaValueFactory factory = builder.getFactory();
    DfaVariableValue variableValue = factory.getVarFactory().createVariableValue(variable, false);
    builder.pushVariable(variable) // tmpVar = <Value of collection type>
      .push(factory.createTypeValue(call.getType(), Nullness.NOT_NULL))
      .assign() // leave tmpVar on stack: it's result of method call
      .push(factoryInfo.mySizeField.createValue(factory, variableValue)) // tmpVar.size = <size>
      .push(factory.getConstFactory().createFromValue(factoryInfo.mySize, PsiType.INT, null))
      .assign()
      .pop();
    return true;
  }
}
