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

import org.jetbrains.annotations.NotNull;

import java.security.MessageDigest;

/**
 * Equation key (or variable)
 */
public final class EKey {
  @NotNull
  final MethodDescriptor method;
  final int dirKey;
  final boolean stable;
  final boolean negated;

  public EKey(@NotNull MethodDescriptor method, Direction direction, boolean stable) {
    this(method, direction, stable, false);
  }

  EKey(@NotNull MethodDescriptor method, Direction direction, boolean stable, boolean negated) {
    this(method, direction.asInt(), stable, negated);
  }

  EKey(@NotNull MethodDescriptor method, int dirKey, boolean stable, boolean negated) {
    this.method = method;
    this.dirKey = dirKey;
    this.stable = stable;
    this.negated = negated;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    EKey key = (EKey) o;

    if (stable != key.stable) return false;
    if (negated != key.negated) return false;
    if (dirKey != key.dirKey) return false;
    if (!method.equals(key.method)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = method.hashCode();
    result = 31 * result + dirKey;
    result = 31 * result + (stable ? 1 : 0);
    result = 31 * result + (negated ? 1 : 0);
    return result;
  }

  EKey invertStability() {
    return new EKey(method, dirKey, !stable, negated);
  }

  EKey mkStable() {
    return stable ? this : new EKey(method, dirKey, true, negated);
  }

  EKey mkUnstable() {
    return stable ? new EKey(method, dirKey, false, negated) : this;
  }

  public EKey mkBase() {
    return withDirection(Direction.Out);
  }

  EKey withDirection(Direction dir) {
    return dirKey == dir.asInt() ? this : new EKey(method, dir, stable, false);
  }

  EKey negate() {
    return new EKey(method, dirKey, stable, true);
  }

  public EKey hashed(MessageDigest md) {
    HMethod hmethod = method.hashed(md);
    return hmethod == method ? this : new EKey(hmethod, dirKey, stable, negated);
  }

  public Direction getDirection() {
    return Direction.fromInt(dirKey);
  }

  @Override
  public String toString() {
    return "Key [" + method + "|" + (stable ? "S" : "-") + (negated ? "N" : "-") + "|" + Direction.fromInt(dirKey) + "]";
  }
}
