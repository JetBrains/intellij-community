// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.dependencyView.MockAnnotation;
import org.jetbrains.jps.builders.java.dependencyView.MockHierarchyAnnotation;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.javac.Iterators;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class MockAnnotationsChangeTracker implements AnnotationChangesTracker {
  private static final String ANOTATION_NAME = MockAnnotation.class.getName().replace('.', '/');
  private static final String HIERARCHY_ANOTATION_NAME = MockHierarchyAnnotation.class.getName().replace('.', '/');
  private static final Set<String> KOTLIN_TESTS_ANOTATION_NAMES = Set.of("foo/Ann", "Ann");

  @Override
  public @NotNull Set<Recompile> methodAnnotationsChanged(JvmMethod method, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationsDiff, Difference.Specifier<ParamAnnotation, ParamAnnotation.Diff> paramAnnotationsDiff) {
    //return RECOMPILE_NONE;
    return handleChanges(Iterators.map(Iterators.flat(List.of(annotationsDiff.added(), annotationsDiff.removed(), Iterators.map(annotationsDiff.changed(), Difference.Change::getPast), paramAnnotationsDiff.added(), paramAnnotationsDiff.removed(), Iterators.map(paramAnnotationsDiff.changed(), Difference.Change::getPast))), an -> an.getAnnotationClass()));
  }

  @Override
  public @NotNull Set<Recompile> fieldAnnotationsChanged(JvmField field, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationsDiff) {
    //return RECOMPILE_NONE;
    return handleChanges(Iterators.map(Iterators.flat(List.of(annotationsDiff.added(), annotationsDiff.removed(), Iterators.map(annotationsDiff.changed(), Difference.Change::getPast))), an -> an.getAnnotationClass()));
  }

  @Override
  public @NotNull Set<Recompile> classAnnotationsChanged(JvmClass aClass, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationsDiff) {
    //return RECOMPILE_NONE;
    return handleChanges(Iterators.map(Iterators.flat(List.of(annotationsDiff.added(), annotationsDiff.removed(), Iterators.map(annotationsDiff.changed(), Difference.Change::getPast))), an -> an.getAnnotationClass()));
  }

  @NotNull
  public Set<Recompile> handleChanges(Iterable<TypeRepr.ClassType> changes) {
    final Set<Recompile> result = EnumSet.noneOf(Recompile.class);
    if (containsAnnotation(ANOTATION_NAME, changes)) {
      result.add(Recompile.USAGES);
    }
    if (containsAnnotation(HIERARCHY_ANOTATION_NAME, changes)) {
      result.add(Recompile.SUBCLASSES);
    }
    if (containsAnnotation(KOTLIN_TESTS_ANOTATION_NAMES, changes)) {
      result.addAll(RECOMPILE_ALL);
    }
    return result;
  }

  private static boolean containsAnnotation(@NotNull String annotationName, Iterable<TypeRepr.ClassType> classes) {
    return containsAnnotation(Set.of(annotationName), classes);
  }
  private static boolean containsAnnotation(Set<String> annotationNames, Iterable<TypeRepr.ClassType> classes) {
    return !Iterators.isEmpty(Iterators.filter(classes, type -> annotationNames.contains(type.getJvmName())));
  }
}
