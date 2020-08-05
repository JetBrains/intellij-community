// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis.asm;

import org.jetbrains.annotations.Contract;
import org.jetbrains.org.objectweb.asm.Type;
import org.jetbrains.org.objectweb.asm.tree.analysis.BasicValue;
import org.jetbrains.org.objectweb.asm.tree.analysis.Frame;
import org.jetbrains.org.objectweb.asm.tree.analysis.Value;

import java.util.List;

/**
 * @author lambdamix
 */
public final class ASMUtils {
  public static final Type THIS_TYPE = Type.getObjectType("this");
  public static final Type THROWABLE_TYPE = Type.getObjectType("java/lang/Throwable");
  public static final BasicValue THIS_VALUE = new BasicValue(THIS_TYPE);
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
  public static boolean isThisType(Type tp) {
    return THIS_TYPE.equals(tp);
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

  public static <V extends Value> Frame<V>[] newFrameArray(int size) {
    @SuppressWarnings("unchecked") Frame<V>[] a = (Frame<V>[])new Frame[size];
    return a;
  }

  public static <V> List<V>[] newListArray(int size) {
    @SuppressWarnings("unchecked") List<V>[] a = (List<V>[])new List[size];
    return a;
  }
}