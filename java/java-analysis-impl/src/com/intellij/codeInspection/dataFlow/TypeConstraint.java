// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiType;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.EntryStream;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable object representing a number of type constraints applied to some reference value.
 * Type constraints represent a lattice with {@link TypeConstraints#TOP} and {@link TypeConstraints#BOTTOM}
 * elements, as well as {@link #join(TypeConstraint)} and {@link #meet(TypeConstraint)} operations.
 *
 * Besides TOP and BOTTOM there are two types of constrains: {@link Exact} (value is known to have exactly some JVM type)
 * and {@link Constrained} (value is instanceof zero or more JVM types and not instanceof zero or more JVM types).
 *
 * value is instance of some type and value is not an instance of some type.
 * Null or primitive types are not handled here.
 */
public interface TypeConstraint {
  /**
   * @param other other constraint to join with
   * @return joined constraint. If some type satisfies either this or other constraint, it also satisfies the resulting constraint.
   */
  @NotNull
  TypeConstraint join(@NotNull TypeConstraint other);

  @Nullable TypeConstraint tryJoinExactly(TypeConstraint other);

  /**
   * @param other other constraint to meet with
   * @return intersection constraint. If some type satisfies the resulting constraint, it also satisfies both this and other constraints.
   */
  @NotNull
  TypeConstraint meet(@NotNull TypeConstraint other);

  /**
   * @param other other constraint to check
   * @return true if every type satisfied by other constraint is also satisfied by this constraint.
   */
  boolean isSuperConstraintOf(@NotNull TypeConstraint other);

  /**
   * @return negated constraint (a constraint that satisfied only by types not satisfied by this constraint).
   * Null if such a constraint cannot be created.
   */
  default @Nullable TypeConstraint tryNegate() {
    return null;
  }

  /**
   * @param project current project
   * @return the narrowest PsiType that contains all the values satisfied by this constraint.
   */
  default @Nullable PsiType getPsiType(Project project) {
    return null;
  }

  /**
   * @param type declared PsiType of the value
   * @return presentation text that tells about additional constraints; can be empty if no additional constraints are known
   */
  default @NotNull @NlsSafe String getPresentationText(@Nullable PsiType type) {
    return toShortString();
  }

  /**
   * @return true if this constraint represents an exact type
   */
  default boolean isExact() {
    return false;
  }

  /**
   * @param className fully-qualified class name to check
   * @return true if this constraint represents a class with a given class name
   */
  default boolean isExact(@NotNull String className) {
    return false;
  }

  /**
   * @return true if this type constraint represents a known singleton type (an object that has exactly one instance)
   */
  default boolean isSingleton() {
    return false;
  }

  /**
   * @param className fully-qualified class name to check
   * @return true if all types that satisfy this constraint are subtypes of a given class name
   */
  boolean isSubtypeOf(@NotNull String className);

  /**
   * @return true if the types represented by this constraint are known to be compared by .equals() within DFA algorithm
   */
  default boolean isComparedByEquals() {
    return false;
  }

  /**
   * @return true if given type is resolved
   */
  default boolean isResolved() {
    return true;
  }

  /**
   * @param otherType          other type
   * @param expectedAssignable whether other type is expected to be assignable from this, or not
   * @param elementTitle
   * @return textual explanation about why expected assignability cannot be satisfied; null if it can be satisfied, or
   * explanation cannot be found.
   */
  default @Nullable @Nls String getAssignabilityExplanation(@NotNull TypeConstraint otherType,
                                                            boolean expectedAssignable,
                                                            @Nls String elementTitle) {
    return null;
  }

  /**
   * @return stream of "instanceof" constraints of this type
   */
  default StreamEx<Exact> instanceOfTypes() {
    return StreamEx.empty();
  }

  /**
   * @return stream of "not-instanceof" constraints of this type
   */
  default StreamEx<Exact> notInstanceOfTypes() {
    return StreamEx.empty();
  }

  /**
   * @return a {@link DfType} that represents any object that satisfies this constraint, or null (nullability is unknown)
   */
  default DfType asDfType() {
    return this == TypeConstraints.BOTTOM ? DfType.BOTTOM :
           DfTypes.customObject(this, DfaNullability.UNKNOWN, Mutability.UNKNOWN, null, DfType.BOTTOM);
  }

  /**
   * @return a short string representation
   */
  default String toShortString() {
    return toString();
  }

  /**
   * @return an array component type for an array type; BOTTOM if this type is not always an array type
   */
  default @NotNull DfType getArrayComponentType() {
    return DfType.BOTTOM;
  }

  /**
   * @return true if this constraint always represents an array type
   */
  default boolean isArray() {
    return false;
  }

  /**
   * @return type that represents unboxed type of this type;
   * {@link DfType#TOP} if this constraint can be one of several primitive wrappers
   * {@link DfType#BOTTOM} if this constraint is not primitive wrapper
   */
  default DfType getUnboxedType() {
    return DfType.BOTTOM;
  }

  /**
   * @return true if this type always represents a primitive wrapper
   */
  default boolean isPrimitiveWrapper() {
    return false;
  }

  /**
   * @return true if this type always represents an enum
   */
  default boolean isEnum() {
    return false;
  }

  default @Nullable PsiEnumConstant getEnumConstant(int ordinal) {
    return null;
  }

  /**
   * @param type {@link DfType} to extract {@link TypeConstraint} from
   * @return an extracted type constraint
   */
  static @NotNull TypeConstraint fromDfType(@NotNull DfType type) {
    return type instanceof DfReferenceType ? ((DfReferenceType)type).getConstraint() :
           type == DfType.BOTTOM ? TypeConstraints.BOTTOM :
           TypeConstraints.TOP;
  }

  /**
   * Represents an exact type. It may also represent types that cannot be instantiated (e.g. interface types), so no object
   * could satisfy them, but they are still useful as building blocks for {@link Constrained}.
   */
  interface Exact extends TypeConstraint {
    @Override
    default @NotNull TypeConstraint join(@NotNull TypeConstraint other) {
      if (other == TypeConstraints.BOTTOM || this.equals(other)) return this;
      if (other == TypeConstraints.TOP) return other;
      return new Constrained(Collections.singleton(this), Collections.emptySet()).join(other);
    }

    @Override
    default @Nullable TypeConstraint tryJoinExactly(TypeConstraint other) {
      if (other == TypeConstraints.BOTTOM || this.equals(other)) return this;
      if (other == TypeConstraints.TOP) return other;
      return new Constrained(Collections.singleton(this), Collections.emptySet()).tryJoinExactly(other);
    }

    @Override
    default @NotNull TypeConstraint meet(@NotNull TypeConstraint other) {
      if (this.equals(other) || other.isSuperConstraintOf(this)) return this;
      return TypeConstraints.BOTTOM;
    }

    /**
     * @return true if the type represented by this constraint cannot have subtypes
     */
    boolean isFinal();

    @Override
    default boolean isExact() {
      return true;
    }

    @Override
    default boolean isExact(@NotNull String className) {
      return className.equals(toString());
    }

    @Override
    default boolean isSubtypeOf(@NotNull String className) {
      return isExact(className) || superTypes().anyMatch(st -> st.isExact(className));
    }

    /**
     * @return true if instances of this type can exist (i.e. the type is not abstract).
     */
    default boolean canBeInstantiated() {
      return true;
    }

    /**
     * @return stream of supertypes
     */
    StreamEx<Exact> superTypes();

    /**
     * @param other type to test assignability
     * @return true if this type is assignable from the other type
     */
    boolean isAssignableFrom(@NotNull Exact other);

    /**
     * @param other type to test convertibility
     * @return true if this type is convertible from the other type
     */
    boolean isConvertibleFrom(@NotNull Exact other);

    @Override
    default StreamEx<Exact> instanceOfTypes() {
      return StreamEx.of(this);
    }

    @Override
    default String getAssignabilityExplanation(@NotNull TypeConstraint otherType,
                                               boolean expectedAssignable,
                                               @Nls String elementTitle) {
      Exact exact = otherType.instanceOfTypes().collect(MoreCollectors.onlyOne()).orElse(null);
      if (exact == null) return null;
      boolean actual = exact.isAssignableFrom(this);
      if (actual != expectedAssignable) return null;
      if (expectedAssignable) {
        if (equals(exact)) {
          return JavaAnalysisBundle.message("type.constraint.assignability.explanation.exact", elementTitle, toShortString());
        }
        return JavaAnalysisBundle.message("type.constraint.assignability.explanation.exact.subtype", elementTitle, toShortString(), exact.toShortString());
      }
      else {
        return JavaAnalysisBundle.message("type.constraint.assignability.explanation.exact.not.subtype", elementTitle, toShortString(), exact.toShortString());
      }
    }

    @Override
    default boolean isSuperConstraintOf(@NotNull TypeConstraint other) {
      return other == TypeConstraints.BOTTOM || this.equals(other);
    }

    @Override
    default TypeConstraint tryNegate() {
      return isFinal() ? notInstanceOf() : null;
    }

    /**
     * @return a constraint that represents objects not only of this type but also of any subtypes. May return self if the type is final.
     */
    default @NotNull TypeConstraint instanceOf() {
      if (isFinal()) return canBeInstantiated() ? this : TypeConstraints.BOTTOM;
      return new Constrained(Collections.singleton(this), Collections.emptySet());
    }

    /**
     * @return a constraint that represents objects that are not instanceof this type
     */
    default @NotNull TypeConstraint notInstanceOf() {
      return new Constrained(Collections.emptySet(), Collections.singleton(this));
    }

    @Override
    default String toShortString() {
      return StringUtil.getShortName(toString());
    }

    @Override
    default @NotNull String getPresentationText(@Nullable PsiType type) {
      return type != null && TypeConstraints.exact(type).equals(this) ? "" : "exactly " + toShortString();
    }
  }

  /**
   * A non-exact, constrained type
   */
  final class Constrained implements TypeConstraint {
    private static final Set<String> WRAPPER_SUPER_TYPES = Set.of(CommonClassNames.JAVA_LANG_OBJECT,
                                                                  CommonClassNames.JAVA_LANG_NUMBER,
                                                                  CommonClassNames.JAVA_IO_SERIALIZABLE,
                                                                  CommonClassNames.JAVA_LANG_COMPARABLE,
                                                                  "java.lang.constant.Constable",
                                                                  "java.lang.constant.ConstantDesc");
    private final @NotNull Set<Exact> myInstanceOf;
    private final @NotNull Set<Exact> myNotInstanceOf;

    Constrained(@NotNull Set<Exact> instanceOf, @NotNull Set<Exact> notInstanceOf) {
      assert !instanceOf.isEmpty() || !notInstanceOf.isEmpty();
      myInstanceOf = instanceOf;
      myNotInstanceOf = notInstanceOf;
    }

    @Override
    public boolean isResolved() {
      return ContainerUtil.and(myInstanceOf, Exact::isResolved);
    }

    @Override
    public @Nullable PsiType getPsiType(Project project) {
      PsiType[] conjuncts = StreamEx.of(myInstanceOf).map(exact -> exact.getPsiType(project)).nonNull().toArray(PsiType.EMPTY_ARRAY);
      return conjuncts.length == 0 ? null : PsiIntersectionType.createIntersection(true, conjuncts);
    }

    @Override
    public @Nullable TypeConstraint tryNegate() {
      if (myInstanceOf.size() == 1 && myNotInstanceOf.isEmpty()) {
        return myInstanceOf.iterator().next().notInstanceOf();
      }
      if (myNotInstanceOf.size() == 1 && myInstanceOf.isEmpty()) {
        return myNotInstanceOf.iterator().next().instanceOf();
      }
      return null;
    }

    @Override
    public @NotNull TypeConstraint join(@NotNull TypeConstraint other) {
      if (isSuperConstraintOf(other)) return this;
      if (other.isSuperConstraintOf(this)) return other;
      if (other instanceof Constrained) {
        return joinWithConstrained((Constrained)other);
      }
      if (other instanceof Exact) {
        return joinWithConstrained(new Constrained(Collections.singleton((Exact)other), Collections.emptySet()));
      }
      return TypeConstraints.TOP;
    }

    @Override
    public @Nullable TypeConstraint tryJoinExactly(TypeConstraint other) {
      if (isSuperConstraintOf(other)) return this;
      if (other.isSuperConstraintOf(this)) return other;
      if (other instanceof Constrained) {
        Constrained constrained = (Constrained)other;
        if (myInstanceOf.equals(constrained.myInstanceOf)) {
          return joinWithConstrained(constrained);
        }
        int size1 = myInstanceOf.size() + myNotInstanceOf.size();
        int size2 = constrained.myInstanceOf.size() + constrained.myNotInstanceOf.size();
        // size1 >= 3 check allows to avoid merging too eagerly and preserves 'possible CCE' warnings
        if (size1 == size2 && size1 >= 3) {
          if (myInstanceOf.containsAll(constrained.myInstanceOf) &&
              constrained.myNotInstanceOf.containsAll(myNotInstanceOf)) {
            Set<Exact> diff = new HashSet<>(myInstanceOf);
            diff.removeAll(constrained.myInstanceOf);
            if (diff.size() == 1 && constrained.myNotInstanceOf.containsAll(diff)) {
              return new Constrained(constrained.myInstanceOf, myNotInstanceOf);
            }
          }
          if (constrained.myInstanceOf.containsAll(myInstanceOf) &&
              myNotInstanceOf.containsAll(constrained.myNotInstanceOf)) {
            Set<Exact> diff = new HashSet<>(constrained.myInstanceOf);
            diff.removeAll(myInstanceOf);
            if (diff.size() == 1 && myNotInstanceOf.containsAll(diff)) {
              return new Constrained(myInstanceOf, constrained.myNotInstanceOf);
            }
          }
        }
      }
      return null;
    }

    private @NotNull TypeConstraint joinWithConstrained(@NotNull Constrained other) {
      Set<Exact> notTypes = new HashSet<>(this.myNotInstanceOf);
      notTypes.retainAll(other.myNotInstanceOf);
      Set<Exact> instanceOfTypes;
      if (this.myInstanceOf.containsAll(other.myInstanceOf)) {
        instanceOfTypes = other.myInstanceOf;
      }
      else if (other.myInstanceOf.containsAll(this.myInstanceOf)) {
        instanceOfTypes = this.myInstanceOf;
      }
      else {
        instanceOfTypes = withSuper(this.myInstanceOf);
        instanceOfTypes.retainAll(withSuper(other.myInstanceOf));
      }
      TypeConstraint constraint = TypeConstraints.TOP;
      for (Exact type: instanceOfTypes) {
        constraint = constraint.meet(type.instanceOf());
      }
      for (Exact type: notTypes) {
        constraint = constraint.meet(type.notInstanceOf());
      }
      return constraint;
    }

    private static @NotNull Set<Exact> withSuper(@NotNull Set<Exact> instanceofValues) {
      return StreamEx.of(instanceofValues).flatMap(Exact::superTypes).append(instanceofValues).toSet();
    }

    private @Nullable Constrained withInstanceofValue(@NotNull Exact type) {
      if (myInstanceOf.contains(type)) return this;

      for (Exact notInst : myNotInstanceOf) {
        if (notInst.isAssignableFrom(type)) return null;
      }

      Set<Exact> newInstanceof = new HashSet<>(myInstanceOf);
      for (Exact alreadyInstanceof : myInstanceOf) {
        if (type.isAssignableFrom(alreadyInstanceof)) {
          return this;
        }
        if (!type.isConvertibleFrom(alreadyInstanceof)) {
          return null;
        }
        if (alreadyInstanceof.isAssignableFrom(type)) {
          newInstanceof.remove(alreadyInstanceof);
        }
      }
      newInstanceof.add(type);
      return new Constrained(newInstanceof, myNotInstanceOf);
    }

    private @Nullable Constrained withNotInstanceofValue(Exact type) {
      if (myNotInstanceOf.contains(type)) return this;

      for (Exact dfaTypeValue : myInstanceOf) {
        if (type.isAssignableFrom(dfaTypeValue)) return null;
      }

      Set<Exact> newNotInstanceof = new HashSet<>(myNotInstanceOf);
      for (Exact alreadyNotInstanceof : myNotInstanceOf) {
        if (alreadyNotInstanceof.isAssignableFrom(type)) {
          return this;
        }
        if (type.isAssignableFrom(alreadyNotInstanceof)) {
          newNotInstanceof.remove(alreadyNotInstanceof);
        }
      }
      newNotInstanceof.add(type);

      return new Constrained(myInstanceOf, newNotInstanceof);
    }

    @Override
    public @NotNull TypeConstraint meet(@NotNull TypeConstraint other) {
      if (this.isSuperConstraintOf(other)) return other;
      if (other.isSuperConstraintOf(this)) return this;
      if (!(other instanceof Constrained)) return TypeConstraints.BOTTOM;
      Constrained right = (Constrained)other;

      Constrained result = this;
      for (Exact type : right.myInstanceOf) {
        result = result.withInstanceofValue(type);
        if (result == null) return TypeConstraints.BOTTOM;
      }
      for (Exact type : right.myNotInstanceOf) {
        result = result.withNotInstanceofValue(type);
        if (result == null) return TypeConstraints.BOTTOM;
      }
      return result;
    }

    @Override
    public boolean isSuperConstraintOf(@NotNull TypeConstraint other) {
      if (other == TypeConstraints.BOTTOM) return true;
      if (other instanceof Constrained) {
        Constrained that = (Constrained)other;
        if (!that.myNotInstanceOf.containsAll(myNotInstanceOf)) {
          if (that.myInstanceOf.isEmpty()) return false;
          for (Exact thisNotType : this.myNotInstanceOf) {
            if (!that.myNotInstanceOf.contains(thisNotType)) {
              for (Exact thatType : that.myInstanceOf) {
                if (thisNotType.isConvertibleFrom(thatType)) {
                  return false;
                }
              }
            }
          }
        }
        if (that.myInstanceOf.containsAll(myInstanceOf)) return true;
        if (that.myInstanceOf.isEmpty()) return myInstanceOf.isEmpty();
        for (Exact thatType : that.myInstanceOf) {
          for (Exact thisType : this.myInstanceOf) {
            if (!thisType.isAssignableFrom(thatType)) {
              return false;
            }
          }
        }
        return true;
      } else if (other instanceof Exact) {
        Exact otherType = (Exact)other;
        for (Exact thisInstance : this.myInstanceOf) {
          if (!thisInstance.isAssignableFrom(otherType)) {
            return false;
          }
        }
        for (Exact thisNotInstance : this.myNotInstanceOf) {
          if (thisNotInstance.isAssignableFrom(otherType)) {
            return false;
          }
        }
        return true;
      }
      return false;
    }

    @Override
    public String getAssignabilityExplanation(@NotNull TypeConstraint otherType,
                                              boolean expectedAssignable,
                                              @Nls String elementTitle) {
      Exact exact = otherType.instanceOfTypes().collect(MoreCollectors.onlyOne()).orElse(null);
      if (exact == null) return null;
      if (expectedAssignable) {
        for (Exact inst : myInstanceOf) {
          if (exact.isAssignableFrom(inst)) {
            if (exact == inst) {
              return JavaAnalysisBundle.message("type.constraint.assignability.explanation.exact", elementTitle, inst.toShortString());
            }
            else {
              return JavaAnalysisBundle.message("type.constraint.assignability.explanation.subtype.of.subtype",
                                                elementTitle, inst.toShortString(), exact.toShortString());
            }
          }
        }
      }
      else {
        for (Exact notInst : myNotInstanceOf) {
          if (notInst.isAssignableFrom(exact)) {
            if (exact == notInst) {
              return JavaAnalysisBundle.message("type.constraint.assignability.explanation.not.instance.of", elementTitle, notInst.toShortString());
            }
            else {
              return JavaAnalysisBundle.message("type.constraint.assignability.explanation.not.instance.of.supertype", elementTitle, notInst.toShortString(), exact.toShortString());
            }
          }
        }
        for (Exact inst : myInstanceOf) {
          if (!exact.isConvertibleFrom(inst)) {
            return JavaAnalysisBundle.message("type.constraint.assignability.explanation.definitely.inconvertible", elementTitle, inst.toShortString(), exact.toShortString());
          }
        }
      }
      return null;
    }

    @Override
    public StreamEx<Exact> instanceOfTypes() {
      return StreamEx.of(myInstanceOf);
    }

    @Override
    public StreamEx<Exact> notInstanceOfTypes() {
      return StreamEx.of(myNotInstanceOf);
    }

    @Override
    public @NotNull DfType getArrayComponentType() {
      return instanceOfTypes().map(Exact::getArrayComponentType)
        .reduce(DfType::meet).orElse(DfType.BOTTOM);
    }

    @Override
    public DfType getUnboxedType() {
      return instanceOfTypes().allMatch(t -> WRAPPER_SUPER_TYPES.contains(t.toString())) ? DfType.TOP : DfType.BOTTOM;
    }

    @Override
    public boolean isArray() {
      return instanceOfTypes().anyMatch(Exact::isArray);
    }

    @Override
    public boolean isEnum() {
      return myInstanceOf.size() == 1 && myInstanceOf.iterator().next().isEnum();
    }

    @Override
    public @Nullable PsiEnumConstant getEnumConstant(int ordinal) {
      return myInstanceOf.size() == 1 ? myInstanceOf.iterator().next().getEnumConstant(ordinal) : null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Constrained that = (Constrained)o;
      return Objects.equals(myInstanceOf, that.myInstanceOf) &&
             Objects.equals(myNotInstanceOf, that.myNotInstanceOf);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myInstanceOf, myNotInstanceOf);
    }

    @Override
    public @NotNull String toString() {
      return EntryStream.of("instanceof ", myInstanceOf,
                            "not instanceof ", myNotInstanceOf)
        .removeValues(Set::isEmpty)
        .mapKeyValue((prefix, set) -> StreamEx.of(set).joining(", ", prefix, ""))
        .joining(" ");
    }

    @Override
    public @NotNull String getPresentationText(@Nullable PsiType type) {
      Set<Exact> instanceOfTypes = myInstanceOf;
      Exact exact = type == null ? null : ObjectUtils.tryCast(TypeConstraints.exact(type), Exact.class);
      if (exact != null) {
        instanceOfTypes = StreamEx.of(instanceOfTypes)
          .without(exact)
          .toSet();
      }
      return EntryStream.of("instanceof ", instanceOfTypes,
                            "not instanceof ", myNotInstanceOf)
        .removeValues(Set::isEmpty)
        .mapKeyValue((prefix, set) -> StreamEx.of(set).map(Exact::toShortString).sorted().joining(", ", prefix, ""))
        .joining("\n");
    }

    @Override
    public boolean isSubtypeOf(@NotNull String className) {
      return instanceOfTypes().anyMatch(ex -> ex.isSubtypeOf(className));
    }
  }
}
