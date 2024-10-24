// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Represents a lattice product of a constant {@link #value} and all {@link #ids}.
 */
final class Component {
  static final Component[] EMPTY_ARRAY = new Component[0];
  @NotNull Value value;
  final EKey @NotNull [] ids;

  Component(@NotNull Value value, @NotNull Collection<EKey> ids) {
    this(value, ids.toArray(EKey.EMPTY_ARRAY));
  }

  Component(@NotNull Value value, EKey @NotNull ... ids) {
    this.value = value;
    this.ids = ids.length == 0 ? EKey.EMPTY_ARRAY : ids;
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

  public boolean isSuperStateOf(Component other) {
    return other.value == value && Arrays.asList(other.ids).containsAll(Arrays.asList(ids));
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
  @NotNull final List<? extends DirectionResultPair> results;
  final boolean stable;

  Equations(@NotNull List<? extends DirectionResultPair> results, boolean stable) {
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

  Optional<Result> find(Direction direction) {
    int key = direction.asInt();
    for (DirectionResultPair result : results) {
      if (result.directionKey == key) {
        return Optional.of(result).map(pair -> pair.result);
      }
    }
    return Optional.empty();
  }
}

final class DirectionResultPair {
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
}

sealed interface Result permits Effects, FieldAccess, Pending, Value {
  /**
   * @return a stream of keys which should be solved to make this result final
   */
  default Stream<EKey> dependencies() {
    return Stream.empty();
  }
  
  default void processDependencies(Consumer<EKey> processor) {
  }
}

/**
 * A result for the {@link Direction#Access} direction:
 * for setter/constructor parameter: unconditional field set;
 * for method: unconditional field return
 * @param name name of the field
 */
record FieldAccess(String name) implements Result {}

final class Pending implements Result {
  final Component @NotNull [] delta; // sum

  Pending(Collection<Component> delta) {
    this(delta.toArray(Component.EMPTY_ARRAY));
  }

  Pending(Component @NotNull [] delta) {
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
  public void processDependencies(Consumer<EKey> processor) {
    for (Component component : delta) {
      for (EKey id : component.ids) {
        processor.accept(id);
      }
    }
  }

  @Override
  public String toString() {
    return "Pending["+delta.length+"]";
  }
}

final class Effects implements Result {
  static final Set<EffectQuantum> TOP_EFFECTS = Set.of(EffectQuantum.TopEffectQuantum);
  static final Effects VOLATILE_EFFECTS = new Effects(DataValue.UnknownDataValue2, TOP_EFFECTS);

  @NotNull final DataValue returnValue;
  @NotNull final Set<EffectQuantum> effects;

  Effects(@NotNull DataValue returnValue, @NotNull Set<EffectQuantum> effects) {
    this.returnValue = returnValue;
    this.effects = effects;
  }

  Effects combine(Effects other) {
    if (this.equals(other)) return this;
    Set<EffectQuantum> newEffects;
    if (this.effects.containsAll(other.effects)) {
      newEffects = this.effects;
    }
    else if (other.effects.containsAll(this.effects)) {
      newEffects = other.effects;
    }
    else {
      newEffects = new HashSet<>(this.effects);
      newEffects.addAll(other.effects);
      if (newEffects.contains(EffectQuantum.TopEffectQuantum)) {
        newEffects = TOP_EFFECTS;
      }
      newEffects = Set.copyOf(newEffects);
    }
    DataValue newReturnValue = this.returnValue.equals(other.returnValue) ? this.returnValue : DataValue.UnknownDataValue1;
    return new Effects(newReturnValue, newEffects);
  }

  @Override
  public Stream<EKey> dependencies() {
    return Stream.concat(returnValue.dependencies(), effects.stream().flatMap(EffectQuantum::dependencies));
  }

  @Override
  public void processDependencies(Consumer<EKey> processor) {
    returnValue.processDependencies(processor);
    for (EffectQuantum effect : effects) {
      effect.processDependencies(processor);
    }
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