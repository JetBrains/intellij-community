// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.jps.dependency.DifferentiateContext;
import org.jetbrains.jps.dependency.DifferentiateStrategy;
import org.jetbrains.jps.dependency.Graph;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.javac.Iterators;

/**
 * This is a template differentiate strategy suitable for Jvm Nodes
 * @noinspection unused
 */
public abstract class GeneralDifferentiateStrategy implements DifferentiateStrategy {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.dependency.java.GeneralDifferentiateStrategy");
  
  @Override
  public final boolean differentiate(DifferentiateContext context, Iterable<Node<?, ?>> nodesBefore, Iterable<Node<?, ?>> nodesAfter) {
    Utils future = new Utils(context.getGraph(), context.getDelta());
    Utils present = new Utils(context.getGraph(), null);

    Difference.Specifier<JvmClass, JvmClass.Diff> classesDiff = Difference.deepDiff(
      Graph.getNodesOfType(nodesBefore, JvmClass.class), Graph.getNodesOfType(nodesAfter, JvmClass.class)
    );

    if (!classesDiff.unchanged()) {
      if (!processRemovedClasses(context, classesDiff.removed(), future, present)) {
        return false;
      }
      if (!processAddedClasses(context, classesDiff.added(), future, present)) {
        return false;
      }

      Iterable<Difference.Change<JvmClass, JvmClass.Diff>> changedClasses = classesDiff.changed();
      if (!processChangedClasses(context, changedClasses, future, present)) {
        return false;
      }
    }

    Difference.Specifier<JvmModule, JvmModule.Diff> modulesDiff = Difference.deepDiff(
      Graph.getNodesOfType(nodesBefore, JvmModule.class), Graph.getNodesOfType(nodesAfter, JvmModule.class)
    );

    if (!modulesDiff.unchanged()) {
      if (!processRemovedModules(context, modulesDiff.removed(), future, present)) {
        return false;
      }
      if (!processAddedModules(context, modulesDiff.added(), future, present)) {
        return false;
      }
      if (!processChangedModules(context, modulesDiff.changed(), future, present)) {
        return false;
      }
    }

    return true;
  }

  protected boolean processRemovedClasses(DifferentiateContext context, Iterable<JvmClass> removedClasses, Utils future, Utils present) {
    if (!Iterators.isEmpty(removedClasses)) {
      debug("Processing removed classes:");
      try {
        for (JvmClass aClass : removedClasses) {
          if (!processRemovedClass(context, aClass, future, present)) {
            return false;
          }
        }
      }
      finally {
        debug("End of removed classes processing.");
      }
    }
    return true;
  }

  protected boolean processRemovedClass(DifferentiateContext context, JvmClass removedClass, Utils future, Utils present) {
    return true;
  }

  protected boolean processAddedClasses(DifferentiateContext context, Iterable<JvmClass> addedClasses, Utils future, Utils present) {
    if (!Iterators.isEmpty(addedClasses)) {
      debug("Processing added classes:");
      try {
        for (JvmClass aClass : addedClasses) {
          if (!processAddedClass(context, aClass, future, present)) {
            return false;
          }
        }
      }
      finally {
        debug("End of added classes processing.");
      }
    }
    return true;
  }

  protected boolean processAddedClass(DifferentiateContext context, JvmClass addedClass, Utils future, Utils present) {
    return true;
  }

  protected boolean processChangedClasses(DifferentiateContext context, Iterable<Difference.Change<JvmClass, JvmClass.Diff>> changed, Utils future, Utils present) {
    if (!Iterators.isEmpty(changed)) {
      debug("Processing changed classes:");

      try {
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
      }
      finally {
        debug("End of changed classes processing.");
      }
    }
    return true;
  }

  protected boolean processChangedClass(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Utils future, Utils present) {
    return true;
  }

  protected boolean processRemovedMethods(DifferentiateContext context, JvmClass changedClass, Iterable<JvmMethod> removed, Utils future, Utils present) {
    if (!Iterators.isEmpty(removed)) {
      debug("Processing removed methods:");
      try {
        for (JvmMethod method : removed) {
          if (!processRemovedMethod(context, changedClass, method, future, present)) {
            return false;
          }
        }
      }
      finally {
        debug("End of removed methods processing.");
      }
    }
    return true;
  }

  protected boolean processRemovedMethod(DifferentiateContext context, JvmClass changedClass, JvmMethod removedMethod, Utils future, Utils present) {
    return true;
  }

  protected boolean processAddedMethods(DifferentiateContext context, JvmClass changedClass, Iterable<JvmMethod> added, Utils future, Utils present) {
    if (!Iterators.isEmpty(added)) {
      debug("Processing added methods:");
      try {
        for (JvmMethod method : added) {
          if (!processAddedMethod(context, changedClass, method, future, present)) {
            return false;
          }
        }
      }
      finally {
        debug("End of added methods processing.");
      }
    }
    return true;
  }

  protected boolean processAddedMethod(DifferentiateContext context, JvmClass changedClass, JvmMethod addedMethod, Utils future, Utils present) {
    return true;
  }

  protected boolean processChangedMethods(DifferentiateContext context, JvmClass changedClass, Iterable<Difference.Change<JvmMethod, JvmMethod.Diff>> changed, Utils future, Utils present) {
    if (!Iterators.isEmpty(changed)) {
      debug("Processing changed methods:");
      try {
        for (Difference.Change<JvmMethod, JvmMethod.Diff> change : changed) {
          if (!processChangedMethod(context, changedClass, change, future, present)) {
            return false;
          }
        }
      }
      finally {
        debug("End of changed methods processing.");
      }
    }
    return true;
  }

  protected boolean processChangedMethod(DifferentiateContext context, JvmClass changedClass, Difference.Change<JvmMethod, JvmMethod.Diff> change, Utils future, Utils present) {
    return true;
  }

  protected boolean processRemovedFields(DifferentiateContext context, JvmClass changedClass, Iterable<JvmField> removed, Utils future, Utils present) {
    if (!Iterators.isEmpty(removed)) {
      debug("Processing removed fields:");
      try {
        for (JvmField field : removed) {
          if (!processRemovedField(context, changedClass, field, future, present)) {
            return false;
          }
        }
      }
      finally {
        debug("End of removed fields processing.");
      }
    }
    return true;
  }

  protected boolean processRemovedField(DifferentiateContext context, JvmClass changedClass, JvmField removedField, Utils future, Utils present) {
    return true;
  }

  protected boolean processAddedFields(DifferentiateContext context, JvmClass changedClass, Iterable<JvmField> added, Utils future, Utils present) {
    if (!Iterators.isEmpty(added)) {
      debug("Processing added fields:");
      try {
        for (JvmField field : added) {
          if (!processAddedField(context, changedClass, field, future, present)) {
            return false;
          }
        }
      }
      finally {
        debug("End of added fields processing.");
      }
    }
    return true;
  }

  protected boolean processAddedField(DifferentiateContext context, JvmClass changedClass, JvmField addedField, Utils future, Utils present) {
    return true;
  }

  protected boolean processChangedFields(DifferentiateContext context, JvmClass changedClass, Iterable<Difference.Change<JvmField, JvmField.Diff>> changed, Utils future, Utils present) {
    if (!Iterators.isEmpty(changed)) {
      debug("Processing changed fields:");
      try {
        for (Difference.Change<JvmField, JvmField.Diff> change : changed) {
          if (!processChangedField(context, changedClass, change, future, present)) {
            return false;
          }
        }
      }
      finally {
        debug("End of changed fields processing.");
      }
    }
    return true;
  }

  protected boolean processChangedField(DifferentiateContext context, JvmClass changedClass, Difference.Change<JvmField, JvmField.Diff> change, Utils future, Utils present) {
    return true;
  }

  protected boolean processRemovedModules(DifferentiateContext context, Iterable<JvmModule> removed, Utils future, Utils present) {
    if (!Iterators.isEmpty(removed)) {
      debug("Processing removed modules:");
      try {
        for (JvmModule module : removed) {
          if (!processRemovedModule(context, module, future, present)) {
            return false;
          }
        }
      }
      finally {
        debug("End of removed modules processing.");
      }
    }
    return true;
  }

  protected boolean processRemovedModule(DifferentiateContext context, JvmModule removedModule, Utils future, Utils present) {
    return true;
  }

  protected boolean processAddedModules(DifferentiateContext context, Iterable<JvmModule> added, Utils future, Utils present) {
    if (!Iterators.isEmpty(added)) {
      debug("Processing added modules:");
      try {
        for (JvmModule module : added) {
          if (!processAddedModule(context, module, future, present)) {
            return false;
          }
        }
      }
      finally {
        debug("End of added modules processing.");
      }
    }
    return true;
  }

  protected boolean processAddedModule(DifferentiateContext context, JvmModule addedModule, Utils future, Utils present) {
    return true;
  }

  protected boolean processChangedModules(DifferentiateContext context, Iterable<Difference.Change<JvmModule, JvmModule.Diff>> changed, Utils future, Utils present) {
    if (!Iterators.isEmpty(changed)) {
      debug("Processing changed modules:");
      try {
        for (Difference.Change<JvmModule, JvmModule.Diff> change : changed) {
          if (!processChangedModule(context, change, future, present)) {
            return false;
          }
        }
      }
      finally {
        debug("End of changed modules processing.");
      }
    }
    return true;
  }

  protected boolean processChangedModule(DifferentiateContext context, Difference.Change<JvmModule, JvmModule.Diff> change, Utils future, Utils present) {
    return true;
  }

  protected boolean isDebugEnabled() {
    return LOG.isDebugEnabled();
  }

  protected void debug(String message, Object... details) {
    if (isDebugEnabled()) {
      StringBuilder msg = new StringBuilder(message);
      for (Object detail : details) {
        msg.append(detail);
      }
      debug(msg.toString());
    }
  }

  protected void debug(String message) {
    LOG.debug(message);
  }
}
