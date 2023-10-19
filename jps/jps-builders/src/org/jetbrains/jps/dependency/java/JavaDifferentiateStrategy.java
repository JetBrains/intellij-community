// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.openapi.util.Pair;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.javac.Iterators;

import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

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

  private <T> boolean processMethodChanges(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> classChange, Utils future, Utils present) {
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
          break;
        }
      }
    }

    for (JvmMethod addedMethod : methodsDiff.added()) {
      debug("Method: " + addedMethod.getName());

      if (addedMethod.isPrivate()) {
        continue;
      }
      
      Iterable<JvmNodeReferenceID> propagated = Iterators.lazy(() -> future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), addedMethod));

      if (!Iterators.isEmpty(addedMethod.getArgTypes()) && !present.hasOverriddenMethods(changedClass, addedMethod)) {
        debug("Conservative case on overriding methods, affecting method usages");
        context.affectUsage(addedMethod.createUsageQuery(changedClass.getName()));
        if (!addedMethod.isConstructor()) { // do not propagate constructors access, since constructors are always concrete and not accessible via references to subclasses
          for (JvmNodeReferenceID id : propagated) {
            context.affectUsage(new AffectionScopeMetaUsage(id));
            context.affectUsage(addedMethod.createUsageQuery(id.getNodeName()));
          }
        }
      }

      if (addedMethod.isStatic()) {
        affectStaticMemberOnDemandUsages(context, changedClass.getReferenceID(), propagated);
      }

      Predicate<JvmMethod> lessSpecificCond = future.lessSpecific(addedMethod);
      for (JvmMethod lessSpecific : Iterators.filter(changedClass.getMethods(), m -> !Iterators.contains(methodsDiff.removed(), m) && lessSpecificCond.test(m))) {
        debug("Found less specific method, affecting method usages; " + lessSpecific.getName() + lessSpecific.getDescriptor());
        for (JvmNodeReferenceID id : Iterators.flat(Iterators.asIterable(changedClass.getReferenceID()), propagated)) {
          context.affectUsage(lessSpecific.createUsage(id.getNodeName()));
        }
      }

      debug("Processing affected by specificity methods");

      for (Pair<JvmClass, JvmMethod> pair : future.getOverriddenMethods(changedClass, lessSpecificCond)) {
        JvmClass cls = pair.getFirst();
        JvmMethod overriddenMethod = pair.getSecond();
        // isInheritor(cls, changedClass) == false

        debug("Method: " + overriddenMethod.getName());
        debug("Class : " + cls.getName());
        debug("Affecting method usages for that found");
        for (JvmNodeReferenceID id : Iterators.flat(Iterators.asIterable(changedClass.getReferenceID()), present.collectSubclassesWithoutMethod(changedClass.getReferenceID(), overriddenMethod))) {
          context.affectUsage(overriddenMethod.createUsage(id.getNodeName()));
        }
      }

      for (Pair<JvmClass, JvmMethod> pair : future.getOverridingMethods(changedClass, addedMethod, lessSpecificCond)) {
        JvmClass cls = pair.getFirst();
        JvmMethod overridingMethod = pair.getSecond();
        // isInheritor(cls, changedClass) == true

        debug("Method: " + overridingMethod.getName());
        debug("Class : " + cls.getName());

        if (overridingMethod.isSame(addedMethod)) {
          debug("Current method overrides the added method");
          for (NodeSource source : context.getGraph().getSources(cls.getReferenceID())) {
            if (!context.isCompiled(source)) {
              debug("Affecting source " + source.getPath());
              context.affectNodeSource(source);
            }
          }
        }
        else {
          debug("Current method does not override the added method");
          debug("Affecting method usages for the method");
          for (JvmNodeReferenceID id : Iterators.flat(Iterators.asIterable(cls.getReferenceID()), present.collectSubclassesWithoutMethod(cls.getReferenceID(), overridingMethod))) {
            context.affectUsage(overridingMethod.createUsage(id.getNodeName()));
          }
        }
      }

      for (ReferenceID subClassId : future.allSubclasses(changedClass.getReferenceID())) {
        Iterable<NodeSource> sources = context.getGraph().getSources(subClassId);
        if (!Iterators.isEmpty(Iterators.filter(sources, s -> !context.isCompiled(s)))) { // has non-compiled sources
          for (JvmClass outerClass : Iterators.flat(Iterators.map(future.getNodes(subClassId, JvmClass.class), cl -> future.getNodes(new JvmNodeReferenceID(cl.getOuterFqName()), JvmClass.class)))) {
            if (future.isMethodVisible(outerClass, addedMethod)  || future.inheritsFromLibraryClass(outerClass)) {
              for (NodeSource source : sources) {
                debug("Affecting file due to local overriding: " + source.getPath());
                context.affectNodeSource(source);
              }
            }
          }
        }
      }

    }
    debug("End of added methods processing");

    debug("Processing removed methods: ");

    boolean extendsLibraryClass = future.inheritsFromLibraryClass(changedClass); // todo: lazy?
    for (JvmMethod removedMethod : methodsDiff.removed()) {
      debug("Method " + removedMethod.getName());
      Iterable<JvmNodeReferenceID> propagated = Iterators.lazy(() -> future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), removedMethod));

      if (!removedMethod.isPrivate() && removedMethod.isStatic()) {
        debug("The method was static --- affecting static method import usages");
        affectStaticMemberImportUsages(context, changedClass.getReferenceID(), removedMethod.getName(), propagated);
      }

      Iterable<Pair<JvmClass, JvmMethod>> overridden = Iterators.lazy(() -> removedMethod.isConstructor()? Collections.emptyList() : future.getOverriddenMethods(changedClass, removedMethod::isSame));
      boolean isClearlyOverridden = removedMethod.getSignature().isEmpty() && !extendsLibraryClass && !Iterators.isEmpty(overridden) && Iterators.isEmpty(
        Iterators.filter(overridden, p -> !p.getSecond().getType().equals(removedMethod.getType()) || !p.getSecond().getSignature().isEmpty() || removedMethod.isMoreAccessibleThan(p.getSecond()))
      );
      if (!isClearlyOverridden) {
        debug("No overridden methods found, affecting method usages");
        for (JvmNodeReferenceID id : Iterators.flat(Iterators.asIterable(changedClass.getReferenceID()), propagated)) {
          context.affectUsage(removedMethod.createUsage(id.getNodeName()));
          debug("Affect method usage referenced of class " + id.getNodeName());
        }
      }

      for (Pair<JvmClass, JvmMethod> overriding : future.getOverridingMethods(changedClass, removedMethod, removedMethod::isSame)) {
        for (NodeSource source : context.getGraph().getSources(overriding.getFirst().getReferenceID())) {
          if (!context.isCompiled(source)) {
            context.affectNodeSource(source);
            debug("Affecting file by overriding: " + source.getPath());
          }
        }
      }

      if (!removedMethod.isConstructor() && !removedMethod.isAbstract() && !removedMethod.isStatic()) {
        for (JvmNodeReferenceID id : propagated) {
          for (JvmClass subClass : future.getNodes(id, JvmClass.class)) {
            Iterable<Pair<JvmClass, JvmMethod>> overriddenForSubclass = future.getOverriddenMethods(subClass, removedMethod::isSame);
            boolean allOverriddenAbstract = !Iterators.isEmpty(overriddenForSubclass) && Iterators.isEmpty(Iterators.filter(overriddenForSubclass, p -> !p.getSecond().isAbstract()));
            if (allOverriddenAbstract || future.inheritsFromLibraryClass(subClass)) {
              debug("Removed method is not abstract & overrides some abstract method which is not then over-overridden in subclass " + subClass.getName());
              for (NodeSource source : context.getGraph().getSources(subClass.getReferenceID())) {
                if (!context.isCompiled(source)) {
                  context.affectNodeSource(source);
                  debug("Affecting subclass source file: " + source.getPath());
                }
              }
            }
            break;
          }
        }
      }
    }
    debug("End of removed methods processing");


    debug("Processing changed methods: ");
    for (Difference.Change<JvmMethod, JvmMethod.Diff> methodChange : methodsDiff.changed()) {
      // todo: in the new implementation methods with changes in return types will be placed in "changed" category, while in the previous one they were in the "removed" and "added" categories
      // todo: check if 'testChangeToCovariantMethodInBase' test is correct and IImpl really should be recompiled
      JvmMethod changedMethod = methodChange.getPast();
    }
    return true;
  }

  private void affectStaticMemberOnDemandUsages(DifferentiateContext context, JvmNodeReferenceID clsId, Iterable<JvmNodeReferenceID> propagated) {
    for (JvmNodeReferenceID id : Iterators.flat(Iterators.asIterable(clsId), propagated)) {
      debug("Affect static member on-demand import usage referenced of class " + id.getNodeName());
      context.affectUsage(new ImportStaticOnDemandUsage(id.getNodeName()));
    }
  }

  private void affectStaticMemberImportUsages(DifferentiateContext context, JvmNodeReferenceID clsId, String memberName, Iterable<JvmNodeReferenceID> propagated) {
    for (JvmNodeReferenceID id : Iterators.flat(Iterators.asIterable(clsId), propagated)) {
      debug("Affect static member import usage referenced of class " + id.getNodeName());
      context.affectUsage(new ImportStaticMemberUsage(id.getNodeName(), memberName));
    }
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
