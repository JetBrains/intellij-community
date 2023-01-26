// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.lang.jvm.types.JvmPrimitiveTypeKind;
import org.jetbrains.annotations.NotNull;

public final class PsiTypes {
  /** 
   * Returns instance corresponding to {@code byte} type. 
   */
  public static @NotNull PsiPrimitiveType byteType() { 
    return BYTE; 
  }

  /** 
   * Returns instance corresponding to {@code char} type. 
   */
  public static @NotNull PsiPrimitiveType charType() { 
    return CHAR; 
  }

  /** 
   * Returns instance corresponding to {@code double} type. 
   */
  public static @NotNull PsiPrimitiveType doubleType() { 
    return DOUBLE; 
  }

  /** 
   * Returns instance corresponding to {@code float} type. 
   */
  public static @NotNull PsiPrimitiveType floatType() { 
    return FLOAT; 
  }

  /** 
   * Returns instance corresponding to {@code int} type. 
   */
  public static @NotNull PsiPrimitiveType intType() { 
    return INT; 
  }

  /** 
   * Returns instance corresponding to {@code long} type. 
   */
  public static @NotNull PsiPrimitiveType longType() { 
    return LONG; 
  }

  /** 
   * Returns instance corresponding to {@code short} type. 
   */
  public static @NotNull PsiPrimitiveType shortType() { 
    return SHORT; 
  }

  /** 
   * Returns instance corresponding to {@code boolean} type. 
   */
  public static @NotNull PsiPrimitiveType booleanType() { 
    return BOOLEAN; 
  }

  /** 
   * Returns instance corresponding to {@code void} type. 
   */
  public static @NotNull PsiPrimitiveType voidType() { 
    return VOID; 
  }

  /** 
   * Returns instance describing the type of {@code null} value. 
   */
  public static @NotNull PsiType nullType() { 
    return NULL;
  }

  private static final PsiPrimitiveType BYTE = new PsiPrimitiveType(JvmPrimitiveTypeKind.BYTE);
  private static final PsiPrimitiveType CHAR = new PsiPrimitiveType(JvmPrimitiveTypeKind.CHAR);
  private static final PsiPrimitiveType DOUBLE = new PsiPrimitiveType(JvmPrimitiveTypeKind.DOUBLE);
  private static final PsiPrimitiveType FLOAT = new PsiPrimitiveType(JvmPrimitiveTypeKind.FLOAT);
  private static final PsiPrimitiveType INT = new PsiPrimitiveType(JvmPrimitiveTypeKind.INT);
  private static final PsiPrimitiveType LONG = new PsiPrimitiveType(JvmPrimitiveTypeKind.LONG);
  private static final PsiPrimitiveType SHORT = new PsiPrimitiveType(JvmPrimitiveTypeKind.SHORT);
  private static final PsiPrimitiveType BOOLEAN = new PsiPrimitiveType(JvmPrimitiveTypeKind.BOOLEAN);
  private static final PsiPrimitiveType VOID = new PsiPrimitiveType(JvmPrimitiveTypeKind.VOID);
  private static final PsiPrimitiveType NULL = new PsiPrimitiveType(null);
  
  private PsiTypes() {
  }
}
