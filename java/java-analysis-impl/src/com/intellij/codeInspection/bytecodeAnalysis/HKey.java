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

import one.util.streamex.IntStreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Small size key, constructed by hashing method signature.
 * 'H' in this and related class names stands for 'Hash'.
 * @see BytecodeAnalysisConverter for details of construction.
 */
public final class HKey {
  @NotNull
  final byte[] key;
  final int dirKey;
  final boolean stable;
  final boolean negated;

  HKey(@NotNull byte[] key, int dirKey, boolean stable, boolean negated) {
    this.key = key;
    this.dirKey = dirKey;
    this.stable = stable;
    this.negated = negated;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HKey hKey = (HKey)o;
    if (dirKey != hKey.dirKey) return false;
    if (stable != hKey.stable) return false;
    if (negated != hKey.negated) return false;
    if (!Arrays.equals(key, hKey.key)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = Arrays.hashCode(key);
    result = 31 * result + dirKey;
    result = 31 * result + (stable ? 1 : 0);
    result = 31 * result + (negated ? 1 : 0);
    return result;
  }

  HKey invertStability() {
    return new HKey(key, dirKey, !stable, negated);
  }

  HKey mkStable() {
    return stable ? this : new HKey(key, dirKey, true, negated);
  }

  HKey mkUnstable() {
    return stable ? new HKey(key, dirKey, false, negated) : this;
  }

  public HKey mkBase() {
    return dirKey == 0 ? this : new HKey(key, 0, stable, false);
  }

  HKey withDirection(Direction dir) {
    return new HKey(key, dir.asInt(), stable, false);
  }

  HKey negate() {
    return new HKey(key, dirKey, stable, true);
  }

  public Direction getDirection() {
    return Direction.fromInt(dirKey);
  }

  @Override
  public String toString() {
    return "HKey [" + bytesToString(key) + "|" + (stable ? "S" : "-") + (negated ? "N" : "-") + "|" + getDirection() + "]";
  }

  static String bytesToString(byte[] key) {
    return IntStreamEx.of(key).mapToObj(b -> String.format("%02x", b & 0xFF)).joining(".");
  }
}
