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

import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntegralType;
import com.intellij.codeInspection.dataFlow.types.DfLongType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaBinOpValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A type of the fact which restricts some value.
 *
 * @author Tagir Valeev
 * @deprecated Will be removed once {@link TrackingRunner} is adapted to avoid it
 */
@Deprecated
abstract class DfaFactType<T> extends Key<T> {
  private static final List<DfaFactType<?>> ourFactTypes = new ArrayList<>();

  /**
   * This fact specifies whether the value can be null. The absence of the fact means that the nullability is unknown.
   */
  public static final DfaFactType<DfaNullability> NULLABILITY = new DfaFactType<DfaNullability>("Nullability") {
    @NotNull
    @Override
    public String toString(@NotNull DfaNullability fact) {
      return fact.getInternalName();
    }

    @Override
    public boolean isUnknown(@NotNull DfaNullability fact) {
      return fact == DfaNullability.UNKNOWN;
    }

    @Override
    boolean isSuper(@Nullable DfaNullability superFact, @Nullable DfaNullability subFact) {
      return (superFact == null && (subFact == DfaNullability.NOT_NULL || subFact == DfaNullability.FLUSHED)) ||
             super.isSuper(superFact, subFact);
    }

    @NotNull
    @Override
    DfaNullability uniteFacts(@NotNull DfaNullability left, @NotNull DfaNullability right) {
      return left.unite(right);
    }

    @Nullable
    @Override
    DfaNullability intersectFacts(@NotNull DfaNullability left, @NotNull DfaNullability right) {
      return left.intersect(right);
    }

    @Nullable
    @Override
    public DfaNullability fromDfaValue(DfaValue value) {
      DfaNullability nullability = DfaNullability.fromDfType(value.getDfType());
      return nullability == DfaNullability.UNKNOWN ? null : nullability;
    }
  };

  public static final DfaFactType<Mutability> MUTABILITY = new DfaFactType<Mutability>("Mutability") {
    @Override
    public boolean isUnknown(@NotNull Mutability fact) {
      return fact == Mutability.UNKNOWN;
    }

    @NotNull
    @Override
    Mutability uniteFacts(@NotNull Mutability left, @NotNull Mutability right) {
      return left.unite(right);
    }
  };

  /**
   * This fact is applied to the integral values (of types byte, char, short, int, long).
   * Its value represents a range of possible values.
   */
  public static final DfaFactType<LongRangeSet> RANGE = new DfaFactType<LongRangeSet>("Range") {
    @Override
    boolean isSuper(@Nullable LongRangeSet superFact, @Nullable LongRangeSet subFact) {
      return superFact == null || subFact != null && superFact.contains(subFact);
    }

    @Override
    public boolean isUnknown(@NotNull LongRangeSet fact) {
      return LongRangeSet.all().equals(fact);
    }

    @Nullable
    @Override
    public LongRangeSet fromDfaValue(DfaValue value) {
      if(value instanceof DfaVariableValue) {
        return calcFromVariable((DfaVariableValue)value);
      }
      if(value instanceof DfaBinOpValue) {
        DfaBinOpValue binOp = (DfaBinOpValue)value;
        LongRangeSet left = fromDfaValue(binOp.getLeft());
        LongRangeSet right = fromDfaValue(binOp.getRight());
        if (left != null && right != null) {
          return left.binOpFromToken(binOp.getTokenType(), right, PsiType.LONG.equals(binOp.getType()));
        }
      }
      return DfLongType.extractRange(value.getDfType());
    }

    @Nullable
    private LongRangeSet calcFromVariable(@NotNull DfaVariableValue var) {
      VariableDescriptor descriptor = var.getDescriptor();
      if(descriptor instanceof SpecialField) {
        DfType dfType = ((SpecialField)descriptor).getDefaultValue(false);
        if (dfType instanceof DfIntegralType) {
          return ((DfIntegralType)dfType).getRange();
        }
      }
      LongRangeSet fromType = LongRangeSet.fromType(var.getType());
      return fromType == null ? null : LongRangeSet.fromPsiElement(var.getPsiVariable()).intersect(fromType);
    }

    @NotNull
    @Override
    LongRangeSet uniteFacts(@NotNull LongRangeSet left, @NotNull LongRangeSet right) {
      return left.unite(right);
    }

    @Nullable
    @Override
    LongRangeSet intersectFacts(@NotNull LongRangeSet left, @NotNull LongRangeSet right) {
      LongRangeSet intersection = left.intersect(right);
      return intersection.isEmpty() ? null : intersection;
    }
  };
  /**
   * This fact represents a set of possible types of this value
   * {@link TypeConstraint#empty()} value is equivalent to absent fact (not constrained)
   */
  public static final DfaFactType<TypeConstraint> TYPE_CONSTRAINT = new DfaFactType<TypeConstraint>("Constraints") {
    @Override
    boolean isSuper(@Nullable TypeConstraint superFact, @Nullable TypeConstraint subFact) {
      return superFact == null || (subFact != null && superFact.isSuperStateOf(subFact));
    }

    @Override
    public boolean isUnknown(@NotNull TypeConstraint fact) {
      return fact.isEmpty();
    }

    @Nullable
    @Override
    TypeConstraint intersectFacts(@NotNull TypeConstraint left, @NotNull TypeConstraint right) {
      return left.intersect(right);
    }

    @NotNull
    @Override
    TypeConstraint uniteFacts(@NotNull TypeConstraint left, @NotNull TypeConstraint right) {
      return left.unite(right);
    }
  };

  public static final DfaFactType<Boolean> LOCALITY = new DfaFactType<Boolean>("Locality") {
    @Override
    public boolean isUnknown(@NotNull Boolean fact) {
      return !fact;
    }

    @NotNull
    @Override
    public String toString(@NotNull Boolean fact) {
      return fact ? "local object" : "";
    }
  };
  
  public static final DfaFactType<SpecialFieldValue> SPECIAL_FIELD_VALUE = new DfaFactType<SpecialFieldValue>("Special field value") {
    @NotNull
    @Override
    public String getName(SpecialFieldValue fact) {
      return fact == null ? super.getName(null) : StringUtil.wordsToBeginFromUpperCase(fact.getField().toString());
    }

    @Nullable
    @Override
    SpecialFieldValue uniteFacts(@NotNull SpecialFieldValue left, @NotNull SpecialFieldValue right) {
      return left.unite(right);
    }
  };

  @NotNull
  private final String myName;

  private DfaFactType(@NotNull String name) {
    super("DfaFactType: " + name);
    myName = name;
    // Thread-safe as all DfaFactType instances are created only from DfaFactType class static initializer
    ourFactTypes.add(this);
  }

  @NotNull
  public String getName(T fact) {
    return myName;
  }

  @Nullable
  public T fromDfaValue(DfaValue value) {
    return value != null ? DfaFactMap.fromDfType(value.getDfType()).get(this) : null;
  }

  boolean isSuper(@Nullable T superFact, @Nullable T subFact) {
    return Objects.equals(superFact, subFact);
  }

  public boolean isUnknown(@NotNull T fact) {
    return false;
  }

  /**
   * Intersects two facts of this type.
   *
   * @param left left fact
   * @param right right fact
   * @return intersection fact or null if facts are incompatible
   */
  @Nullable
  T intersectFacts(@NotNull T left, @NotNull T right) {
    return left.equals(right) ? left : null;
  }

  /**
   * Unites two facts of this type.
   *
   * @param left left fact
   * @param right right fact
   * @return union fact (null means that the fact can have any value)
   */
  @Nullable
  T uniteFacts(@NotNull T left, @NotNull T right) {
    return left.equals(right) ? left : null;
  }

  /**
   * Produces a short suitable for debug output fact representation
   * @param fact a fact to represent
   * @return a string representation of the fact
   */
  @NotNull
  public String toString(@NotNull T fact) {
    return fact.toString();
  }

  static List<DfaFactType<?>> getTypes() {
    return Collections.unmodifiableList(ourFactTypes);
  }
}
