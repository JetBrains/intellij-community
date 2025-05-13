// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import java.util.Set;

public class AnnotationGroup {
  public enum AffectionKind {
    added, removed, changed
  }

  public enum AnnTarget {
    type, field, method, method_parameter
  }

  public enum AffectionScope {
    /**
     * If present in the returned result set, the usages of the annotated program element (class, field, method) will be affected.
     * it means that files where this program element is references, will be marked for recompilation
     */
    usages,

    /**
     * If present in the returned result set, the subclasses of the annotated class will be affected.
     * If returned for an annotated field/method, the subclasses of the class containing this field/method will be affected.
     */
    subclasses
  }

  public final String name;
  public final Set<AffectionKind> affectionKind;
  public final Set<AffectionScope> affectionScope;
  public final Set<AnnTarget> targets;
  public final Set<TypeRepr.ClassType> types;

  private AnnotationGroup(String name, Set<AffectionKind> affectionKind, Set<AffectionScope> affectionScope, Set<AnnTarget> targets, Set<TypeRepr.ClassType> types) {
    this.name = name;
    this.affectionKind = affectionKind;
    this.affectionScope = affectionScope;
    this.targets = targets;
    this.types = types;
  }

  public static AnnotationGroup of(String name, Set<AnnTarget> targets, Set<AffectionKind> affectionKind, Set<AffectionScope> affectionScope, Set<TypeRepr.ClassType> annotationTypes) {
    return new AnnotationGroup(name, affectionKind, affectionScope, targets, annotationTypes);
  }
}
