// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.javac.Iterators;

import java.lang.annotation.RetentionPolicy;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

public final class JavaDifferentiateStrategy implements DifferentiateStrategy {

  @Override
  public boolean isIncremental(DifferentiateContext context, Node<?, ?> affectedNode) {
    if (affectedNode instanceof JvmClass && ((JvmClass)affectedNode).getFlags().isGenerated()) {
      // If among affected files are annotation processor-generated, then we might need to re-generate them.
      // To achieve this, we need to recompile the whole chunk which will cause processors to re-generate these affected files
      debug("Turning non-incremental for the BuildTarget because dependent class is annotation-processor generated: " + affectedNode.getReferenceID());
      return false;
    }
    return true;
  }

  @Override
  public boolean differentiate(DifferentiateContext context, Iterable<Node<?, ?>> nodesBefore, Iterable<Node<?, ?>> nodesAfter) {
    Utils future = new Utils(context.getGraph(), context.getDelta());
    Utils present = new Utils(context.getGraph(), null);

    Difference.Specifier<JvmClass, JvmClass.Diff> classesDiff = Difference.deepDiff(
      Graph.getNodesOfType(nodesBefore, JvmClass.class), Graph.getNodesOfType(nodesAfter, JvmClass.class)
    );
    
    for (JvmClass removed : classesDiff.removed()) {
      if (!processRemovedClass(context, removed, future, present)) {
        return false;
      }
    }

    for (JvmClass added : classesDiff.added()) {
      if (!processAddedClass(context, added, future, present)) {
        return false;
      }
    }

    for (Difference.Change<JvmClass, JvmClass.Diff> change : classesDiff.changed()) {
      if (!processChangedClass(context, change, future, present)) {
        return false;
      }
    }

    Difference.Specifier<JvmModule, JvmModule.Diff> modulesDiff = Difference.deepDiff(
      Graph.getNodesOfType(nodesBefore, JvmModule.class), Graph.getNodesOfType(nodesAfter, JvmModule.class)
    );

    for (JvmModule removed : modulesDiff.removed()) {
      if (!processRemovedModule(context, removed, future, present)) {
        return false;
      }
    }

    for (JvmModule added : modulesDiff.added()) {
      if (!processAddedModule(context, added, future, present)) {
        return false;
      }
    }

    for (Difference.Change<JvmModule, JvmModule.Diff> change : modulesDiff.changed()) {
      if (!processChangedModule(context, change, future, present)) {
        return false;
      }
    }

    return true;
  }

  public boolean processRemovedClass(DifferentiateContext context, JvmClass removedClass, Utils future, Utils present) {
    return true;
  }

  public boolean processAddedClass(DifferentiateContext context, JvmClass addedClass, Utils future, Utils present) {
    return true;
  }

  private boolean processChangedClass(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    JvmClass.Diff classDiff = change.getDiff();

    if (classDiff.superClassChanged() || classDiff.signatureChanged() || !classDiff.interfaces().unchanged()) {
      boolean extendsChanged = classDiff.superClassChanged() && !classDiff.extendsAdded();
      boolean affectUsages = classDiff.signatureChanged() || extendsChanged || !Iterators.isEmpty(classDiff.interfaces().removed());
      affectSubclasses(context, future, change.getNow().getReferenceID(), affectUsages);

      if (extendsChanged) {
        TypeRepr.ClassType exClass = new TypeRepr.ClassType(changedClass.getName());
        for (JvmClass depClass : Iterators.flat(Iterators.map(context.getGraph().getDependingNodes(changedClass.getReferenceID()), dep -> present.getNodes(dep, JvmClass.class)))) {
          for (JvmMethod method : depClass.getMethods()) {
            if (method.getExceptions().contains(exClass)) {
              context.affectUsage(method.createUsage(depClass.getName()));
              debug("Affecting usages of methods throwing " + exClass.getJvmName() + " exception; class " + depClass.getName());
            }
          }
        }
      }

      if (!changedClass.isAnonymous()) {
        Set<JvmNodeReferenceID> parents = Iterators.collect(present.allSupertypes(changedClass.getReferenceID()), new HashSet<>());
        parents.removeAll(Iterators.collect(future.allSupertypes(changedClass.getReferenceID()), new HashSet<>()));
        for (JvmNodeReferenceID parent : parents) {
          debug("Affecting usages in generic type parameter bounds of class: " + parent);
          context.affectUsage(new ClassAsGenericBoundUsage(parent.getNodeName())); // todo: need support of usage constraint?
        }
      }
    }

    JVMFlags addedFlags = classDiff.getAddedFlags();

    if (addedFlags.isInterface() || classDiff.getRemovedFlags().isInterface()) {
      debug("Class-to-interface or interface-to-class conversion detected, added class usage to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getName()));
    }

    if (changedClass.isAnnotation() && changedClass.getRetentionPolicy() == RetentionPolicy.SOURCE) {
      debug("Annotation, retention policy = SOURCE => a switch to non-incremental mode requested");
      return false;
    }

    if (addedFlags.isProtected()) {
      debug("Introduction of 'protected' modifier detected, adding class usage + inheritance constraint to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getName()), new InheritanceConstraint(future, changedClass));
    }

    if (!changedClass.getFlags().isPackageLocal() && change.getNow().getFlags().isPackageLocal()) {
      debug("Introduction of 'package-private' access detected, adding class usage + package constraint to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getName()), new PackageConstraint(changedClass.getPackageName()));
    }

    if (addedFlags.isFinal() || addedFlags.isPrivate()) {
      debug("Introduction of 'private' or 'final' modifier(s) detected, adding class usage to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getName()));
    }

    if (addedFlags.isAbstract() || addedFlags.isStatic()) {
      debug("Introduction of 'abstract' or 'static' modifier(s) detected, adding class new usage to affected usages");
      context.affectUsage(new ClassNewUsage(changedClass.getName()));
    }

    if (!changedClass.isAnonymous() && !changedClass.isPrivate() && classDiff.flagsChanged() && changedClass.isInnerClass()) {
      debug("Some modifiers (access flags) were changed for non-private inner class, adding class usage to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getName()));
    }

    if (changedClass.isAnnotation()) {
      debug("Class is annotation, performing annotation-specific analysis");

      if (classDiff.retentionPolicyChanged()) {
        debug("Retention policy change detected, adding class usage to affected usages");
        context.affectUsage(new ClassUsage(changedClass.getName()));
      }
      else if (classDiff.targetAttributeCategoryMightChange()) {
        debug("Annotation's attribute category in bytecode might be affected because of TYPE_USE or RECORD_COMPONENT target, adding class usage to affected usages");
        context.affectUsage(new ClassUsage(changedClass.getName()));
      }
      else {
        Difference.Specifier<ElemType, ?> targetsDiff = classDiff.annotationTargets();
        Set<ElemType> removedTargets = Iterators.collect(targetsDiff.removed(), EnumSet.noneOf(ElemType.class));

        if (removedTargets.contains(ElemType.LOCAL_VARIABLE)) {
          debug("Removed target contains LOCAL_VARIABLE => a switch to non-incremental mode requested");
          if (!present.incrementalDecision(context, changedClass, null)) {
            debug("End of Differentiate, returning false");
            return false;
          }
        }

        if (!removedTargets.isEmpty()) {
          debug("Removed some annotation targets, adding annotation query");
          TypeRepr.ClassType classType = new TypeRepr.ClassType(changedClass.getName());
          context.affectUsage((node, usage) -> {
            if (usage instanceof AnnotationUsage) {
              AnnotationUsage annotUsage = (AnnotationUsage)usage;
              if (classType.equals(annotUsage.getClassType())) {
                for (ElemType target : annotUsage.getTargets()) {
                  if (removedTargets.contains(target)) {
                    return true;
                  }
                }
              }
            }
            return false;
          });
        }

        for (JvmMethod m : classDiff.methods().added()) {
          if (m.getValue() == null) {
            debug("Added method with no default value: " + m.getName());
            debug("Adding class usage to affected usages");
            context.affectUsage(new ClassUsage(changedClass.getName()));
            break;
          }
        }
      }
      debug("End of annotation-specific analysis");
    }

    if (!processMethodChanges(context, change, future, present)) {
      return false;
    }

    if (!processFieldChanges(context, change, future, present)) {
      return false;
    }

    return true;
  }

  private boolean processMethodChanges(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> classChange, Utils future, Utils present) {
    JvmClass changedClass = classChange.getPast();
    Difference.Specifier<JvmMethod, JvmMethod.Diff> methodsDiff = classChange.getDiff().methods();

    if (changedClass.isAnnotation()) {
      debug("Class is annotation, skipping method analysis");
      return true;
    }
    
    debug("Processing added methods: ");
    for (JvmMethod addedMethod : methodsDiff.added()) {
      if (!addedMethod.isPrivate() && (changedClass.isInterface() || changedClass.isAbstract() || addedMethod.isAbstract())) {
        debug("Method: " + addedMethod.getName());
        debug("Class is abstract, or is interface, or added non-private method is abstract => affecting all subclasses");
        affectSubclasses(context, future, changedClass.getReferenceID(), false);
        break;
      }
    }

    if (changedClass.isInterface()) {
      for (JvmMethod addedMethod : methodsDiff.added()) {
        if (!addedMethod.isPrivate() && addedMethod.isAbstract()) {
          debug("Added non-private abstract method: " + addedMethod.getName());
          affectLambdaInstantiations(context, present, changedClass.getReferenceID());
        }
      }
    }

    for (JvmMethod removedMethod : methodsDiff.removed()) {

    }

    for (Difference.Change<JvmMethod, JvmMethod.Diff> methodChange : methodsDiff.changed()) {
      JvmMethod changedMethod = methodChange.getPast();
    }
    return true;
  }

  private boolean processFieldChanges(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> classChange, Utils future, Utils present) {
    JvmClass changedClass = classChange.getPast();
    Difference.Specifier<JvmField, JvmField.Diff> fields = classChange.getDiff().fields();
    return true;
  }

  private boolean processRemovedModule(DifferentiateContext context, JvmModule removedModule, Utils future, Utils present) {
    return true;
  }

  private boolean processAddedModule(DifferentiateContext context, JvmModule addedModule, Utils future, Utils present) {
    return true;
  }

  private boolean processChangedModule(DifferentiateContext context, Difference.Change<JvmModule, JvmModule.Diff> change, Utils future, Utils present) {
    JvmModule.Diff moduleDiff = change.getDiff();
    return true;
  }

  private void affectSubclasses(DifferentiateContext context, Utils utils, ReferenceID fromClass, boolean affectUsages) {
    debug("Affecting subclasses of class: " + fromClass + "; with usages affection: " + affectUsages);
    
    Graph graph = context.getGraph();
    for (ReferenceID cl : utils.withAllSubclasses(fromClass)) {
      for (NodeSource source : graph.getSources(cl)) {
        if (!context.isCompiled(source)) {
          context.affectNodeSource(source);
        }
      }
      if (affectUsages) {
        String nodeName = utils.getNodeName(cl);
        if (nodeName != null) {
          context.affectUsage(new ClassUsage(nodeName));
        }
      }
    }
  }

  private void affectLambdaInstantiations(DifferentiateContext context, Utils utils, ReferenceID fromClass) {
    for (ReferenceID id : utils.withAllSubclasses(fromClass)) {
      if (utils.isLambdaTarget(id)) {
        String clsName = utils.getNodeName(id);
        if (clsName != null) {
          debug("The interface could be not a SAM interface anymore or lambda target method name has changed => affecting lambda instantiations for " + clsName);
          context.affectUsage(new ClassNewUsage(clsName));
        }
      }
    }
  }

  private void debug(String message) {
    // todo
  }

}
