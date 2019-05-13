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
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class PsiMethodReferenceHighlightingUtil {
  static HighlightInfo checkRawConstructorReference(@NotNull PsiMethodReferenceExpression expression) {
    if (expression.isConstructor()) {
      PsiType[] typeParameters = expression.getTypeParameters();
      if (typeParameters.length > 0) {
        PsiElement qualifier = expression.getQualifier();
        if (qualifier instanceof PsiReferenceExpression) {
          PsiElement resolve = ((PsiReferenceExpression)qualifier).resolve();
          if (resolve instanceof PsiClass && ((PsiClass)resolve).hasTypeParameters()) {
            return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(expression)
              .descriptionAndTooltip("Raw constructor reference with explicit type parameters for constructor").create();
          }
        }
      }
    }
    return null;
  }
}
