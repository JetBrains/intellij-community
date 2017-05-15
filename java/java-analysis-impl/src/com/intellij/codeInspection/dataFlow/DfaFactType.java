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
import com.intellij.codeInspection.dataFlow.value.DfaOptionalValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.util.Key;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A type of the fact which restricts some value.
 *
 * @author Tagir Valeev
 */
public abstract class DfaFactType<T> extends Key<T> {
  /**
   * This fact is applied to the Optional values (like {@link java.util.Optional} or Guava Optional).
   * When its value is true, then optional is known to be present.
   * When its value is false, then optional is known to be empty (absent).
   */
  public static final DfaFactType<Boolean> OPTIONAL_PRESENCE = new DfaFactType<Boolean>("Optional presense") {
    @Override
    String toString(Boolean fact) {
      return fact ? "present Optional" : "absent Optional";
    }

    @Nullable
    @Override
    Boolean fromDfaValue(DfaValue value) {
      return value instanceof DfaOptionalValue ? ((DfaOptionalValue)value).isPresent() : null;
    }
  };

  /**
   * This fact is applied to the integral values (of types byte, char, short, int, long).
   * Its value represents a range of possible values.
   */
  public static final DfaFactType<LongRangeSet> RANGE = new DfaFactType<LongRangeSet>("Mutability") {
    @Override
    boolean isSuper(@NotNull LongRangeSet superFact, @NotNull LongRangeSet subFact) {
      return superFact.contains(subFact);
    }

    @Nullable
    @Override
    LongRangeSet fromDfaValue(DfaValue value) {
      if(value instanceof DfaVariableValue) {
        DfaVariableValue var = (DfaVariableValue)value;
        if(var.getQualifier() != null) {
          LongRangeSet specialRange =
            StreamEx.of(SpecialField.values()).findFirst(sf -> sf.isMyAccessor(var.getPsiVariable())).map(SpecialField::getRange)
              .orElse(null);
          if(specialRange != null) {
            return specialRange;
          }
        }
      }
      return LongRangeSet.fromDfaValue(value);
    }

    @Nullable
    @Override
    LongRangeSet intersectFacts(@NotNull LongRangeSet left, @NotNull LongRangeSet right) {
      LongRangeSet intersection = left.intersect(right);
      return intersection.isEmpty() ? null : intersection;
    }

    @Override
    String toString(LongRangeSet fact) {
      return fact.toString();
    }
  };

  private DfaFactType(String name) {
    super(name);
  }

  @Nullable
  T fromDfaValue(DfaValue value) {
    return null;
  }

  boolean isSuper(@NotNull T superFact, @NotNull T subFact) {
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

  String toString(T fact) {
    return fact.toString();
  }
}
