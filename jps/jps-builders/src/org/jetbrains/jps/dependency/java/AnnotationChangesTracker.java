// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.diff.Difference;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * This class defines an extension, allowing to customise dependency analysis in cases when JPS detects, that
 * annotations for certain program elements are changed. Currently, annotations on classes, fields and methods are tracked.
 * For methods, their parameter annotations are tracked too.
 * Every time changes in annotation set on mentioned program elements are detected, all registered extensions are queried, and
 * all requested dependencies are marked for recompilation.
 *
 * <p/>
 * To register custom AnnotationsChangeTracker, a JPS plugin must:
 * <ul>
 * <li> define a subclass of this class implementing desired callback methods
 * <li> register this implementation via standard java Service Provider Interface mechanism:
 *   <ul>
 *     <li>create a file 'META-INF/services/org.jetbrains.jps.dependency.java.AnnotationChangesTracker'
 *     <li>containing fully qualified name of extension implementation, e.g. "org.plugin-name.MyAnnotationsChangeTrackerImpl"
 *   </ul>
 * </ul>
 */
public interface AnnotationChangesTracker {

  /**
   * Enumeration defining all possible places that extension requests to recompile
   */
  enum Recompile {
    /**
     * If present in the returned result set, the usages of the annotated program element (class, field, method) will be affected.
     * it means that files where this program element is references, will be marked for recompilation
     */
    USAGES,

    /**
     * If present in the returned result set, the subclasses of the annotated class will be affected.
     * If returned for an annotated field/method, the subclasses of the class containing this field/method will be affected.
     */
    SUBCLASSES
  }

  /**
   * Utility object specifying extension's return result, when all possible dependencies on the item being examined, should be recompiled
   * See {@link AnnotationsChangeTracker.Recompile}
   */
  Set<Recompile> RECOMPILE_ALL = Collections.unmodifiableSet(EnumSet.allOf(Recompile.class));

  /**
   * Utility object specifying extension's return result, indicating that no additional files should be recompiled.
   * See {@link AnnotationsChangeTracker.Recompile}
   */
  Set<Recompile> RECOMPILE_NONE = Collections.unmodifiableSet(EnumSet.noneOf(Recompile.class));

  /**
   * @param annotationType the annotation class type to check. DependencyGraph will parse and store only those annotations,
   *                       if there exists at least one registered AnnotationTracker that can track annotations of this type
   * @return true if this AnnotationTracker can track annotations of this type, false otherwise.
   */
  boolean isAnnotationTracked(@NotNull TypeRepr.ClassType annotationType);
  
  /**
   * Invoked when changes in annotation list or parameter annotations for some method are detected
   * @param method the method in question
   * @param annotationsDiff differences descriptor for annotations on the method
   * @param paramAnnotationsDiff differences descriptor on method parameters annotations
   * @return a set of specifiers, determining what places in the program should be recompiled, see {@link org.jetbrains.jps.builders.java.dependencyView.AnnotationsChangeTracker.Recompile}
   */
  default @NotNull Set<Recompile> methodAnnotationsChanged(JvmMethod method, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationsDiff, Difference.Specifier<ParamAnnotation, ParamAnnotation.Diff> paramAnnotationsDiff) {
    return RECOMPILE_NONE;
  }

  /**
   * Invoked when changes in annotation list for some field are detected
   * @param field the field in question
   * @param annotationsDiff differences descriptor for annotations on the field
   * @return a set of specifiers, determining what places in the program should be recompiled, see {@link org.jetbrains.jps.builders.java.dependencyView.AnnotationsChangeTracker.Recompile}
   */
  default @NotNull Set<Recompile> fieldAnnotationsChanged(JvmField field, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationsDiff) {
    return RECOMPILE_NONE;
  }

  /**
   * Invoked when changes in annotation list for some class are detected
   * @param aClass the class in question
   * @param annotationsDiff differences descriptor for the class annotations
   * @return a set of specifiers, determining what places in the program should be recompiled, see {@link org.jetbrains.jps.builders.java.dependencyView.AnnotationsChangeTracker.Recompile}
   */
  default @NotNull Set<Recompile> classAnnotationsChanged(JvmClass aClass, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationsDiff) {
    return RECOMPILE_NONE;
  }
}

