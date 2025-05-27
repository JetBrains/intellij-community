// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A class that represents nullability of a type, including the nullability itself, and the nullability source.
 */
@ApiStatus.Experimental
public final class TypeNullability {
  /**
   * Unknown nullability without the source 
   */
  public static final TypeNullability UNKNOWN = new TypeNullability(Nullability.UNKNOWN, NullabilitySource.Standard.NONE);
  /**
   * Mandated not-null nullability
   */
  public static final TypeNullability NOT_NULL_MANDATED = new TypeNullability(Nullability.NOT_NULL, NullabilitySource.Standard.MANDATED);
  /**
   * Mandated nullable nullability
   */
  public static final TypeNullability NULLABLE_MANDATED = new TypeNullability(Nullability.NULLABLE, NullabilitySource.Standard.MANDATED);
  
  private final @NotNull Nullability myNullability;
  private final @NotNull NullabilitySource mySource;

  public TypeNullability(@NotNull Nullability nullability, @NotNull NullabilitySource source) {
    myNullability = nullability;
    mySource = source;
    if (nullability != Nullability.UNKNOWN && source == NullabilitySource.Standard.NONE) {
      throw new IllegalArgumentException("Source must be specified for non-unknown nullability");
    }
  }

  /**
   * @return the nullability of the type
   */
  public @NotNull Nullability nullability() {
    return myNullability;
  }

  /**
   * @return the source of the nullability information
   */
  public @NotNull NullabilitySource source() {
    return mySource;
  }

  /**
   * @param collection type nullabilities to intersect
   * @return the intersection of the type nullabilities in the collection
   */
  public static @NotNull TypeNullability intersect(@NotNull Collection<@NotNull TypeNullability> collection) {
    Map<Nullability, Set<NullabilitySource>> map = collection.stream().collect(Collectors.groupingBy(
      TypeNullability::nullability, Collectors.mapping(TypeNullability::source, Collectors.toSet())));
    Set<NullabilitySource> sources = map.get(Nullability.NOT_NULL);
    if (sources != null) {
      return new TypeNullability(Nullability.NOT_NULL, NullabilitySource.multiSource(sources));
    }
    sources = map.get(Nullability.NULLABLE);
    if (sources != null) {
      return new TypeNullability(Nullability.NULLABLE, NullabilitySource.multiSource(sources));
    }
    sources = map.get(Nullability.UNKNOWN);
    if (sources != null) {
      return new TypeNullability(Nullability.UNKNOWN, NullabilitySource.multiSource(sources));
    }
    return UNKNOWN;
  }
  
  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    TypeNullability that = (TypeNullability)o;
    return myNullability == that.myNullability && mySource.equals(that.mySource);
  }

  @Override
  public int hashCode() {
    int result = myNullability.hashCode();
    result = 31 * result + mySource.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return myNullability + " (" + mySource + ")";
  }
}
