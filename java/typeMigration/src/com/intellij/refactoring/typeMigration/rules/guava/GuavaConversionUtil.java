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
package com.intellij.refactoring.typeMigration.rules.guava;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class GuavaConversionUtil {

  @Nullable
  public static PsiType getFunctionReturnType(PsiExpression functionExpression) {
    PsiType currentType = functionExpression.getType();
    if (currentType == null) return null;

    while (true) {
      if (LambdaUtil.isFunctionalType(currentType)) {
        return LambdaUtil.getFunctionalInterfaceReturnType(currentType);
      }
      final PsiType[] superTypes = currentType.getSuperTypes();
      currentType = null;
      for (PsiType type : superTypes) {
        final PsiClass aClass = PsiTypesUtil.getPsiClass(type);
        if (aClass != null && InheritanceUtil.isInheritor(aClass, GuavaFunctionConversionRule.GUAVA_FUNCTION)) {
          currentType = type;
          break;
        }
      }
      if (currentType == null) {
        return null;
      }
    }
  }

  @NotNull
  public static PsiType addTypeParameters(@NotNull String baseClassQualifiedName, @Nullable PsiType type, @NotNull PsiElement context) {
    String parameterText = "";
    if (type != null) {
      final String canonicalText = type.getCanonicalText(false);
      if (canonicalText.contains("<")) {
        parameterText = canonicalText.substring(canonicalText.indexOf('<'));
      }
    }

    return JavaPsiFacade.getElementFactory(context.getProject()).createTypeFromText(baseClassQualifiedName + parameterText, context);
  }
}
