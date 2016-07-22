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

import com.google.common.base.Objects;
import com.intellij.util.ThreeState;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.IOUtil;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * @author peter
 */
public class FunctionalExpressionKey {
  public static final int UNKNOWN_PARAM_COUNT = -1;
  public static final int MAX_ARG_COUNT = 10;
  @NotNull public final String methodName;
  public final int lambdaParameterCount;
  public final int methodArgsLength;
  public final int callArgIndex;
  public final ThreeState isVoid;

  public FunctionalExpressionKey(@NotNull String methodName,
                                 int lambdaParameterCount,
                                 int methodArgsLength,
                                 int callArgIndex,
                                 ThreeState isVoid) {
    this.methodName = methodName;
    this.lambdaParameterCount = lambdaParameterCount;
    this.methodArgsLength = Math.min(methodArgsLength, MAX_ARG_COUNT);
    this.callArgIndex = Math.min(callArgIndex, MAX_ARG_COUNT - 1);
    this.isVoid = isVoid;
  }

  @NotNull
  public static FunctionalExpressionKey deserializeKey(@NotNull DataInput dataStream) throws IOException {
    String methodName = IOUtil.readUTF(dataStream);
    int parameterCount = DataInputOutputUtil.readINT(dataStream);
    int methodArgsLength = DataInputOutputUtil.readINT(dataStream);
    int argIndex = DataInputOutputUtil.readINT(dataStream);
    ThreeState voidCompatible = ThreeState.values()[dataStream.readByte()];
    return new FunctionalExpressionKey(methodName, parameterCount, methodArgsLength, argIndex, voidCompatible);
  }

  public boolean canRepresent(int samParamCount, boolean samVoid) {
    return (samParamCount == lambdaParameterCount || lambdaParameterCount == -1) &&
           (isVoid == ThreeState.UNSURE || samVoid == isVoid.toBoolean());
  }

  public void serializeKey(@NotNull DataOutput dataStream) throws IOException {
    IOUtil.writeUTF(dataStream, methodName);
    DataInputOutputUtil.writeINT(dataStream, lambdaParameterCount);
    DataInputOutputUtil.writeINT(dataStream, methodArgsLength);
    DataInputOutputUtil.writeINT(dataStream, callArgIndex);
    dataStream.writeByte(isVoid.ordinal());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FunctionalExpressionKey)) return false;

    FunctionalExpressionKey key = (FunctionalExpressionKey)o;

    if (lambdaParameterCount != key.lambdaParameterCount) return false;
    if (methodArgsLength != key.methodArgsLength) return false;
    if (callArgIndex != key.callArgIndex) return false;
    if (!methodName.equals(key.methodName)) return false;
    if (isVoid != key.isVoid) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = methodName.hashCode();
    result = 31 * result + lambdaParameterCount;
    result = 31 * result + methodArgsLength;
    result = 31 * result + callArgIndex;
    result = 31 * result + isVoid.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
      .add("methodName", methodName)
      .add("lambdaParameterCount", lambdaParameterCount)
      .add("methodArgsLength", methodArgsLength)
      .add("callArgIndex", callArgIndex)
      .add("isVoid", isVoid)
      .toString();
  }
}
