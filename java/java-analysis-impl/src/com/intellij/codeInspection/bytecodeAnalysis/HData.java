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

import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Represents a lattice product of a constant {@link #value} and all {@link #ids}.
 */
final class HComponent {
  static final HComponent[] EMPTY_ARRAY = new HComponent[0];
  static final ArrayFactory<HComponent> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new HComponent[count];
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