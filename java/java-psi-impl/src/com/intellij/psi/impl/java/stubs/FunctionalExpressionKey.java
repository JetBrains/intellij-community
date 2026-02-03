// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java.stubs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class FunctionalExpressionKey {
  public static final int UNKNOWN_PARAM_COUNT = -1;
  private final int lambdaParameterCount;
  private final CoarseType lambdaReturnType;
  private final @NotNull String knownType;

  public FunctionalExpressionKey(int lambdaParameterCount, @NotNull CoarseType lambdaReturnType, @Nullable String knownFunExprType) {
    this.lambdaParameterCount = lambdaParameterCount;
    this.lambdaReturnType = lambdaReturnType;
    this.knownType = StringUtil.notNullize(knownFunExprType);
  }

  public static @NotNull FunctionalExpressionKey deserializeKey(@NotNull DataInput dataStream) throws IOException {
    int parameterCount = dataStream.readByte();
    CoarseType type = CoarseType.values()[dataStream.readByte()];
    String knownType = IOUtil.readUTF(dataStream);
    return new FunctionalExpressionKey(parameterCount, type, knownType);
  }

  public void serializeKey(@NotNull DataOutput dataStream) throws IOException {
    dataStream.writeByte(lambdaParameterCount);
    dataStream.writeByte(lambdaReturnType.ordinal());
    IOUtil.writeUTF(dataStream, knownType);
  }

  public boolean canRepresent(int samParamCount, boolean booleanCompatible, boolean isVoid) {
    if (lambdaParameterCount >= 0 && samParamCount != lambdaParameterCount) return false;

    switch (lambdaReturnType) {
      case VOID: return isVoid;
      case NON_VOID: return !isVoid;
      case BOOLEAN: return booleanCompatible;
      default: return true;
    }
  }

  public static boolean isBooleanCompatible(PsiType samType) {
    return PsiTypes.booleanType().equals(samType) || TypeConversionUtil.isAssignableFromPrimitiveWrapper(TypeConversionUtil.erasure(samType));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FunctionalExpressionKey)) return false;

    FunctionalExpressionKey key = (FunctionalExpressionKey)o;

    if (lambdaParameterCount != key.lambdaParameterCount) return false;
    if (lambdaReturnType != key.lambdaReturnType) return false;
    if (!knownType.equals(key.knownType)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = lambdaParameterCount;
    result = 31 * result + lambdaReturnType.ordinal();
    result = 31 * result + knownType.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "FunctionalExpressionKey{" +
           "lambdaParameterCount=" + lambdaParameterCount +
           ", lambdaReturnType=" + lambdaReturnType +
           ", knownType='" + knownType + '\'' +
           '}';
  }

  public enum CoarseType {
    VOID, UNKNOWN, BOOLEAN, NON_VOID
  }
}
