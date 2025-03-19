// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.dependency.DifferentiateContext;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.diff.Difference;

/**
 * This is a template differentiate strategy suitable for Jvm Nodes
 * @noinspection unused
 */
public interface JvmDifferentiateStrategy {

  /**
   * @param annotationType the annotation class type to check. DependencyGraph will parse and store only those annotations,
   *                       if there exists at least one registered AnnotationTracker that can track annotations of this type
   * @return true if this AnnotationTracker can track annotations of this type, false otherwise.
   */
  default boolean isAnnotationTracked(@NotNull TypeRepr.ClassType annotationType) {
    return false;
  }

  default boolean isIncremental(DifferentiateContext context, Node<?, ?> affectedNode) {
    return true;
  }

  default boolean processRemovedClasses(DifferentiateContext context, Iterable<JvmClass> removedClasses, Utils future, Utils present) {
    for (JvmClass aClass : removedClasses) {
      if (!processRemovedClass(context, aClass, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processRemovedClass(DifferentiateContext context, JvmClass removedClass, Utils future, Utils present) {
    return true;
  }

  default boolean processAddedClasses(DifferentiateContext context, Iterable<JvmClass> addedClasses, Utils future, Utils present) {
    for (JvmClass aClass : addedClasses) {
      if (!processAddedClass(context, aClass, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processAddedClass(DifferentiateContext context, JvmClass addedClass, Utils future, Utils present) {
    return true;
  }

  default boolean processChangedClasses(DifferentiateContext context, Iterable<Difference.Change<JvmClass, JvmClass.Diff>> changed, Utils future, Utils present) {
    for (Difference.Change<JvmClass, JvmClass.Diff> change : changed) {
      if (!processChangedClass(context, change, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processChangedClass(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Utils future, Utils present) {
    Difference.Specifier<JvmMethod, JvmMethod.Diff> methodsDiff = change.getDiff().methods();
    if (!methodsDiff.unchanged()) {
      if (!processRemovedMethods(context, change, methodsDiff.removed(), future, present)) {
        return false;
      }
      if (!processAddedMethods(context, change, methodsDiff.added(), future, present)) {
        return false;
      }
      if (!processChangedMethods(context, change, methodsDiff.changed(), future, present)) {
        return false;
      }
    }

    Difference.Specifier<JvmField, JvmField.Diff> fieldsDiff = change.getDiff().fields();
    if (!fieldsDiff.unchanged()) {
      if (!processRemovedFields(context, change, fieldsDiff.removed(), future, present)) {
        return false;
      }
      if (!processAddedFields(context, change, fieldsDiff.added(), future, present)) {
        return false;
      }
      if (!processChangedFields(context, change, fieldsDiff.changed(), future, present)) {
        return false;
      }
    }

    Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationDiff = change.getDiff().annotations();
    if (!annotationDiff.unchanged()) {
      if (!processClassAnnotations(context, change, annotationDiff, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processClassAnnotations(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationDiff, Utils future, Utils present) {
    return true;
  }

  default boolean processRemovedMethods(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Iterable<JvmMethod> removed, Utils future, Utils present) {
    for (JvmMethod method : removed) {
      if (!processRemovedMethod(context, change, method, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processRemovedMethod(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, JvmMethod removedMethod, Utils future, Utils present) {
    return true;
  }

  default boolean processAddedMethods(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Iterable<JvmMethod> added, Utils future, Utils present) {
    for (JvmMethod method : added) {
      if (!processAddedMethod(context, change, method, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processAddedMethod(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, JvmMethod addedMethod, Utils future, Utils present) {
    return true;
  }

  default boolean processChangedMethods(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Iterable<Difference.Change<JvmMethod, JvmMethod.Diff>> methodChanges, Utils future, Utils present) {
    for (Difference.Change<JvmMethod, JvmMethod.Diff> methodChange : methodChanges) {
      if (!processChangedMethod(context, change, methodChange, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processChangedMethod(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmMethod, JvmMethod.Diff> methodChange, Utils future, Utils present) {
    JvmMethod.Diff diff = methodChange.getDiff();
    Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationsDiff = diff.annotations();
    Difference.Specifier<ParamAnnotation, ParamAnnotation.Diff> paramAnnotationsDiff = diff.paramAnnotations();
    if (!annotationsDiff.unchanged() || !paramAnnotationsDiff.unchanged()) {
      if (!processMethodAnnotations(context, clsChange, methodChange, annotationsDiff, paramAnnotationsDiff, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processMethodAnnotations(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmMethod, JvmMethod.Diff> methodChange, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationsDiff, Difference.Specifier<ParamAnnotation, ParamAnnotation.Diff> paramAnnotationsDiff, Utils future, Utils present){
    return true;
  }

  default boolean processRemovedFields(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Iterable<JvmField> removed, Utils future, Utils present) {
    for (JvmField field : removed) {
      if (!processRemovedField(context, change, field, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processRemovedField(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, JvmField removedField, Utils future, Utils present) {
    return true;
  }

  default boolean processAddedFields(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Iterable<JvmField> added, Utils future, Utils present) {
    for (JvmField field : added) {
      if (!processAddedField(context, change, field, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processAddedField(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, JvmField addedField, Utils future, Utils present) {
    return true;
  }

  default boolean processChangedFields(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Iterable<Difference.Change<JvmField, JvmField.Diff>> fieldChanges, Utils future, Utils present) {
    for (Difference.Change<JvmField, JvmField.Diff> fieldChange : fieldChanges) {
      if (!processChangedField(context, clsChange, fieldChange, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processChangedField(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmField, JvmField.Diff> fieldChange, Utils future, Utils present) {
    Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationDiff = fieldChange.getDiff().annotations();
    if (!annotationDiff.unchanged()) {
      if (!processFieldAnnotations(context, clsChange, fieldChange, annotationDiff, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processFieldAnnotations(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmField, JvmField.Diff> fieldChange, Difference.Specifier<ElementAnnotation, ElementAnnotation.Diff> annotationDiff, Utils future, Utils present) {
    return true;
  }

  default boolean processRemovedModules(DifferentiateContext context, Iterable<JvmModule> removed, Utils future, Utils present) {
    for (JvmModule module : removed) {
      if (!processRemovedModule(context, module, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processRemovedModule(DifferentiateContext context, JvmModule removedModule, Utils future, Utils present) {
    return true;
  }

  default boolean processAddedModules(DifferentiateContext context, Iterable<JvmModule> added, Utils future, Utils present) {
    for (JvmModule module : added) {
      if (!processAddedModule(context, module, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processAddedModule(DifferentiateContext context, JvmModule addedModule, Utils future, Utils present) {
    return true;
  }

  default boolean processChangedModules(DifferentiateContext context, Iterable<Difference.Change<JvmModule, JvmModule.Diff>> changed, Utils future, Utils present) {
    for (Difference.Change<JvmModule, JvmModule.Diff> change : changed) {
      if (!processChangedModule(context, change, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processChangedModule(DifferentiateContext context, Difference.Change<JvmModule, JvmModule.Diff> change, Utils future, Utils present) {
    return true;
  }

  default boolean processNodesWithErrors(DifferentiateContext context, Iterable<JVMClassNode<?, ?>> nodes, Utils present) {
    return true;
  }
}
