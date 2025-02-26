// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.dependencyView.MockAnnotation;
import org.jetbrains.jps.builders.java.dependencyView.MockHierarchyAnnotation;
import org.jetbrains.jps.dependency.DifferentiateContext;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.javac.Iterators;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The main purpose of this class is to register the annotation tracker for mock and test annotations used in tests
 */
public class TestJvmDifferentiateStrategy extends JvmDifferentiateStrategyImpl {

  private static final String ANOTATION_NAME = MockAnnotation.class.getName().replace('.', '/');
  private static final String HIERARCHY_ANOTATION_NAME = MockHierarchyAnnotation.class.getName().replace('.', '/');
  private static final Set<String> KOTLIN_TESTS_ANOTATION_NAMES = Set.of("foo/Ann", "Ann");

  @Override
  public boolean isAnnotationTracked(@NotNull TypeRepr.ClassType annotationType) {
    String typeName = annotationType.getJvmName();
    return KOTLIN_TESTS_ANOTATION_NAMES.contains(typeName) || Objects.equals(ANOTATION_NAME, typeName) || Objects.equals(HIERARCHY_ANOTATION_NAME, typeName);
  }

  @Override
  public boolean processClassAnnotations(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationDiff, Utils future, Utils present) {
    Set<AnnotationAffectionScope> affectionScope = getAffectionScope(getAffectedAnnotations(annotationDiff, EnumSet.of(AnnotationAffectionKind.added, AnnotationAffectionKind.removed, AnnotationAffectionKind.changed)));
    if (!affectionScope.isEmpty()) {
      affectClassAnnotationUsages(context, affectionScope, change, future, present);
    }
    return super.processClassAnnotations(context, change, annotationDiff, future, present);
  }

  @Override
  public boolean processFieldAnnotations(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmField, JvmField.Diff> fieldChange, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationDiff, Utils future, Utils present) {
    Set<AnnotationAffectionScope> affectionScope = getAffectionScope(getAffectedAnnotations(annotationDiff, EnumSet.of(AnnotationAffectionKind.added, AnnotationAffectionKind.removed, AnnotationAffectionKind.changed)));
    if (!affectionScope.isEmpty()) {
      affectFieldAnnotationUsages(context, affectionScope, clsChange, fieldChange.getPast(), future, present);
    }
    return super.processFieldAnnotations(context, clsChange, fieldChange, annotationDiff, future, present);
  }

  @Override
  public boolean processMethodAnnotations(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmMethod, JvmMethod.Diff> methodChange, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationsDiff, Difference.Specifier<ParamAnnotation, ParamAnnotation.Diff> paramAnnotationsDiff, Utils future, Utils present) {
    EnumSet<AnnotationAffectionKind> affectionKinds = EnumSet.of(AnnotationAffectionKind.added, AnnotationAffectionKind.removed, AnnotationAffectionKind.changed);
    Set<AnnotationAffectionScope> affectionScope = getAffectionScope(
      Iterators.flat(getAffectedAnnotations(annotationsDiff, affectionKinds), getAffectedAnnotations(paramAnnotationsDiff, affectionKinds))
    );
    if (!affectionScope.isEmpty()) {
      affectMethodAnnotationUsages(context, affectionScope, clsChange, methodChange.getPast(), future, present);
    }
    return super.processMethodAnnotations(context, clsChange, methodChange, annotationsDiff, paramAnnotationsDiff, future, present);
  }

  private static Set<AnnotationAffectionScope> getAffectionScope(Iterable<TypeRepr.ClassType> trackedAnnotations) {
    Set<AnnotationAffectionScope> result = EnumSet.noneOf(AnnotationAffectionScope.class);
    for (TypeRepr.ClassType annotation : trackedAnnotations) {
      if (ANOTATION_NAME.equals(annotation.getJvmName())) {
        result.add(AnnotationAffectionScope.usages);
      }
      else if (HIERARCHY_ANOTATION_NAME.equals(annotation.getJvmName())) {
        result.add(AnnotationAffectionScope.subclasses);
      }
      else if (KOTLIN_TESTS_ANOTATION_NAMES.contains(annotation.getJvmName())) {
        result.addAll(EnumSet.of(AnnotationAffectionScope.usages, AnnotationAffectionScope.subclasses));
      }
    }
    return result;
  }
}
