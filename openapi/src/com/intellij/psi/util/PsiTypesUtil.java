/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.util;

import com.intellij.psi.*;

public class PsiTypesUtil {
  public static String getDefaultValueOfType(PsiType type) {
    if (type instanceof PsiArrayType) {
      int count = type.getArrayDimensions() - 1;
      PsiType componentType = type.getDeepComponentType();

      if (componentType instanceof PsiClassType) {
        final PsiClassType classType = (PsiClassType)componentType;
        if (classType.resolve() instanceof PsiTypeParameter) {
          return PsiKeyword.NULL;
        }
      }

      StringBuffer buffer = new StringBuffer();
      buffer.append(PsiKeyword.NEW);
      buffer.append(" ");
      buffer.append(componentType.getCanonicalText());
      buffer.append("[0]");
      for (int i = 0; i < count; i++) {
        buffer.append("[]");
      }
      return buffer.toString();
    }
    else if (type instanceof PsiPrimitiveType) {
      if (PsiType.BOOLEAN == type) {
        return PsiKeyword.FALSE;
      }
      else {
        return "0";
      }
    }
    else {
      return PsiKeyword.NULL;
    }
  }
}
