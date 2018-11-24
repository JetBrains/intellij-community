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
package com.intellij.codeInspection.bytecodeAnalysis;

import com.intellij.openapi.vfs.CharsetToolkit;
import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;
import java.util.Arrays;

import static com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisConverter.getMessageDigest;

/**
 * Hashed representation of method.
 */
public final class HMethod implements MethodDescriptor {
  // how many bytes are taken from class fqn digest
  private static final int CLASS_HASH_SIZE = 10;
  // how many bytes are taken from signature digest
  private static final int SIGNATURE_HASH_SIZE = 4;
  static final int HASH_SIZE = CLASS_HASH_SIZE + SIGNATURE_HASH_SIZE;

  @NotNull
  final byte[] myBytes;

  HMethod(Method method, MessageDigest md) {
    if (md == null) {
      md = getMessageDigest();
    }
    byte[] classDigest = md.digest(method.internalClassName.getBytes(CharsetToolkit.UTF8_CHARSET));
    md.update(method.methodName.getBytes(CharsetToolkit.UTF8_CHARSET));
    md.update(method.methodDesc.getBytes(CharsetToolkit.UTF8_CHARSET));
    byte[] sigDigest = md.digest();
    myBytes = new byte[HASH_SIZE];
    System.arraycopy(classDigest, 0, myBytes, 0, CLASS_HASH_SIZE);
    System.arraycopy(sigDigest, 0, myBytes, CLASS_HASH_SIZE, SIGNATURE_HASH_SIZE);
  }

  public HMethod(@NotNull byte[] bytes) {
    myBytes = bytes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return Arrays.equals(myBytes, ((HMethod)o).myBytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(myBytes);
  }

  @NotNull
  @Override
  public HMethod hashed(MessageDigest md) {
    return this;
  }

  public String toString() {
    return bytesToString(myBytes);
  }

  static String bytesToString(byte[] key) {
    return IntStreamEx.of(key).mapToObj(b -> String.format("%02x", b & 0xFF)).joining(".");
  }
}
