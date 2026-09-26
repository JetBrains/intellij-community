// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.test;

import org.jetbrains.jps.dependency.java.AnnotationGroup;
import org.jetbrains.jps.dependency.java.JvmDifferentiateStrategyImpl;
import org.jetbrains.jps.dependency.java.TypeRepr;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The main purpose of this class is to register the annotation tracker for mock and test annotations used in tests
 */
public class TestJvmDifferentiateStrategy extends JvmDifferentiateStrategyImpl {
  private static final boolean ENABLED = Boolean.getBoolean("jvm-inc-builder.test.track.mock.annotations");
  private static final String ANOTATION_NAME = "org/jetbrains/jps/dependency/test/MockAnnotation";
  private static final String HIERARCHY_ANOTATION_NAME = "org/jetbrains/jps/dependency/test/MockHierarchyAnnotation";

  private static final List<AnnotationGroup> ourTrackedAnnotations = List.of(
    AnnotationGroup.of(
      "Test annotation",
      EnumSet.allOf(AnnotationGroup.AnnTarget.class),
      EnumSet.allOf(AnnotationGroup.AffectionKind.class),
      EnumSet.of(AnnotationGroup.AffectionScope.usages),
      Set.of(
        new TypeRepr.ClassType(ANOTATION_NAME)
      )
    ),

    AnnotationGroup.of(
      "Test hierarchy annotation",
      EnumSet.allOf(AnnotationGroup.AnnTarget.class),
      EnumSet.allOf(AnnotationGroup.AffectionKind.class),
      EnumSet.of(AnnotationGroup.AffectionScope.subclasses),
      Set.of(
        new TypeRepr.ClassType(HIERARCHY_ANOTATION_NAME)
      )
    ),

    AnnotationGroup.of(
      "Kotlin test annotations",
      EnumSet.of(AnnotationGroup.AnnTarget.type, AnnotationGroup.AnnTarget.field, AnnotationGroup.AnnTarget.method),
      EnumSet.allOf(AnnotationGroup.AffectionKind.class),
      EnumSet.of(AnnotationGroup.AffectionScope.usages, AnnotationGroup.AffectionScope.subclasses),
      Set.of(
        new TypeRepr.ClassType("foo/Ann"),
        new TypeRepr.ClassType("Ann")
      )
    )
  );

  @Override
  protected Iterable<AnnotationGroup> getTrackedAnnotations() {
    return ENABLED? ourTrackedAnnotations : List.of();
  }
}

