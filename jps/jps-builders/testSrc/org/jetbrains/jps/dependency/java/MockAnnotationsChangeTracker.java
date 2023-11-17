// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.java.dependencyView.MockAnnotation;
import org.jetbrains.jps.builders.java.dependencyView.MockHierarchyAnnotation;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.javac.Iterators;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class MockAnnotationsChangeTracker extends AnnotationChangesTracker {
  private static final String ANOTATION_NAME = MockAnnotation.class.getName().replace('.', '/');
  private static final String HIERARCHY_ANOTATION_NAME = MockHierarchyAnnotation.class.getName().replace('.', '/');

  @Override
  public @NotNull Set<Recompile> methodAnnotationsChanged(JvmMethod method, Difference.Specifier<TypeRepr.ClassType, ?> annotationsDiff, Difference.Specifier<ParamAnnotation, ?> paramAnnotationsDiff) {
    //return RECOMPILE_NONE;
    return handleChanges(Iterators.flat(List.of(annotationsDiff.added(), annotationsDiff.removed(), Iterators.map(paramAnnotationsDiff.added(), pc -> pc.type), Iterators.map(paramAnnotationsDiff.removed(), pc -> pc.type))));
  }

  @Override
  public @NotNull Set<Recompile> fieldAnnotationsChanged(JvmField field, Difference.Specifier<TypeRepr.ClassType, ?> annotationsDiff) {
    //return RECOMPILE_NONE;
    return handleChanges(Iterators.flat(annotationsDiff.added(), annotationsDiff.removed()));
  }

  @Override
  public @NotNull Set<Recompile> classAnnotationsChanged(JvmClass aClass, Difference.Specifier<TypeRepr.ClassType, ?> annotationsDiff) {
    //return RECOMPILE_NONE;
    return handleChanges(Iterators.flat(annotationsDiff.added(), annotationsDiff.removed()));
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
    return result;
  }

  private static boolean containsAnnotation(String annotationName, Iterable<TypeRepr.ClassType> classes) {
    return !Iterators.isEmpty(Iterators.filter(classes, type -> Objects.equals(annotationName, type.getJvmName()) ));
  }
}
