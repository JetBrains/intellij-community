/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;

public class JavaGenericsUtil {
  public static boolean isReifiableType(PsiType type) {
    if (type instanceof PsiArrayType) {
      return isReifiableType(((PsiArrayType)type).getComponentType());
    }

    if (type instanceof PsiPrimitiveType) {
      return true;
    }

    if (PsiUtil.resolveClassInType(type) instanceof PsiTypeParameter) {
      return false;
    }

    if (type instanceof PsiClassType) {
      final PsiClassType classType = (PsiClassType)PsiUtil.convertAnonymousToBaseType(type);
      if (classType.isRaw()) {
        return true;
      }
      PsiType[] parameters = classType.getParameters();

      for (PsiType parameter : parameters) {
        if (parameter instanceof PsiWildcardType && ((PsiWildcardType)parameter).getBound() == null) {
          return true;
        }
      }
      final PsiClass resolved = ((PsiClassType)PsiUtil.convertAnonymousToBaseType(classType)).resolve();
      if (resolved instanceof PsiTypeParameter) {
        return false;
      }
      if (parameters.length == 0) {
        if (resolved != null && !resolved.hasModifierProperty(PsiModifier.STATIC)) {
          final PsiClass containingClass = resolved.getContainingClass();
          if (containingClass != null) {
            final PsiTypeParameter[] containingClassTypeParameters = containingClass.getTypeParameters();
            if (containingClassTypeParameters.length > 0) {
              return false;
            }
          }
        }
        return true;
      }
    }

    return false;
  }
}
