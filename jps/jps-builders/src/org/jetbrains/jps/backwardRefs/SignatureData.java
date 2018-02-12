/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.jps.backwardRefs;

import org.intellij.lang.annotations.MagicConstant;

public class SignatureData {
  public static final byte ZERO_DIM = 0;
  public static final byte ARRAY_ONE_DIM = 1;
  // represents java's Iterator, Iterable and BaseStream
  public static final byte ITERATOR_ONE_DIM = -1;
  @MagicConstant(intValues = {ZERO_DIM, ARRAY_ONE_DIM, ITERATOR_ONE_DIM})
  public @interface IteratorKind {}

  private final int myRawReturnType;
  private final byte myArrayKind;
  private final boolean myStatic;

  public SignatureData(int rawReturnType, byte arrayKind, boolean isStatic) {
    myRawReturnType = rawReturnType;
    myArrayKind = arrayKind;
    myStatic = isStatic;
  }

  public int getRawReturnType() {
    return myRawReturnType;
  }

  public byte getIteratorKind() {
    return myArrayKind;
  }

  public boolean isStatic() {
    return myStatic;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SignatureData data = (SignatureData)o;

    if (myRawReturnType != data.myRawReturnType) return false;
    if (myArrayKind != data.myArrayKind) return false;
    if (myStatic != data.myStatic) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myRawReturnType;
    result = 31 * result + myArrayKind;
    result = 31 * result + (myStatic ? 1 : 0);
    return result;
  }
}
