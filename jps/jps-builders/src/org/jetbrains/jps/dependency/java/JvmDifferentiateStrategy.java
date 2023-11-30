// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.DifferentiateContext;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.diff.Difference;

/**
 * This is a template differentiate strategy suitable for Jvm Nodes
 * @noinspection unused
 */
public interface JvmDifferentiateStrategy {

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

      JvmClass changedClass = change.getPast();

      Difference.Specifier<JvmMethod, JvmMethod.Diff> methodsDiff = change.getDiff().methods();
      if (!methodsDiff.unchanged()) {
        if (!processRemovedMethods(context, changedClass, methodsDiff.removed(), future, present)) {
          return false;
        }
        if (!processAddedMethods(context, changedClass, methodsDiff.added(), future, present)) {
          return false;
        }
        if (!processChangedMethods(context, changedClass, methodsDiff.changed(), future, present)) {
          return false;
        }
      }

      Difference.Specifier<JvmField, JvmField.Diff> fieldsDiff = change.getDiff().fields();
      if (!fieldsDiff.unchanged()) {
        if (!processRemovedFields(context, changedClass, fieldsDiff.removed(), future, present)) {
          return false;
        }
        if (!processAddedFields(context, changedClass, fieldsDiff.added(), future, present)) {
          return false;
        }
        if (!processChangedFields(context, changedClass, fieldsDiff.changed(), future, present)) {
          return false;
        }
      }
    }
    return true;
  }

  default boolean processChangedClass(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Utils future, Utils present) {
    return true;
  }

  default boolean processRemovedMethods(DifferentiateContext context, JvmClass changedClass, Iterable<JvmMethod> removed, Utils future, Utils present) {
    for (JvmMethod method : removed) {
      if (!processRemovedMethod(context, changedClass, method, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processRemovedMethod(DifferentiateContext context, JvmClass changedClass, JvmMethod removedMethod, Utils future, Utils present) {
    return true;
  }

  default boolean processAddedMethods(DifferentiateContext context, JvmClass changedClass, Iterable<JvmMethod> added, Utils future, Utils present) {
    for (JvmMethod method : added) {
      if (!processAddedMethod(context, changedClass, method, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processAddedMethod(DifferentiateContext context, JvmClass changedClass, JvmMethod addedMethod, Utils future, Utils present) {
    return true;
  }

  default boolean processChangedMethods(DifferentiateContext context, JvmClass changedClass, Iterable<Difference.Change<JvmMethod, JvmMethod.Diff>> changed, Utils future, Utils present) {
    for (Difference.Change<JvmMethod, JvmMethod.Diff> change : changed) {
      if (!processChangedMethod(context, changedClass, change, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processChangedMethod(DifferentiateContext context, JvmClass changedClass, Difference.Change<JvmMethod, JvmMethod.Diff> change, Utils future, Utils present) {
    return true;
  }

  default boolean processRemovedFields(DifferentiateContext context, JvmClass changedClass, Iterable<JvmField> removed, Utils future, Utils present) {
    for (JvmField field : removed) {
      if (!processRemovedField(context, changedClass, field, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processRemovedField(DifferentiateContext context, JvmClass changedClass, JvmField removedField, Utils future, Utils present) {
    return true;
  }

  default boolean processAddedFields(DifferentiateContext context, JvmClass changedClass, Iterable<JvmField> added, Utils future, Utils present) {
    for (JvmField field : added) {
      if (!processAddedField(context, changedClass, field, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processAddedField(DifferentiateContext context, JvmClass changedClass, JvmField addedField, Utils future, Utils present) {
    return true;
  }

  default boolean processChangedFields(DifferentiateContext context, JvmClass changedClass, Iterable<Difference.Change<JvmField, JvmField.Diff>> changed, Utils future, Utils present) {
    for (Difference.Change<JvmField, JvmField.Diff> change : changed) {
      if (!processChangedField(context, changedClass, change, future, present)) {
        return false;
      }
    }
    return true;
  }

  default boolean processChangedField(DifferentiateContext context, JvmClass changedClass, Difference.Change<JvmField, JvmField.Diff> change, Utils future, Utils present) {
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

}
