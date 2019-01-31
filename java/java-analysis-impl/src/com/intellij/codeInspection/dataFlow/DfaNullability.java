// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a value nullability within DFA. Unlike {@link Nullability} may additionally hold a {@link #FLUSHED} value
 * which means that non-stable variable declared as Nullable was checked for nullity and flushed afterwards (e.g. by unknown method call),
 * so we are unsure about its nullability anymore.
 *
 * @see DfaFactType#NULLABILITY
 */
public enum DfaNullability {
  NULL("Null", "null", Nullability.NULLABLE),
  NULLABLE("Nullable", "nullable", Nullability.NULLABLE),
  NOT_NULL("Not-null", "non-null", Nullability.NOT_NULL),
  UNKNOWN("Unknown", "", Nullability.UNKNOWN),
  FLUSHED("Flushed", "", Nullability.UNKNOWN);

  private final @NotNull String myInternalName;
  private final @NotNull String myPresentationalName;
  private final @NotNull Nullability myNullability;

  DfaNullability(@NotNull String internalName, @NotNull String presentationalName, @NotNull Nullability nullability) {
    myInternalName = internalName;
    myPresentationalName = presentationalName;
    myNullability = nullability;
  }

  @NotNull
  public String getInternalName() {
    return myInternalName;
  }

  @NotNull
  public String getPresentationName() {
    return myPresentationalName;
  }

  public static boolean isNullable(DfaFactMap map) {
    return toNullability(map.get(DfaFactType.NULLABILITY)) == Nullability.NULLABLE;
  }

  public static boolean isNotNull(DfaFactMap map) {
    return map.get(DfaFactType.NULLABILITY) == NOT_NULL;
  }

  @NotNull
  public static Nullability toNullability(@Nullable DfaNullability dfaNullability) {
    return dfaNullability == null ? Nullability.UNKNOWN : dfaNullability.myNullability;
  }

  @NotNull
  public static DfaNullability fromNullability(@NotNull Nullability nullability) {
    switch (nullability) {
      case NOT_NULL:
        return NOT_NULL;
      case NULLABLE:
        return NULLABLE;
      case UNKNOWN:
        return UNKNOWN;
    }
    throw new IllegalStateException("Unknown nullability: "+nullability);
  }
}
