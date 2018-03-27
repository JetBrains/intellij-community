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
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.util.keyFMap.KeyFMap;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * An immutable collection of facts which are known for some value. Each fact is identified by {@link DfaFactType} and fact value.
 * A null value for some fact type means that the value is not restricted by given fact type or given fact type is not
 * applicable to given value.
 * <p>
 * To create a new {@code DfaFactMap}, use {@link #EMPTY} and call {@link #with(DfaFactType, Object)} method.
 *
 * @author Tagir Valeev
 */
public final class DfaFactMap {
  public static final DfaFactMap EMPTY = new DfaFactMap(KeyFMap.EMPTY_MAP);

  // Contains DfaFactType as keys only
  private final @NotNull KeyFMap myMap;

  private DfaFactMap(@NotNull KeyFMap map) {
    myMap = map;
  }

  /**
   * Returns a fact value for given fact type stored in current fact map or
   * null if current fact map is not restricted by given fact type.
   *
   * @param type fact type to fetch
   * @param <T> type of the fact value
   * @return a fact value or null
   */
  @Nullable
  public <T> T get(@NotNull DfaFactType<T> type) {
    return myMap.get(type);
  }

  /**
   * Returns a new fact map which is the same as current map, but replaces fact value of given type with the new value.
   *
   * @param type fact type to replace
   * @param value new value (supplying null here effectively removes the value)
   * @param <T> the type of fact value
   * @return a new fact map. May return itself if it's detected that this fact map already contains the supplied value.
   */
  @NotNull
  public <T> DfaFactMap with(@NotNull DfaFactType<T> type, @Nullable T value) {
    KeyFMap newMap = value == null || type.isUnknown(value) ? myMap.minus(type) : myMap.plus(type, value);
    return newMap == myMap ? this : new DfaFactMap(newMap);
  }

  /**
   * Checks whether the passed fact map is a sub-state of this map (i.e. any exact value
   * which conforms the passed fact map also conforms this fact map).
   *
   * @param subMap a fact map to check
   * @return true if this fact map is a super-state of supplied fact map.
   */
  public boolean isSuperStateOf(DfaFactMap subMap) {
    // absent fact is not always a superstate of present fact
    // e.g. absent CAN_BE_NULL means that nullability is unknown,
    // but CAN_BE_NULL=false means that value is definitely nullable and we should warn about nullability violation if any
    // so the (CAN_BE_NULL=false) state cannot be superseded by (CAN_BE_NULL=null) state
    for (DfaFactType<?> key : DfaFactType.getTypes()) {
      @SuppressWarnings("unchecked")
      DfaFactType<Object> type = (DfaFactType<Object>)key;
      Object thisValue = myMap.get(type);
      Object other = subMap.get(type);
      if(!type.isSuper(thisValue, other)) return false;
    }
    return true;
  }

  /**
   * Returns a fact map which is additionally restricted by supplied fact.
   * The returned map is a sub-state of this map.
   *
   * @param type  a type of a new fact
   * @param value a fact value which should be true for the resulting map. Passing null
   *              is essentially a no-op as no additional restriction is applied.
   * @param <T>   a fact value type
   * @return a new fact map or null if new fact is incompatible with current fact map (no value is possible
   * which conforms to the new fact and to this fact map simultaneously). May return itself if
   * it's known that new fact does not actually change this map.
   */
  @Nullable
  public <T> DfaFactMap intersect(@NotNull DfaFactType<T> type, @Nullable T value) {
    if (value == null) return this;
    T curFact = get(type);
    if (curFact == null) return with(type, value);
    T newFact = type.intersectFacts(curFact, value);
    return newFact == null ? null : with(type, newFact);
  }

  private <TT> DfaFactMap intersect(@NotNull DfaFactMap otherMap, @NotNull DfaFactType<TT> type) {
    return intersect(type, otherMap.get(type));
  }

  @Nullable
  public DfaFactMap intersect(@NotNull DfaFactMap other) {
    DfaFactMap result = this;
    List<DfaFactType<?>> types = DfaFactType.getTypes();
    for (DfaFactType<?> type : types) {
      result = result.intersect(other, type);
      if (result == null) return null;
    }
    return result;
  }

  /**
   * Returns a fact map which additionally allows having supplied value for the supplied fact
   *
   * @param type  a type of a new fact
   * @param value an additional fact value which should be allowed. Passing null means that fact may have any value
   * @param <T>   a fact value type
   * @return a new fact map. May return itself if it's known that new fact does not actually change this map.
   */
  @NotNull
  public <T> DfaFactMap union(@NotNull DfaFactType<T> type, @Nullable T value) {
    if (value == null) return with(type, null);
    T curFact = get(type);
    if (curFact == null) return this;
    T newFact = type.unionFacts(curFact, value);
    return with(type, newFact);
  }

  @NotNull
  private <TT> DfaFactMap union(DfaFactMap otherMap, @NotNull DfaFactType<TT> type) {
    return union(type, otherMap.get(type));
  }

  @NotNull
  public DfaFactMap union(@NotNull DfaFactMap other) {
    return StreamEx.of(DfaFactType.getTypes()).foldLeft(this, (map, type) -> map.union(other, type));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    return o instanceof DfaFactMap && myMap.equals(((DfaFactMap)o).myMap);
  }

  @Override
  public int hashCode() {
    return myMap.hashCode();
  }

  @SuppressWarnings("unchecked")
  @Override
  public String toString() {
    return StreamEx.of(myMap.getKeys())
      .map(key -> ((DfaFactType<Object>)key).toString(Objects.requireNonNull(myMap.get(key))))
      .joining(", ");
  }

  /**
   * Calculate facts from variable itself (not knowing its state). This method should not be used directly.
   * Instead use {@link DfaVariableValue#getInherentFacts()} which caches the calculated value.
   *
   * @param value variable value to calculate facts from
   * @return a calculated fact map.
   */
  public static DfaFactMap calcFromVariable(@NotNull DfaVariableValue value) {
    return StreamEx.of(DfaFactType.getTypes()).foldLeft(EMPTY, (factMap, type) -> updateMap(factMap, type, value));
  }

  private static <T> DfaFactMap updateMap(DfaFactMap map, DfaFactType<T> factType, DfaVariableValue value) {
    return map.with(factType, factType.calcFromVariable(value));
  }
}
