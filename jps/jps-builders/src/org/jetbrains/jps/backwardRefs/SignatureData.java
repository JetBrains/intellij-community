// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.backwardRefs;

import org.intellij.lang.annotations.MagicConstant;

public final class SignatureData {
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
