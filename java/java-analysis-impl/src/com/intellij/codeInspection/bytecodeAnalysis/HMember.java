// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.openapi.vfs.CharsetToolkit;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

import static com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisConverter.getMessageDigest;

/**
 * Hashed representation of method.
 */
public final class HMember implements MemberDescriptor {
  // how many bytes are taken from class fqn digest
  private static final int CLASS_HASH_SIZE = Long.BYTES+Short.BYTES;
  // how many bytes are taken from signature digest
  private static final int SIGNATURE_HASH_SIZE = Integer.BYTES;
  static final int HASH_SIZE = CLASS_HASH_SIZE + SIGNATURE_HASH_SIZE;

  final long myClassHi;
  final short myClassLo;
  final int myMethod;

  HMember(Member method, MessageDigest md) {
    if (md == null) {
      md = getMessageDigest();
    }
    byte[] classDigest = md.digest(method.internalClassName.getBytes(CharsetToolkit.UTF8_CHARSET));
    ByteBuffer classBuffer = ByteBuffer.wrap(classDigest);
    myClassHi = classBuffer.getLong();
    myClassLo = classBuffer.getShort();

    md.update(method.methodName.getBytes(CharsetToolkit.UTF8_CHARSET));
    md.update(method.methodDesc.getBytes(CharsetToolkit.UTF8_CHARSET));
    byte[] sigDigest = md.digest();
    myMethod = ByteBuffer.wrap(sigDigest).getInt();
  }

  public HMember(@NotNull byte[] bytes) {
    ByteBuffer buffer = ByteBuffer.wrap(bytes);
    myClassHi = buffer.getLong();
    myClassLo = buffer.getShort();
    myMethod = buffer.getInt();
  }

  @NotNull
  byte[] asBytes() {
    ByteBuffer bytes = ByteBuffer.allocate(HASH_SIZE);
    bytes.putLong(myClassHi).putShort(myClassLo).putInt(myMethod);
    return bytes.array();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HMember that = (HMember)o;
    return that.myClassHi == myClassHi && that.myClassLo == myClassLo && that.myMethod == myMethod;
  }

  @Override
  public int hashCode() {
    // Must work as Arrays.hashCode(asBytes()) to preserve compatibility with old caches
    int result = 1;
    for (int i = Long.BYTES - 1; i >= 0; i--) result = result * 31 + (byte)((myClassHi >>> (i * 8)) & 0xFF);
    for (int i = Short.BYTES - 1; i >= 0; i--) result = result * 31 + (byte)((myClassLo >>> (i * 8)) & 0xFF);
    for (int i = Integer.BYTES - 1; i >= 0; i--) result = result * 31 + (byte)((myMethod >>> (i * 8)) & 0xFF);
    return result;
  }

  @NotNull
  @Override
  public HMember hashed(MessageDigest md) {
    return this;
  }

  public String toString() {
    return bytesToString(asBytes());
  }

  static String bytesToString(byte[] key) {
    return IntStreamEx.of(key).mapToObj(b -> String.format("%02x", b & 0xFF)).joining(".");
  }
}