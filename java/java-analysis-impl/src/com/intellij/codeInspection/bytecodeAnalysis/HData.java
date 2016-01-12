/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Small size key, constructed by hashing method signature.
 * 'H' in this and related class names stands for 'Hash'.
 * @see com.intellij.codeInspection.bytecodeAnalysis.BytecodeAnalysisConverter for details of construction.
 */
final class HKey {
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

  HKey updateDirection(int newDirKey) {
    return new HKey(key, newDirKey, stable, false);
  }

  HKey negate() {
    return new HKey(key, dirKey, stable, true);
  }
}

/**
 * Represents a lattice product of a constant {@link #value} and all {@link #ids}.
 */
final class HComponent {
  @NotNull Value value;
  @NotNull final HKey[] ids;

  HComponent(@NotNull Value value, @NotNull HKey[] ids) {
    this.value = value;
    this.ids = ids;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HComponent that = (HComponent)o;

    if (!Arrays.equals(ids, that.ids)) return false;
    if (value != that.value) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = value.hashCode();
    result = 31 * result + Arrays.hashCode(ids);
    return result;
  }

  public boolean remove(@NotNull HKey id) {
    return HUtils.remove(ids, id);
  }

  public boolean isEmpty() {
    return HUtils.isEmpty(ids);
  }

  @NotNull
  public HComponent copy() {
    return new HComponent(value, ids.clone());
  }
}

class HUtils {

  static boolean isEmpty(HKey[] ids) {
    for (HKey id : ids) {
      if (id != null) return false;
    }
    return true;
  }

  static boolean remove(HKey[] ids, @NotNull HKey id) {
    boolean removed = false;
    for (int i = 0; i < ids.length; i++) {
      if (id.equals(ids[i])) {
        ids[i] = null;
        removed = true;
      }
    }
    return removed;
  }
}

final class HEquation {
  @NotNull final HKey key;
  @NotNull final HResult result;

  HEquation(@NotNull HKey key, @NotNull HResult result) {
    this.key = key;
    this.result = result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HEquation hEquation = (HEquation)o;
    if (!key.equals(hEquation.key)) return false;
    if (!result.equals(hEquation.result)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result1 = key.hashCode();
    result1 = 31 * result1 + result.hashCode();
    return result1;
  }
}

/**
 * Bytes of primary HKey of a method.
 */
final class Bytes {
  @NotNull
  final byte[] bytes;
  Bytes(@NotNull byte[] bytes) {
    this.bytes = bytes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return Arrays.equals(bytes, ((Bytes)o).bytes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(bytes);
  }
}

class HEquations {
  @NotNull final List<DirectionResultPair> results;
  final boolean stable;

  HEquations(@NotNull List<DirectionResultPair> results, boolean stable) {
    this.results = results;
    this.stable = stable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HEquations that = (HEquations)o;

    if (stable != that.stable) return false;
    if (!results.equals(that.results)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = results.hashCode();
    result = 31 * result + (stable ? 1 : 0);
    return result;
  }
}

class DirectionResultPair {
  final int directionKey;
  @NotNull
  final HResult hResult;

  DirectionResultPair(int directionKey, @NotNull HResult hResult) {
    this.directionKey = directionKey;
    this.hResult = hResult;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DirectionResultPair that = (DirectionResultPair)o;

    if (directionKey != that.directionKey) return false;
    if (!hResult.equals(that.hResult)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = directionKey;
    result = 31 * result + hResult.hashCode();
    return result;
  }
}

interface HResult {}
final class HFinal implements HResult {
  @NotNull final Value value;

  HFinal(@NotNull Value value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HFinal hFinal = (HFinal)o;

    if (value != hFinal.value) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return value.ordinal();
  }
}

final class HPending implements HResult {
  @NotNull final HComponent[] delta; // sum

  HPending(@NotNull HComponent[] delta) {
    this.delta = delta;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HPending hPending = (HPending)o;
    if (!Arrays.equals(delta, hPending.delta)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(delta);
  }

  @NotNull
  HPending copy() {
    HComponent[] delta1 = new HComponent[delta.length];
    for (int i = 0; i < delta.length; i++) {
      delta1[i] = delta[i].copy();

    }
    return new HPending(delta1);
  }
}

final class HEffects implements HResult {
  @NotNull final Set<HEffectQuantum> effects;

  HEffects(@NotNull Set<HEffectQuantum> effects) {
    this.effects = effects;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    HEffects hEffects = (HEffects)o;
    return effects.equals(hEffects.effects);
  }

  @Override
  public int hashCode() {
    return effects.hashCode();
  }
}