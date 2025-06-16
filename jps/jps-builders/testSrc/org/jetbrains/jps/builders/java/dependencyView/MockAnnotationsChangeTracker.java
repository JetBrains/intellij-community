// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.util.Iterators;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class MockAnnotationsChangeTracker extends AnnotationsChangeTracker{
  private static final String ANOTATION_NAME = MockAnnotation.class.getName().replace('.', '/');
  private static final String HIERARCHY_ANOTATION_NAME = MockHierarchyAnnotation.class.getName().replace('.', '/');

  @Override
  @NotNull
  public Set<Recompile> methodAnnotationsChanged(NamingContext context, ProtoMethodEntity method, Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff, Difference.Specifier<ParamAnnotation, Difference> paramAnnotationsDiff) {
    //return RECOMPILE_NONE;
    return handleChanges(context, Iterators.flat(List.of(annotationsDiff.added(), annotationsDiff.removed(), Iterators.map(paramAnnotationsDiff.added(), pc -> pc.type), Iterators.map(paramAnnotationsDiff.removed(), pc -> pc.type))));
  }

  @Override
  @NotNull
  public Set<Recompile> fieldAnnotationsChanged(NamingContext context, ProtoFieldEntity field, Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff) {
    //return RECOMPILE_NONE;
    return handleChanges(context, Iterators.flat(annotationsDiff.added(), annotationsDiff.removed()));
  }

  @Override
  @NotNull
  public Set<Recompile> classAnnotationsChanged(NamingContext context, ClassRepr aClass, Difference.Specifier<TypeRepr.ClassType, Difference> annotationsDiff) {
    //return RECOMPILE_NONE;
    return handleChanges(context, Iterators.flat(annotationsDiff.added(), annotationsDiff.removed()));
  }

  @NotNull
  public Set<Recompile> handleChanges(NamingContext context, Iterable<TypeRepr.ClassType> changes) {
    final int annot = context.get(ANOTATION_NAME);
    final Set<Recompile> result = EnumSet.noneOf(Recompile.class);
    if (containsAnnotation(annot, changes)) {
      result.add(Recompile.USAGES);
    }
    final int hierarchyAnnot = context.get(HIERARCHY_ANOTATION_NAME);
    if (containsAnnotation(hierarchyAnnot, changes)) {
      result.add(Recompile.SUBCLASSES);
    }
    return result;
  }

  private static boolean containsAnnotation(int annotationName, Iterable<TypeRepr.ClassType> classes) {
    return !Iterators.isEmpty(Iterators.filter(classes, type -> annotationName == type.className));
  }
}
