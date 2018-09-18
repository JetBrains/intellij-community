// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.annotations.Contract;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;

/**
 * @author lambdamix
 */
public class ASMUtils {
  public static final Type THROWABLE_TYPE = Type.getObjectType("java/lang/Throwable");
  public static final BasicValue THROWABLE_VALUE = new BasicValue(THROWABLE_TYPE);

  @Contract(pure = true)
  public static boolean isReferenceType(Type tp) {
    int sort = tp.getSort();
    return sort == Type.OBJECT || sort == Type.ARRAY;
  }

  @Contract(pure = true)
  public static boolean isBooleanType(Type tp) {
    return Type.BOOLEAN_TYPE.equals(tp);
  }

  @Contract(pure = true)
  public static int getSizeFast(String desc) {
    switch (desc.charAt(0)) {
      case 'J':
      case 'D':
        return 2;
      default:
        return 1;
    }
  }

  @Contract(pure = true)
  public static int getReturnSizeFast(String methodDesc) {
    switch (methodDesc.charAt(methodDesc.indexOf(')') + 1)) {
      case 'J':
      case 'D':
        return 2;
      default:
        return 1;
    }
  }

  @Contract(pure = true)
  public static boolean isReferenceReturnType(String methodDesc) {
    switch (methodDesc.charAt(methodDesc.indexOf(')') + 1)) {
      case 'L':
      case '[':
        return true;
      default:
        return false;
    }
  }
}