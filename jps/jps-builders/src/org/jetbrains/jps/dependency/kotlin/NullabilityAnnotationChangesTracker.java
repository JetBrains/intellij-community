// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.kotlin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.java.*;
import org.jetbrains.jps.javac.Iterators;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class NullabilityAnnotationChangesTracker implements AnnotationChangesTracker {
  private static final Set<String> ourTrackedAnnotations = Set.of(
    "org/jetbrains/annotations/Nullable",
    "androidx/annotation/Nullable",
    "android/support/annotation/Nullable",
    "android/annotation/Nullable",
    "com/android/annotations/Nullable",
    "org/eclipse/jdt/annotation/Nullable",
    "org/checkerframework/checker/nullness/qual/Nullable",
    "javax/annotation/Nullable",
    "javax/annotation/CheckForNull",
    "edu/umd/cs/findbugs/annotations/CheckForNull",
    "edu/umd/cs/findbugs/annotations/Nullable",
    "edu/umd/cs/findbugs/annotations/PossiblyNull",
    "io/reactivex/annotations/Nullable",
    "io/reactivex/rxjava3/annotations/Nullable",

    "javax/annotation/Nonnull",
    "org/jetbrains/annotations/NotNull",
    "edu/umd/cs/findbugs/annotations/NonNull",
    "androidx/annotation/NonNull",
    "android/support/annotation/NonNull",
    "android/annotation/NonNull",
    "com/android/annotations/NonNull",
    "org/eclipse/jdt/annotation/NonNull",
    "org/checkerframework/checker/nullness/qual/NonNull",
    "lombok/NonNull",
    "io/reactivex/annotations/NonNull",
    "io/reactivex/rxjava3/annotations/NonNull"
  );

  @Override
  public @NotNull Set<Recompile> methodAnnotationsChanged(JvmMethod method, Difference.Specifier<TypeRepr.ClassType, ?> annotationsDiff, Difference.Specifier<ParamAnnotation, ?> paramAnnotationsDiff) {
    if (isAffected(Iterators.flat(List.of(annotationsDiff.added(), annotationsDiff.removed(), Iterators.map(Iterators.flat(paramAnnotationsDiff.added(), paramAnnotationsDiff.removed()), an -> an.type))))) {
      return method.isFinal()? EnumSet.of(Recompile.USAGES) : EnumSet.of(Recompile.USAGES, Recompile.SUBCLASSES);
    }
    return RECOMPILE_NONE;
  }

  @Override
  public @NotNull Set<Recompile> fieldAnnotationsChanged(JvmField field, Difference.Specifier<TypeRepr.ClassType, ?> annotationsDiff) {
    return isAffected(Iterators.flat(annotationsDiff.added(), annotationsDiff.removed()))? EnumSet.of(Recompile.USAGES) : RECOMPILE_NONE;
  }

  private static boolean isAffected(Iterable<TypeRepr.ClassType> addedOrRemoved) {
    return !Iterators.isEmpty(Iterators.filter(addedOrRemoved, t -> ourTrackedAnnotations.contains(t.getJvmName())));
  }
}
