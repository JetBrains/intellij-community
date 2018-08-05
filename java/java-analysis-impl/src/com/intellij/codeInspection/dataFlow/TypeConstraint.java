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

import com.intellij.codeInspection.dataFlow.value.DfaPsiType;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Immutable class representing a number of non-primitive type constraints applied to some value.
 * There are two types of constrains: value is instance of some type and value is not an instance of some type.
 * Unlike usual Java semantics, the {@code null} value is considered to be instanceof any type (non-null instanceof can be expressed
 * via additional restriction {@link DfaFactType#CAN_BE_NULL} {@code = false}).
 */
public abstract class TypeConstraint {

  @NotNull
  public abstract String getPresentationText(@Nullable PsiType type);

  @Nullable
  public abstract TypeConstraint withInstanceofValue(@NotNull DfaPsiType type);

  @Nullable
  public abstract TypeConstraint withNotInstanceofValue(DfaPsiType type);

  @NotNull
  abstract TypeConstraint withoutType(@NotNull DfaPsiType type);

  @Nullable
  public abstract PsiType getPsiType();

  abstract boolean isSuperStateOf(@NotNull TypeConstraint other);

  @Nullable
  public abstract TypeConstraint union(@NotNull TypeConstraint other);

  @Nullable
  abstract TypeConstraint intersect(@NotNull TypeConstraint right);

  @NotNull
  public abstract Set<DfaPsiType> getInstanceofValues();

  @NotNull
  public abstract Set<DfaPsiType> getNotInstanceofValues();

  public abstract boolean isEmpty();

  public abstract boolean isExact();


  static final class Exact extends TypeConstraint {
    final @NotNull DfaPsiType myType;

    Exact(@NotNull DfaPsiType type) {
      myType = type;
    }

    @NotNull
    @Override
    public String getPresentationText(@Nullable PsiType type) {
      return myType.getPsiType().equals(type) ? "" : "exactly " + myType;
    }

    @Nullable
    @Override
    public TypeConstraint withInstanceofValue(@NotNull DfaPsiType type) {
      return type.isAssignableFrom(myType) ? this : null;
    }

    @Nullable
    @Override
    public TypeConstraint withNotInstanceofValue(DfaPsiType type) {
      return type.isAssignableFrom(myType) ? null : this;
    }

    @NotNull
    @Override
    TypeConstraint withoutType(@NotNull DfaPsiType type) {
      return myType == type ? Constrained.EMPTY : this;
    }

    @NotNull
    @Override
    public PsiType getPsiType() {
      return myType.getPsiType();
    }

    @Override
    boolean isSuperStateOf(@NotNull TypeConstraint other) {
      return this.equals(other);
    }

    @Nullable
    @Override
    public TypeConstraint union(@NotNull TypeConstraint other) {
      if(isSuperStateOf(other)) return this;
      if(other.isSuperStateOf(this)) return other;
      return new Constrained(Collections.singleton(myType), Collections.emptySet()).union(other);
    }

    @Override
    @Nullable
    TypeConstraint intersect(@NotNull TypeConstraint right) {
      if (right instanceof Exact) {
        return right.equals(this) ? this : null;
      }
      TypeConstraint result = this;
      for (DfaPsiType type : right.getInstanceofValues()) {
        result = result.withInstanceofValue(type);
        if (result == null) return null;
      }
      for (DfaPsiType type : right.getNotInstanceofValues()) {
        result = result.withNotInstanceofValue(type);
        if (result == null) return null;
      }
      return result;
    }

    @NotNull
    @Override
    public Set<DfaPsiType> getInstanceofValues() {
      return Collections.singleton(myType);
    }

    @NotNull
    @Override
    public Set<DfaPsiType> getNotInstanceofValues() {
      return Collections.emptySet();
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean isExact() {
      return true;
    }

    @Override
    public int hashCode() {
      return myType.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj instanceof Exact && ((Exact)obj).myType.equals(myType);
    }

    @Override
    public String toString() {
      return "exactly "+myType;
    }
  }

  private static final class Constrained extends TypeConstraint {
    /**
     * An instance representing no constraints
     */
    private static final TypeConstraint EMPTY = new Constrained(Collections.emptySet(), Collections.emptySet());
    @NotNull private final Set<DfaPsiType> myInstanceofValues;
    @NotNull private final Set<DfaPsiType> myNotInstanceofValues;

    Constrained(@NotNull Set<DfaPsiType> instanceofValues, @NotNull Set<DfaPsiType> notInstanceofValues) {
      myInstanceofValues = instanceofValues;
      myNotInstanceofValues = notInstanceofValues;
    }

    @Override
    @NotNull
    public String getPresentationText(@Nullable PsiType type) {
      Set<DfaPsiType> instanceOfTypes = myInstanceofValues;
      if (type != null) {
        instanceOfTypes = StreamEx.of(instanceOfTypes)
          .removeBy(DfaPsiType::getPsiType, DfaPsiType.normalizeType(type))
          .toSet();
      }
      return EntryStream.of("instanceof ", instanceOfTypes,
                            "not instanceof ", myNotInstanceofValues)
        .removeValues(Set::isEmpty)
        .mapKeyValue((prefix, set) -> StreamEx.of(set).map(DfaPsiType::toString).sorted().joining(", ", prefix, ""))
        .joining("\n");
    }

    boolean checkInstanceofValue(@NotNull DfaPsiType dfaType) {
      if (myInstanceofValues.contains(dfaType)) return true;

      for (DfaPsiType dfaTypeValue : myNotInstanceofValues) {
        if (dfaTypeValue.isAssignableFrom(dfaType)) return false;
      }

      for (DfaPsiType dfaTypeValue : myInstanceofValues) {
        if (!dfaType.isConvertibleFrom(dfaTypeValue)) return false;
      }

      return true;
    }

    @Override
    @Nullable
    public TypeConstraint withInstanceofValue(@NotNull DfaPsiType type) {
      PsiType psiType = type.getPsiType();
      if (psiType instanceof PsiPrimitiveType || LambdaUtil.notInferredType(psiType)) return this;

      PsiClass psiClass = PsiUtil.resolveClassInClassTypeOnly(psiType);
      if (psiClass != null && psiClass.hasModifierProperty(PsiModifier.FINAL)) {
        return new Exact(type).intersect(this);
      }

      if (!checkInstanceofValue(type)) {
        return null;
      }
      List<DfaPsiType> moreGeneric = new ArrayList<>();
      for (DfaPsiType alreadyInstanceof : myInstanceofValues) {
        if (type.isAssignableFrom(alreadyInstanceof)) {
          return this;
        }
        if (alreadyInstanceof.isAssignableFrom(type)) {
          moreGeneric.add(alreadyInstanceof);
        }
      }

      Set<DfaPsiType> newInstanceof = ContainerUtil.newHashSet(myInstanceofValues);
      newInstanceof.removeAll(moreGeneric);
      newInstanceof.add(type);
      return create(newInstanceof, myNotInstanceofValues);
    }

    @Override
    @Nullable
    public TypeConstraint withNotInstanceofValue(DfaPsiType type) {
      if (myNotInstanceofValues.contains(type)) return this;

      for (DfaPsiType dfaTypeValue : myInstanceofValues) {
        if (type.isAssignableFrom(dfaTypeValue)) return null;
      }

      List<DfaPsiType> moreSpecific = new ArrayList<>();
      for (DfaPsiType alreadyNotInstanceof : myNotInstanceofValues) {
        if (alreadyNotInstanceof.isAssignableFrom(type)) {
          return this;
        }
        if (type.isAssignableFrom(alreadyNotInstanceof)) {
          moreSpecific.add(alreadyNotInstanceof);
        }
      }

      Set<DfaPsiType> newNotInstanceof = ContainerUtil.newHashSet(myNotInstanceofValues);
      newNotInstanceof.removeAll(moreSpecific);
      newNotInstanceof.add(type);
      return create(myInstanceofValues, newNotInstanceof);
    }

    @Override
    @NotNull
    TypeConstraint withoutType(@NotNull DfaPsiType type) {
      if (myInstanceofValues.contains(type)) {
        HashSet<DfaPsiType> newInstanceof = ContainerUtil.newHashSet(myInstanceofValues);
        newInstanceof.remove(type);
        return create(newInstanceof, myNotInstanceofValues);
      }
      if (myNotInstanceofValues.contains(type)) {
        HashSet<DfaPsiType> newNotInstanceof = ContainerUtil.newHashSet(myNotInstanceofValues);
        newNotInstanceof.remove(type);
        return create(myInstanceofValues, newNotInstanceof);
      }
      return this;
    }

    @Override
    @Nullable
    public PsiType getPsiType() {
      PsiType[] conjuncts = StreamEx.of(myInstanceofValues).map(DfaPsiType::getPsiType).toArray(PsiType.EMPTY_ARRAY);
      return conjuncts.length == 0 ? null : PsiIntersectionType.createIntersection(true, conjuncts);
    }

    @Override
    boolean isSuperStateOf(@NotNull TypeConstraint other) {
      if (other instanceof Constrained) {
        Constrained that = (Constrained)other;
        if (that.myNotInstanceofValues.containsAll(myNotInstanceofValues) && that.myInstanceofValues.containsAll(myInstanceofValues)) {
          return true;
        }
        if (this.myNotInstanceofValues.isEmpty() && that.myNotInstanceofValues.isEmpty()) {
          return that.myInstanceofValues.stream().allMatch(
            thatType -> this.myInstanceofValues.stream().allMatch(thisType -> thisType.isAssignableFrom(thatType)));
        }
      } else if (other instanceof Exact) {
        DfaPsiType otherType = ((Exact)other).myType;
        return this.myInstanceofValues.stream().allMatch(otherType::isAssignableFrom) &&
               this.myNotInstanceofValues.stream().noneMatch(otherType::isAssignableFrom);
      }
      return false;
    }

    @Override
    @Nullable
    public TypeConstraint union(@NotNull TypeConstraint other) {
      if(isSuperStateOf(other)) return this;
      if(other.isSuperStateOf(this)) return other;
      if (other instanceof Constrained) {
        return union((Constrained)other);
      }
      if (other instanceof Exact) {
        return union(new Constrained(Collections.singleton(((Exact)other).myType), Collections.emptySet()));
      }
      return EMPTY;
    }

    @Override
    @Nullable
    TypeConstraint intersect(@NotNull TypeConstraint right) {
      if (right instanceof Exact) {
        return right.intersect(this);
      }
      TypeConstraint result = this;
      for (DfaPsiType type : right.getInstanceofValues()) {
        result = result.withInstanceofValue(type);
        if (result == null) return null;
      }
      for (DfaPsiType type : right.getNotInstanceofValues()) {
        result = result.withNotInstanceofValue(type);
        if (result == null) return null;
      }
      return result;
    }

    private TypeConstraint union(@NotNull Constrained other) {
      Set<DfaPsiType> notTypes = new HashSet<>(this.myNotInstanceofValues);
      notTypes.retainAll(other.myNotInstanceofValues);
      Set<DfaPsiType> instanceOfTypes;
      if (this.myInstanceofValues.containsAll(other.myInstanceofValues)) {
        instanceOfTypes = other.myInstanceofValues;
      } else if (other.myInstanceofValues.containsAll(this.myInstanceofValues)) {
        instanceOfTypes = this.myInstanceofValues;
      } else {
        instanceOfTypes = withSuper(this.myInstanceofValues);
        instanceOfTypes.retainAll(withSuper(other.myInstanceofValues));
      }
      TypeConstraint constraint = EMPTY;
      for (DfaPsiType type: instanceOfTypes) {
        constraint = constraint.withInstanceofValue(type);
        if (constraint == null) {
          // Should not happen normally, but may happen with inconsistent hierarchy (e.g. if final class is extended)
          return EMPTY;
        }
      }
      for (DfaPsiType type: notTypes) {
        constraint = constraint.withNotInstanceofValue(type);
        if (constraint == null) return EMPTY;
      }
      return constraint;
    }

    private static Set<DfaPsiType> withSuper(Set<DfaPsiType> instanceofValues) {
      Set<DfaPsiType> result = new HashSet<>(instanceofValues);
      for (DfaPsiType type : instanceofValues) {
        InheritanceUtil.processSuperTypes(type.getPsiType(), false, t -> result.add(type.getFactory().createDfaType(t)));
      }
      return result;
    }

    @Override
    @NotNull
    public Set<DfaPsiType> getInstanceofValues() {
      return Collections.unmodifiableSet(myInstanceofValues);
    }

    @Override
    @NotNull
    public Set<DfaPsiType> getNotInstanceofValues() {
      return Collections.unmodifiableSet(myNotInstanceofValues);
    }

    @Override
    public boolean isEmpty() {
      return myInstanceofValues.isEmpty() && myNotInstanceofValues.isEmpty();
    }

    @Override
    public boolean isExact() {
      return false;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Constrained that = (Constrained)o;
      return Objects.equals(myInstanceofValues, that.myInstanceofValues) &&
             Objects.equals(myNotInstanceofValues, that.myNotInstanceofValues);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myInstanceofValues, myNotInstanceofValues);
    }

    @Override
    public String toString() {
      return EntryStream.of("instanceof ", myInstanceofValues,
                            "not instanceof ", myNotInstanceofValues)
        .removeValues(Set::isEmpty)
        .mapKeyValue((prefix, set) -> StreamEx.of(set).joining(", ", prefix, ""))
        .joining(" ");
    }

  }

  private static TypeConstraint create(@NotNull Set<DfaPsiType> instanceofValues, @NotNull Set<DfaPsiType> notInstanceofValues) {
    if (instanceofValues.isEmpty() && notInstanceofValues.isEmpty()) {
      return Constrained.EMPTY;
    }
    if (instanceofValues.isEmpty()) {
      instanceofValues = Collections.emptySet();
    }
    else if (instanceofValues.size() == 1) {
      instanceofValues = Collections.singleton(instanceofValues.iterator().next());
    }
    if (notInstanceofValues.isEmpty()) {
      notInstanceofValues = Collections.emptySet();
    }
    else if (notInstanceofValues.size() == 1) {
      notInstanceofValues = Collections.singleton(notInstanceofValues.iterator().next());
    }
    return new TypeConstraint.Constrained(instanceofValues, notInstanceofValues);
  }

  @Nullable
  public static DfaFactMap withInstanceOf(@NotNull DfaFactMap map, @NotNull DfaPsiType type) {
    TypeConstraint constraint = map.get(DfaFactType.TYPE_CONSTRAINT);
    if (constraint == null) constraint = Constrained.EMPTY;
    constraint = constraint.withInstanceofValue(type);
    return constraint == null ? null : map.with(DfaFactType.TYPE_CONSTRAINT, constraint);
  }

  public static TypeConstraint exact(@NotNull DfaPsiType type) {
    return new Exact(type);
  }

  public static TypeConstraint empty() {
    return Constrained.EMPTY;
  }
}
