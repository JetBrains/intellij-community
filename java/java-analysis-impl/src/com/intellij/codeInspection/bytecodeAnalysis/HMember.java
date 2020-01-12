// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.openapi.util.text.StringHash;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

/**
 * Hashed representation of method.
 */
public final class HMember implements MemberDescriptor {
  // how many bytes are taken from class fqn digest
  private static final int CLASS_HASH_SIZE = Long.BYTES;
  // how many bytes are taken from signature digest
  private static final int SIGNATURE_HASH_SIZE = Integer.BYTES;
  static final int HASH_SIZE = CLASS_HASH_SIZE + SIGNATURE_HASH_SIZE;

  final long myClass;
  final int myMethod;

  HMember(Member method) {
    myClass = StringHash.calc(method.internalClassName);
    myMethod = StringHash.murmur(method.methodName, 37) * 31 + StringHash.murmur(method.methodDesc, 41);
  }

  public HMember(byte @NotNull [] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    myClass = buffer.getLong();
    myMethod = buffer.getInt();
  }

  byte @NotNull [] asBytes() {
    ByteBuffer bytes = ByteBuffer.allocate(HASH_SIZE);
    bytes.putLong(myClass).putInt(myMethod);
    return bytes.array();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HMember that = (HMember)o;
    return that.myClass == myClass && that.myMethod == myMethod;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(myClass) * 31 + myMethod;
  }

  @NotNull
  @Override
  public HMember hashed() {
    return this;
  }

  public String toString() {
    return bytesToString(asBytes());
  }

  static String bytesToString(byte[] key) {
    return IntStreamEx.of(key).mapToObj(b -> String.format("%02x", b & 0xFF)).joining(".");
  }
}