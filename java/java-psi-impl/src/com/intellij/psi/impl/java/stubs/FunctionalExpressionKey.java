/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.java.stubs;

import com.google.common.base.MoreObjects;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author peter
 */
public class FunctionalExpressionKey {
  public static final int UNKNOWN_PARAM_COUNT = -1;
  private final int lambdaParameterCount;
  private final CoarseType lambdaReturnType;
  @NotNull private final String knownType;

  public FunctionalExpressionKey(int lambdaParameterCount, @NotNull CoarseType lambdaReturnType, @Nullable String knownFunExprType) {
    this.lambdaParameterCount = lambdaParameterCount;
    this.lambdaReturnType = lambdaReturnType;
    this.knownType = StringUtil.notNullize(knownFunExprType);
  }

  @NotNull
  public static FunctionalExpressionKey deserializeKey(@NotNull DataInput dataStream) throws IOException {
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
    return PsiType.BOOLEAN.equals(samType) || TypeConversionUtil.isAssignableFromPrimitiveWrapper(TypeConversionUtil.erasure(samType));
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
    return MoreObjects.toStringHelper(this)
      .add("lambdaParameterCount", lambdaParameterCount)
      .add("type", lambdaReturnType)
      .add("knownType", knownType)
      .toString();
  }

  public enum CoarseType {
    VOID, UNKNOWN, BOOLEAN, NON_VOID
  }
}
