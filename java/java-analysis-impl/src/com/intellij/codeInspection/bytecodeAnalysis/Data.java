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

import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

/**
 * Represents a lattice product of a constant {@link #value} and all {@link #ids}.
 */
final class Component {
  static final Component[] EMPTY_ARRAY = new Component[0];
  @NotNull Value value;
  @NotNull final EKey[] ids;

  Component(@NotNull Value value, @NotNull Set<EKey> ids) {
    this(value, ids.toArray(new EKey[0]));
  }

  Component(@NotNull Value value, @NotNull EKey[] ids) {
    this.value = value;
    this.ids = ids;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Component that = (Component)o;

    return value == that.value && Arrays.equals(ids, that.ids);
  }

  @Override
  public int hashCode() {
    return 31 * value.hashCode() + Arrays.hashCode(ids);
  }

  public boolean remove(@NotNull EKey id) {
    boolean removed = false;
    for (int i = 0; i < ids.length; i++) {
      if (id.equals(ids[i])) {
        ids[i] = null;
        removed = true;
      }
    }
    return removed;
  }

  public boolean isEmpty() {
    for (EKey id : ids) {
      if (id != null) return false;
    }
    return true;
  }

  @NotNull
  public Component copy() {
    return new Component(value, ids.clone());
  }
}

final class Equation {
  @NotNull final EKey key;
  @NotNull final Result result;

  Equation(@NotNull EKey key, @NotNull Result result) {
    this.key = key;
    this.result = result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Equation equation = (Equation)o;
    return key.equals(equation.key) && result.equals(equation.result);
  }

  @Override
  public int hashCode() {
    return 31 * key.hashCode() + result.hashCode();
  }

  @Override
  public String toString() {
    return "Equation{" + "key=" + key + ", result=" + result + '}';
  }
}

class Equations {
  @NotNull final List<DirectionResultPair> results;
  final boolean stable;

  Equations(@NotNull List<DirectionResultPair> results, boolean stable) {
    this.results = results;
    this.stable = stable;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Equations that = (Equations)o;
    return stable == that.stable && results.equals(that.results);
  }

  @Override
  public int hashCode() {
    return 31 * results.hashCode() + (stable ? 1 : 0);
  }

  @NotNull
  Equations update(Direction direction, Effects newResult) {
    List<DirectionResultPair> newPairs = StreamEx.of(this.results)
      .map(drp -> drp.updateForDirection(direction, newResult))
      .nonNull()
      .toList();
    return new Equations(newPairs, this.stable);
  }

  Optional<Result> find(Direction direction) {
    int key = direction.asInt();
    return StreamEx.of(results).findFirst(pair -> pair.directionKey == key).map(pair -> pair.result);
  }
}

class DirectionResultPair {
  final int directionKey;
  @NotNull
  final Result result;

  DirectionResultPair(int directionKey, @NotNull Result result) {
    this.directionKey = directionKey;
    this.result = result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DirectionResultPair that = (DirectionResultPair)o;
    return directionKey == that.directionKey && result.equals(that.result);
  }

  @Override
  public int hashCode() {
    return 31 * directionKey + result.hashCode();
  }

  @Override
  public String toString() {
    return Direction.fromInt(directionKey) + "->" + result;
  }

  @Nullable
  DirectionResultPair updateForDirection(Direction direction, Result newResult) {
    if (this.directionKey == direction.asInt()) {
      return newResult == null ? null : new DirectionResultPair(direction.asInt(), newResult);
    }
    else {
      return this;
    }
  }
}

interface Result {
  /**
   * @return a stream of keys which should be solved to make this result final
   */
  default Stream<EKey> dependencies() {
    return Stream.empty();
  }
}
final class Final implements Result {
  @NotNull final Value value;

  Final(@NotNull Value value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return value == ((Final)o).value;
  }

  @Override
  public int hashCode() {
    return value.ordinal();
  }

  @Override
  public String toString() {
    return "Final[" + value + ']';
  }
}

final class Pending implements Result {
  @NotNull final Component[] delta; // sum

  Pending(Collection<Component> delta) {
    this(delta.toArray(Component.EMPTY_ARRAY));
  }

  Pending(@NotNull Component[] delta) {
    this.delta = delta;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    return Arrays.equals(delta, ((Pending)o).delta);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(delta);
  }

  @NotNull
  Pending copy() {
    Component[] copy = new Component[delta.length];
    for (int i = 0; i < delta.length; i++) {
      copy[i] = delta[i].copy();
    }
    return new Pending(copy);
  }

  @Override
  public Stream<EKey> dependencies() {
    return Arrays.stream(delta).flatMap(component -> Stream.of(component.ids));
  }

  @Override
  public String toString() {
    return "Pending["+delta.length+"]";
  }
}

final class Effects implements Result {
  static final Set<EffectQuantum> TOP_EFFECTS = Collections.singleton(EffectQuantum.TopEffectQuantum);

  @NotNull final DataValue returnValue;
  @NotNull final Set<EffectQuantum> effects;

  Effects(@NotNull DataValue returnValue, @NotNull Set<EffectQuantum> effects) {
    this.returnValue = returnValue;
    this.effects = effects;
  }

  Effects combine(Effects other) {
    if(this.equals(other)) return this;
    Set<EffectQuantum> newEffects = new HashSet<>(this.effects);
    newEffects.addAll(other.effects);
    if(newEffects.contains(EffectQuantum.TopEffectQuantum)) {
      newEffects = TOP_EFFECTS;
    }
    DataValue newReturnValue = this.returnValue.equals(other.returnValue) ? this.returnValue : DataValue.UnknownDataValue1;
    return new Effects(newReturnValue, newEffects);
  }

  @Override
  public Stream<EKey> dependencies() {
    return Stream.concat(returnValue.dependencies(), effects.stream().flatMap(EffectQuantum::dependencies));
  }

  public boolean isTop() {
    return returnValue == DataValue.UnknownDataValue1 && effects.equals(TOP_EFFECTS);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Effects that = (Effects)o;
    return this.returnValue.equals(that.returnValue) && this.effects.equals(that.effects);
  }

  @Override
  public int hashCode() {
    return effects.hashCode() * 31 + returnValue.hashCode();
  }

  @Override
  public String toString() {
    Object effectsPresentation = effects.isEmpty() ? "Pure" : effects.size() == 1 ? effects.iterator().next() : effects.size();
    return "Effects[" + effectsPresentation + "|" + returnValue + "]";
  }
}