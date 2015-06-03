/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow.value.java;

import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class DfaConstValueFactoryJava extends DfaConstValue.Factory {

  private final DfaValueFactoryJava myFactory;

  public DfaConstValueFactoryJava(DfaValueFactoryJava factory) {
    super(factory);
    myFactory = factory;
  }

  @Nullable
  public DfaValue create(PsiLiteralExpression expression) {
    return create(expression.getType(), expression.getValue());
  }

  @Nullable
  @Override
  public DfaValue create(PsiVariable variable) {
    Object value = variable.computeConstantValue();
    PsiType type = variable.getType();
    if (value == null) {
      Boolean boo = computeJavaLangBooleanFieldReference(variable);
      if (boo != null) {
        DfaConstValue unboxed = createFromValue(boo, PsiType.BOOLEAN, variable);
        return myFactory.getBoxedFactory().createBoxed(unboxed);
      }
      PsiExpression initializer = variable.getInitializer();
      if (initializer instanceof PsiLiteralExpression && initializer.textMatches(PsiKeyword.NULL)) {
        return dfaNull;
      }
      return null;
    }
    return createFromValue(value, type, variable);
  }

  @Nullable
  public static Boolean computeJavaLangBooleanFieldReference(final PsiVariable variable) {
    if (!(variable instanceof PsiField)) return null;
    PsiClass psiClass = ((PsiField)variable).getContainingClass();
    if (psiClass == null || !CommonClassNames.JAVA_LANG_BOOLEAN.equals(psiClass.getQualifiedName())) return null;
    @NonNls String name = variable.getName();
    return "TRUE".equals(name) ? Boolean.TRUE : "FALSE".equals(name) ? Boolean.FALSE : null;
  }
}
