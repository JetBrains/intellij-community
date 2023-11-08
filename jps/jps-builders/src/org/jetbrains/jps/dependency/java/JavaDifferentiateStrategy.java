// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.javac.Iterators;

import java.lang.annotation.RetentionPolicy;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public final class JavaDifferentiateStrategy implements DifferentiateStrategy {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.dependency.java.JavaDifferentiateStrategy");
  
  @Override
  public boolean isIncremental(DifferentiateContext context, Node<?, ?> affectedNode) {
    if (affectedNode instanceof JvmClass && ((JvmClass)affectedNode).getFlags().isGenerated()) {
      // If among affected files are annotation processor-generated, then we might need to re-generate them.
      // To achieve this, we need to recompile the whole chunk which will cause processors to re-generate these affected files
      debug("Turning non-incremental for the BuildTarget because dependent class is annotation-processor generated: ", affectedNode.getReferenceID());
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

    if (!Iterators.isEmpty(classesDiff.removed())) {
      debug("Processing removed classes:");
      for (JvmClass removed : classesDiff.removed()) {
        debug("Adding usages of class ", removed.getName());
        context.affectUsage(new ClassUsage(removed.getReferenceID()));
      }
      debug("End of removed classes processing.");
    }

    if (!processAddedClasses(context, classesDiff, future, present)) {
      return false;
    }

    Iterable<Difference.Change<JvmClass, JvmClass.Diff>> changed = classesDiff.changed();
    if (!Iterators.isEmpty(changed)) {
      debug("Processing changed classes:");
      try {
        for (Difference.Change<JvmClass, JvmClass.Diff> change : changed) {
          if (!processChangedClass(context, change, future, present)) {
            return false;
          }
        }
      }
      finally {
        debug("End of changed classes processing");
      }
    }

    Difference.Specifier<JvmModule, JvmModule.Diff> modulesDiff = Difference.deepDiff(
      Graph.getNodesOfType(nodesBefore, JvmModule.class), Graph.getNodesOfType(nodesAfter, JvmModule.class)
    );

    if (!processModules(context, modulesDiff, future, present)) {
      return false;
    }

    return true;
  }

  public boolean processAddedClasses(DifferentiateContext context, Difference.Specifier<JvmClass, JvmClass.Diff> classesDiff, Utils future, Utils present) {
    Iterable<JvmClass> addedClasses = classesDiff.added();
    if (Iterators.isEmpty(addedClasses)) {
      return true;
    }

    debug("Processing added classes:");

    BackDependencyIndex index = context.getGraph().getIndex(ClassShortNameIndex.NAME);
    assert index != null;
    
    // affecting dependencies on all other classes with the same short name
    Set<ReferenceID> affectedNodes = new HashSet<>();

    for (JvmClass addedClass : addedClasses){
      debug("Class name: ", addedClass.getName());

      // class duplication checks
      if (!addedClass.isAnonymous() && !addedClass.isLocal() && addedClass.getOuterFqName().isEmpty()) {
        Set<NodeSource> deletedSources = context.getDelta().getDeletedSources();
        Predicate<? super NodeSource> belongsToChunk = context.getParams().belongsToCurrentCompilationChunk();
        Set<NodeSource> candidates = Iterators.collect(
          Iterators.filter(present.getNodeSources(addedClass.getReferenceID()), s -> !deletedSources.contains(s) && belongsToChunk.test(s)), new HashSet<>()
        );
        
        if (!Iterators.isEmpty(Iterators.filter(candidates, src -> !context.isCompiled(src)))) {
          Iterators.collect(context.getDelta().getSources(addedClass.getReferenceID()), candidates);
          final StringBuilder msg = new StringBuilder();
          msg.append("Possibly duplicated classes in the same compilation chunk; Scheduling for recompilation sources: ");
          for (NodeSource candidate : candidates) {
            context.affectNodeSource(candidate);
            msg.append(candidate.getPath()).append("; ");
          }
          debug(msg.toString());
          continue; // if duplicates are found, do not perform further checks for classes with the same short name
        }
      }

      if (!addedClass.isAnonymous() && !addedClass.isLocal()) {
        Iterators.collect(index.getDependencies(new JvmNodeReferenceID(addedClass.getShortName())), affectedNodes);
        affectedNodes.add(addedClass.getReferenceID());
      }
    }

    for (ReferenceID id : Iterators.unique(Iterators.flat(Iterators.map(affectedNodes, id -> context.getGraph().getDependingNodes(id))))) {
      affectNodeSources(context, id, "Affecting dependencies on class with the same short name: " + id + " ", true);
    }
    debug("End of added classes processing.");
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
              context.affectUsage(method.createUsage(depClass.getReferenceID()));
              debug("Affecting usages of methods throwing ", exClass.getJvmName(), " exception; class ", depClass.getName());
            }
          }
        }
      }

      if (!changedClass.isAnonymous()) {
        Set<JvmNodeReferenceID> parents = Iterators.collect(present.allSupertypes(changedClass.getReferenceID()), new HashSet<>());
        parents.removeAll(Iterators.collect(future.allSupertypes(changedClass.getReferenceID()), new HashSet<>()));
        for (JvmNodeReferenceID parent : parents) {
          debug("Affecting usages in generic type parameter bounds of class: ", parent);
          context.affectUsage(new ClassAsGenericBoundUsage(parent)); // todo: need support of file-filter usage constraint?
        }
      }
    }

    JVMFlags addedFlags = classDiff.getAddedFlags();

    if (addedFlags.isInterface() || classDiff.getRemovedFlags().isInterface()) {
      debug("Class-to-interface or interface-to-class conversion detected, added class usage to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
    }

    if (changedClass.isAnnotation() && changedClass.getRetentionPolicy() == RetentionPolicy.SOURCE) {
      debug("Annotation, retention policy = SOURCE => a switch to non-incremental mode requested");
      if (!affectOnNonIncrementalChange(context, changedClass.getReferenceID(), changedClass, present)) {
        debug("End of Differentiate, returning false");
        return false;
      }
    }

    if (addedFlags.isProtected()) {
      debug("Introduction of 'protected' modifier detected, adding class usage + inheritance constraint to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()), new InheritanceConstraint(future, changedClass));
    }

    if (!changedClass.getFlags().isPackageLocal() && change.getNow().getFlags().isPackageLocal()) {
      debug("Introduction of 'package-private' access detected, adding class usage + package constraint to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()), new PackageConstraint(changedClass.getPackageName()));
    }

    if (addedFlags.isFinal() || addedFlags.isPrivate()) {
      debug("Introduction of 'private' or 'final' modifier(s) detected, adding class usage to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
    }

    if (addedFlags.isAbstract() || addedFlags.isStatic()) {
      debug("Introduction of 'abstract' or 'static' modifier(s) detected, adding class new usage to affected usages");
      context.affectUsage(new ClassNewUsage(changedClass.getReferenceID()));
    }

    if (!changedClass.isAnonymous() && !changedClass.isPrivate() && classDiff.flagsChanged() && changedClass.isInnerClass()) {
      debug("Some modifiers (access flags) were changed for non-private inner class, adding class usage to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
    }

    if (changedClass.isAnnotation()) {
      debug("Class is annotation, performing annotation-specific analysis");

      if (classDiff.retentionPolicyChanged()) {
        debug("Retention policy change detected, adding class usage to affected usages");
        context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
      }
      else if (classDiff.targetAttributeCategoryMightChange()) {
        debug("Annotation's attribute category in bytecode might be affected because of TYPE_USE or RECORD_COMPONENT target, adding class usage to affected usages");
        context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
      }
      else {
        Difference.Specifier<ElemType, ?> targetsDiff = classDiff.annotationTargets();
        Set<ElemType> removedTargets = Iterators.collect(targetsDiff.removed(), EnumSet.noneOf(ElemType.class));

        if (removedTargets.contains(ElemType.LOCAL_VARIABLE)) {
          debug("Removed target contains LOCAL_VARIABLE => a switch to non-incremental mode requested");
          if (!affectOnNonIncrementalChange(context, changedClass.getReferenceID(), changedClass, present)) {
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
            debug("Added method with no default value: ", m.getName());
            debug("Adding class usage to affected usages");
            context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
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
    if (!changedClass.isAnnotation()) {
      processAddedMethods(context, changedClass, methodsDiff.added(), future, present);
    }
    else {
      debug("Class is annotation, skipping method analysis for added methods");
    }
    processRemovedMethods(context, changedClass, methodsDiff.removed(), future, present);
    processChangedMethods(context, changedClass, methodsDiff.changed(), future, present);

    return true;
  }

  private void processChangedMethods(DifferentiateContext context, JvmClass changedClass, Iterable<Difference.Change<JvmMethod, JvmMethod.Diff>> changed, Utils future, Utils present) {
    debug("Processing changed methods: ");

    if (changedClass.isInterface()) {
      for (Difference.Change<JvmMethod, JvmMethod.Diff> change : Iterators.filter(changed, ch -> ch.getDiff().getRemovedFlags().isAbstract())) {
        debug("Method became non-abstract: ", change.getPast().getName());
        affectLambdaInstantiations(context, present, changedClass.getReferenceID());
        break;
      }
    }

    for (Difference.Change<JvmMethod, JvmMethod.Diff> change : changed) {
      JvmMethod changedMethod = change.getPast();
      JvmMethod.Diff diff = change.getDiff();
      if (diff.unchanged()) {
        continue;
      }
      debug("Method: ", changedMethod.getName());

      if (changedClass.isAnnotation()) {
        if (diff.valueRemoved())  {
          debug("Class is annotation, default value is removed => adding annotation query");
          String argName = changedMethod.getName();
          TypeRepr.ClassType annotType = new TypeRepr.ClassType(changedClass.getName());
          context.affectUsage((node, usage) -> {
            if (usage instanceof AnnotationUsage) {
              // need to find annotation usages that do not use arguments this annotation uses
              AnnotationUsage au = (AnnotationUsage)usage;
              return annotType.equals(au.getClassType()) && Iterators.isEmpty(Iterators.filter(au.getUsedArgNames(), argName::equals));
            }
            return false;
          });
        }
        continue;
      }

      Iterable<JvmNodeReferenceID> propagated = Iterators.lazy(() -> future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), changedMethod));

      if (diff.becamePackageLocal()) {
        debug("Method became package-private, affecting method usages outside the package");
        affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, propagated, new PackageConstraint(changedClass.getPackageName()));
      }

      if (diff.typeChanged() || diff.signatureChanged() || !diff.exceptions().unchanged()) {
        debug("Return type, throws list or signature changed --- affecting method usages");
        affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, propagated);

        for (JvmNodeReferenceID subClass : Iterators.unique(Iterators.map(future.getOverridingMethods(changedClass, changedMethod, changedMethod::isSameByJavaRules), p -> p.getFirst().getReferenceID()))) {
          affectNodeSources(context, subClass, "Affect source file of a class which overrides the changed method: ");
        }
      }
      else if (diff.flagsChanged()) {
        JVMFlags addedFlags = diff.getAddedFlags();
        JVMFlags removedFlags = diff.getRemovedFlags();
        if (addedFlags.isStatic() || addedFlags.isPrivate() || addedFlags.isSynthetic() || addedFlags.isBridge() || removedFlags.isStatic()) {

          // When synthetic or bridge flags are added, this effectively means that explicitly written in the code
          // method with the same signature and return type has been removed and a bridge method has been generated instead.
          // In some cases (e.g. using raw types) the presence of such synthetic methods in the bytecode is ignored by the compiler
          // so that the code that called such method via raw type reference might not compile anymore => to be on the safe side
          // we should recompile all places where the method was used

          debug("Added {static | private | synthetic | bridge} specifier or removed static specifier --- affecting method usages");
          affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, propagated);

          if (addedFlags.isStatic()) {
            debug("Added static specifier --- affecting subclasses");
            affectSubclasses(context, future, changedClass.getReferenceID(), false);
            if (!changedMethod.isPrivate()) {
              debug("Added static modifier --- affecting static member on-demand import usages");
              affectStaticMemberOnDemandUsages(context, changedClass.getReferenceID(), propagated);
            }
          }
          else if (removedFlags.isStatic()) {
            if (!changedMethod.isPrivate()) {
              debug("Removed static modifier --- affecting static method import usages");
              affectStaticMemberImportUsages(context, changedClass.getReferenceID(), changedMethod.getName(), propagated);
            }
          }
        }
        else {
          if (addedFlags.isFinal() || addedFlags.isPublic() || addedFlags.isAbstract()) {
            debug("Added final, public or abstract specifier --- affecting subclasses");
            affectSubclasses(context, future, changedClass.getReferenceID(), false);
            if (changedClass.isInterface() && addedFlags.isAbstract()) {
              affectLambdaInstantiations(context, present, changedClass.getReferenceID());
            }
          }

          if (addedFlags.isProtected() && !removedFlags.isPrivate()) {
            debug("Added public or package-private method became protected --- affect method usages with protected constraint");
            affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, propagated, new InheritanceConstraint(future, changedClass));
          }
        }
      }

      // todo: do we need to support AnnotationChangeTracker in the new implementation? Looks like its functionality transforms into kotlin-specific rules
      //Difference.Specifier<TypeRepr.ClassType, ?> annotationsDiff = diff.annotations();
      //if (!annotationsDiff.unchanged()) {
      //}
    }

    Iterable<Difference.Change<JvmMethod, JvmMethod.Diff>> moreAccessible = Iterators.collect(Iterators.filter(changed, ch -> ch.getDiff().accessExpanded()), new SmartList<>());
    if (!Iterators.isEmpty(moreAccessible)) {
      Iterable<OverloadDescriptor> overloaded = findAllOverloads(future, changedClass, method -> {
        JVMFlags mostAccessible = null;
        for (var change : moreAccessible) {
          JvmMethod m = change.getNow();
          if (Objects.equals(m.getName(), method.getName()) && !m.isSame(method)) {
            if (mostAccessible == null || mostAccessible.isWeakerAccess(m.getFlags())) {
              mostAccessible = m.getFlags();
            }
          }
        }
        return mostAccessible;
      });
      for (OverloadDescriptor descr : overloaded) {
        debug("Method became more accessible --- affect usages of overloading methods: ", descr.overloadMethod.getName());
        Predicate<Node<?, ?>> constr =
          descr.accessScope.isPackageLocal()? new PackageConstraint(changedClass.getPackageName()).negate() :
          descr.accessScope.isProtected()? new InheritanceConstraint(future, changedClass).negate() : null;

        affectMemberUsages(context, descr.owner, descr.overloadMethod, future.collectSubclassesWithoutMethod(descr.owner, descr.overloadMethod), constr);
      }
    }

    debug("End of changed methods processing");
  }

  private static Iterable<OverloadDescriptor> findAllOverloads(Utils utils, final JvmClass cls, Function<? super JvmMethod, JVMFlags> correspondenceFinder) {
    Function<JvmClass, Iterable<OverloadDescriptor>> mapper = c -> Iterators.filter(Iterators.map(c.getMethods(), m -> {
      JVMFlags accessScope = correspondenceFinder.apply(m);
      return accessScope != null? new OverloadDescriptor(accessScope, m, c.getReferenceID()) : null;
    }), Iterators.notNullFilter());

    return Iterators.flat(
      Iterators.flat(Iterators.map(Iterators.recurse(cls, cl -> Iterators.flat(Iterators.map(cl.getSuperTypes(), st -> utils.getClassesByName(st))), true), cl -> mapper.apply(cl))),
      Iterators.flat(Iterators.map(utils.allSubclasses(cls.getReferenceID()), id -> Iterators.flat(Iterators.map(utils.getNodes(id, JvmClass.class), cl1 -> mapper.apply(cl1)))))
    );
  }

  private static final class OverloadDescriptor {
    final JVMFlags accessScope;
    final JvmMethod overloadMethod;
    final JvmNodeReferenceID owner;

    OverloadDescriptor(JVMFlags accessScope, JvmMethod overloadMethod, JvmNodeReferenceID owner) {
      this.accessScope = accessScope;
      this.overloadMethod = overloadMethod;
      this.owner = owner;
    }
  }

  private void processRemovedMethods(DifferentiateContext context, JvmClass changedClass, Iterable<JvmMethod> removed, Utils future, Utils present) {
    debug("Processing removed methods: ");
    boolean extendsLibraryClass = future.inheritsFromLibraryClass(changedClass); // todo: lazy?
    for (JvmMethod removedMethod : removed) {
      debug("Method ", removedMethod.getName());
      Iterable<JvmNodeReferenceID> propagated = Iterators.lazy(() -> future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), removedMethod));

      if (!removedMethod.isPrivate() && removedMethod.isStatic()) {
        debug("The method was static --- affecting static method import usages");
        affectStaticMemberImportUsages(context, changedClass.getReferenceID(), removedMethod.getName(), propagated);
      }

      if (removedMethod.isPackageLocal()) {
        // Sometimes javac cannot find an overridden package local method in superclasses, when superclasses are defined in different packages.
        // This results in compilation error when the code is compiled from the very beginning.
        // So even if we correctly find a corresponding overridden method and the bytecode compatibility remains,
        // we still need to affect package local method usages to behave similar to javac.
        debug("Removed method is package-local, affecting method usages");
        affectMemberUsages(context, changedClass.getReferenceID(), removedMethod, propagated);
      }
      else {
        Iterable<Pair<JvmClass, JvmMethod>> overridden = Iterators.lazy(() -> removedMethod.isConstructor()? Collections.emptyList() : future.getOverriddenMethods(changedClass, removedMethod::isSameByJavaRules));
        boolean isClearlyOverridden = removedMethod.getSignature().isEmpty() && !extendsLibraryClass && !Iterators.isEmpty(overridden) && Iterators.isEmpty(
          Iterators.filter(overridden, p -> !p.getSecond().getType().equals(removedMethod.getType()) || !p.getSecond().getSignature().isEmpty() || removedMethod.isMoreAccessibleThan(p.getSecond()))
        );
        if (!isClearlyOverridden) {
          debug("No overridden methods found, affecting method usages");
          affectMemberUsages(context, changedClass.getReferenceID(), removedMethod, propagated);
        }
      }

      for (Pair<JvmClass, JvmMethod> overriding : future.getOverridingMethods(changedClass, removedMethod, removedMethod::isSameByJavaRules)) {
        affectNodeSources(context, overriding.getFirst().getReferenceID(), "Affecting file by overriding: ");
      }

      if (!removedMethod.isConstructor() && !removedMethod.isAbstract() && !removedMethod.isStatic()) {
        for (JvmNodeReferenceID id : propagated) {
          for (JvmClass subClass : future.getNodes(id, JvmClass.class)) {
            Iterable<Pair<JvmClass, JvmMethod>> overriddenForSubclass = Iterators.filter(future.getOverriddenMethods(subClass, removedMethod::isSameByJavaRules), p -> p.getSecond().isAbstract() || removedMethod.isSame(p.getSecond()));
            boolean allOverriddenAbstract = !Iterators.isEmpty(overriddenForSubclass) && Iterators.isEmpty(Iterators.filter(overriddenForSubclass, p -> !p.getSecond().isAbstract()));
            if (allOverriddenAbstract || future.inheritsFromLibraryClass(subClass)) {
              debug("Removed method is not abstract & overrides some abstract method which is not then over-overridden in subclass ", subClass.getName());
              affectNodeSources(context, subClass.getReferenceID(), "Affecting subclass source file: ");
            }
            break;
          }
        }
      }
    }
    debug("End of removed methods processing");
  }

  private void processAddedMethods(DifferentiateContext context, JvmClass changedClass, Iterable<JvmMethod> added, Utils future, Utils present) {
    debug("Processing added methods: ");
    for (JvmMethod addedMethod : added) {
      if (!addedMethod.isPrivate() && (changedClass.isInterface() || changedClass.isAbstract() || addedMethod.isAbstract())) {
        debug("Method: " + addedMethod.getName());
        debug("Class is abstract, or is interface, or added non-private method is abstract => affecting all subclasses");
        affectSubclasses(context, future, changedClass.getReferenceID(), false);
        break;
      }
    }

    if (changedClass.isInterface()) {
      for (JvmMethod addedMethod : added) {
        if (!addedMethod.isPrivate() && addedMethod.isAbstract()) {
          debug("Added non-private abstract method: ", addedMethod.getName());
          affectLambdaInstantiations(context, present, changedClass.getReferenceID());
          break;
        }
      }
    }

    for (JvmMethod addedMethod : added) {
      debug("Method: ", addedMethod.getName());

      if (addedMethod.isPrivate()) {
        continue;
      }

      Iterable<JvmNodeReferenceID> propagated = Iterators.lazy(() -> future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), addedMethod));

      if (!Iterators.isEmpty(addedMethod.getArgTypes()) && !present.hasOverriddenMethods(changedClass, addedMethod)) {
        debug("Conservative case on overriding methods, affecting method usages");
        context.affectUsage(addedMethod.createUsageQuery(changedClass.getReferenceID()));
        if (!addedMethod.isConstructor()) { // do not propagate constructors access, since constructors are always concrete and not accessible via references to subclasses
          for (JvmNodeReferenceID id : propagated) {
            context.affectUsage(new AffectionScopeMetaUsage(id));
            context.affectUsage(addedMethod.createUsageQuery(id));
          }
        }
      }

      if (addedMethod.isStatic()) {
        affectStaticMemberOnDemandUsages(context, changedClass.getReferenceID(), propagated);
      }

      Predicate<JvmMethod> lessSpecificCond = future.lessSpecific(addedMethod);
      for (JvmMethod lessSpecific : Iterators.filter(changedClass.getMethods(), lessSpecificCond::test)) {
        debug("Found less specific method, affecting method usages; ", lessSpecific.getName(), lessSpecific.getDescriptor());
        affectMemberUsages(context, changedClass.getReferenceID(), lessSpecific, present.collectSubclassesWithoutMethod(changedClass.getReferenceID(), lessSpecific));
      }

      debug("Processing affected by specificity methods");

      for (Pair<JvmClass, JvmMethod> pair : future.getOverriddenMethods(changedClass, lessSpecificCond)) {
        JvmClass cls = pair.getFirst();
        JvmMethod overriddenMethod = pair.getSecond();
        // isInheritor(cls, changedClass) == false

        debug("Method: ", overriddenMethod.getName());
        debug("Class : ", cls.getName());
        debug("Affecting method usages for that found");
        affectMemberUsages(context, changedClass.getReferenceID(), overriddenMethod, present.collectSubclassesWithoutMethod(changedClass.getReferenceID(), overriddenMethod));
      }

      for (Pair<JvmClass, JvmMethod> pair : future.getOverridingMethods(changedClass, addedMethod, lessSpecificCond)) {
        JvmClass cls = pair.getFirst();
        JvmMethod overridingMethod = pair.getSecond();
        // isInheritor(cls, changedClass) == true

        debug("Method: ", overridingMethod.getName());
        debug("Class : ", cls.getName());

        if (overridingMethod.isSameByJavaRules(addedMethod)) {
          debug("Current method overrides the added method");
          affectNodeSources(context, cls.getReferenceID(), "Affecting source ");
        }
        else {
          debug("Current method does not override the added method");
          debug("Affecting method usages for the method");
          affectMemberUsages(context, cls.getReferenceID(), overridingMethod, present.collectSubclassesWithoutMethod(cls.getReferenceID(), overridingMethod));
        }
      }

      for (ReferenceID subClassId : future.allSubclasses(changedClass.getReferenceID())) {
        Iterable<NodeSource> sources = context.getGraph().getSources(subClassId);
        if (!Iterators.isEmpty(Iterators.filter(sources, s -> !context.isCompiled(s)))) { // has non-compiled sources
          for (JvmClass outerClass : Iterators.flat(Iterators.map(future.getNodes(subClassId, JvmClass.class), cl -> future.getNodes(new JvmNodeReferenceID(cl.getOuterFqName()), JvmClass.class)))) {
            if (future.isMethodVisible(outerClass, addedMethod)  || future.inheritsFromLibraryClass(outerClass)) {
              for (NodeSource source : Iterators.filter(sources, context.getParams().affectionFilter()::test)) {
                debug("Affecting file due to local overriding: ", source.getPath());
                context.affectNodeSource(source);
              }
            }
          }
        }
      }

    }
    debug("End of added methods processing");
  }

  private boolean processFieldChanges(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> classChange, Utils future, Utils present) {
    JvmClass changedClass = classChange.getPast();
    Difference.Specifier<JvmField, JvmField.Diff> fieldsDiff = classChange.getDiff().fields();
    if (!processAddedFields(context, changedClass, fieldsDiff.added(), future, present)) {
      return false;
    }
    if (!processRemovedFields(context, changedClass, fieldsDiff.removed(), future, present)) {
      return false;
    }
    if (!processChangedFields(context, changedClass, fieldsDiff.changed(), future, present)) {
      return false;
    }
    return true;
  }

  private boolean processAddedFields(DifferentiateContext context, JvmClass changedClass, Iterable<JvmField> added, Utils future, Utils present) {
    if (Iterators.isEmpty(added)) {
      return true;
    }
    debug("Processing added fields");

    if (changedClass.getFlags().isEnum())  {
      debug("Constants added to enum, affecting class usages " + changedClass.getName());
      // only mark synthetic classes used to implement switch statements: this will limit the number of recompiled classes to those where switch statements on changed enum are used
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()), n -> n instanceof JVMClassNode<?, ?> && ((JVMClassNode<?, ?>)n).isSynthetic());
    }

    for (JvmField addedField : added) {
      debug("Field: " + addedField.getName());
      Set<JvmNodeReferenceID> changedClassWithSubclasses = future.collectSubclassesWithoutField(changedClass.getReferenceID(), addedField.getName());
      changedClassWithSubclasses.add(changedClass.getReferenceID());
      for (JvmNodeReferenceID subClass : changedClassWithSubclasses) {
        String affectReason = null;
        if (!addedField.isPrivate()) {
          for (JvmClass cl : future.getNodes(subClass, JvmClass.class)) {
            if (cl.isLocal()) {
              affectReason = "Affecting local subclass (introduced field can potentially hide surrounding method parameters/local variables): ";
              break;
            }
            else {
              String outerClassName = cl.getOuterFqName();
              if (!outerClassName.isEmpty()) {
                Iterable<JvmClass> outerClasses = Iterators.collect(future.getClassesByName(outerClassName), new SmartList<>());
                if (Iterators.isEmpty(outerClasses) || !Iterators.isEmpty(Iterators.filter(outerClasses, ocl -> future.isFieldVisible(ocl, addedField)))) {
                  affectReason = "Affecting inner subclass (introduced field can potentially hide surrounding class fields): ";
                  break;
                }
              }
            }
          }
        }

        if (affectReason != null) {
          affectNodeSources(context, subClass, affectReason);
        }

        if (!addedField.isPrivate() && addedField.isStatic()) {
          affectStaticMemberOnDemandUsages(context, subClass, Collections.emptyList());
        }
        else {
          // ensure analysis scope includes classes that depend on the subclass
          context.affectUsage(new AffectionScopeMetaUsage(subClass));
        }
      }

      context.affectUsage((n, u) -> {
        // affect all clients that access fields with the same name via subclasses,
        // if the added field is not visible to the client
        if (!(u instanceof FieldUsage) || !(n instanceof JvmClass)) {
          return false;
        }
        FieldUsage fieldUsage = (FieldUsage)u;
        return Objects.equals(fieldUsage.getName(), addedField.getName()) && changedClassWithSubclasses.contains(fieldUsage.getElementOwner());
      });
    }
    
    debug("End of added fields processing");
    return true;
  }

  private boolean processRemovedFields(DifferentiateContext context, JvmClass changedClass, Iterable<JvmField> removed, Utils future, Utils present) {
    if (Iterators.isEmpty(removed)) {
      return true;
    }
    debug("Processing removed fields:");

    for (JvmField removedField : removed) {
      debug("Field: ", removedField.getName());

      if (!context.getParams().isProcessConstantsIncrementally() && !removedField.isPrivate() && removedField.isInlinable() && removedField.getValue() != null) {
        debug("Field had value and was (non-private) final => a switch to non-incremental mode requested");
        if (!affectOnNonIncrementalChange(context, changedClass.getReferenceID(), removedField, present)) {
          debug("End of Differentiate, returning false");
          return false;
        }
      }

      Set<JvmNodeReferenceID> propagated = present.collectSubclassesWithoutField(changedClass.getReferenceID(), removedField.getName());
      affectMemberUsages(context, changedClass.getReferenceID(), removedField, propagated);
      if (!removedField.isPrivate() && removedField.isStatic()) {
        debug("The field was static --- affecting static field import usages");
        affectStaticMemberImportUsages(context, changedClass.getReferenceID(), removedField.getName(), propagated);
      }

    }

    debug("End of removed fields processing");
    return true;
  }

  private boolean processChangedFields(DifferentiateContext context, JvmClass changedClass, Iterable<Difference.Change<JvmField, JvmField.Diff>> changed, Utils future, Utils present) {
    if (Iterators.isEmpty(changed)) {
      return true;
    }
    debug("Processing changed fields:");

    for (Difference.Change<JvmField, JvmField.Diff> change : changed) {
      JvmField changedField = change.getPast();
      JvmField.Diff diff = change.getDiff();
      if (diff.unchanged()) {
        continue;
      }

      debug("Field: ", changedField.getName());

      Iterable<JvmNodeReferenceID> propagated = Iterators.lazy(() -> future.collectSubclassesWithoutField(changedClass.getReferenceID(), changedField.getName()));
      JVMFlags addedFlags = diff.getAddedFlags();
      JVMFlags removedFlags = diff.getRemovedFlags();
      
      if (!changedField.isPrivate() && changedField.isInlinable() && changedField.getValue() != null) { // if the field was a compile-time constant
        boolean harmful = !Iterators.isEmpty(Iterators.filter(List.of(addedFlags, removedFlags), f -> f.isStatic() || f.isFinal()));
        if (harmful || diff.valueChanged() || diff.accessRestricted()) {
          if (context.getParams().isProcessConstantsIncrementally()) {
            debug("Potentially inlined field changed its access or value => affecting field usages and static member import usages");
            affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated);
            affectStaticMemberImportUsages(context, changedClass.getReferenceID(), changedField.getName(), propagated);
          }
          else {
            debug("Potentially inlined field changed its access or value => a switch to non-incremental mode requested");
            if (!affectOnNonIncrementalChange(context, changedClass.getReferenceID(), changedField, present)) {
              debug("End of Differentiate, returning false");
              return false;
            }
          }
        }
      }

      if (diff.typeChanged() || diff.signatureChanged()) {
        debug("Type or signature changed --- affecting field usages");
        affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated);
      }
      else if (diff.flagsChanged()) {
        if (addedFlags.isStatic() || removedFlags.isStatic() || addedFlags.isPrivate() || addedFlags.isVolatile()) {
          debug("Added/removed static modifier or added private/volatile modifier --- affecting field usages");
          affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated);
          if (!changedField.isPrivate()) {
            if (addedFlags.isStatic()) {
              debug("Added static modifier --- affecting static member on-demand import usages");
              affectStaticMemberOnDemandUsages(context, changedClass.getReferenceID(), propagated);
            }
            else if (removedFlags.isStatic()) {
              debug("Removed static modifier --- affecting static field import usages");
              affectStaticMemberImportUsages(context, changedClass.getReferenceID(), changedField.getName(), propagated);
            }
          }
        }
        else {
          Predicate<Node<?, ?>> constraint = null;

          if (removedFlags.isPublic()) {
            debug("Removed public modifier, affecting field usages with appropriate constraint");
            constraint = addedFlags.isProtected()? new InheritanceConstraint(future, changedClass) : new PackageConstraint(changedClass.getPackageName());
            affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated, constraint);
          }
          else if (removedFlags.isProtected() && diff.accessRestricted()){
            debug("Removed protected modifier and the field became less accessible, affecting field usages with package constraint");
            constraint = new PackageConstraint(changedClass.getPackageName());
            affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated, constraint);
          }

          if (addedFlags.isFinal()) {
            debug("Added final modifier --- affecting field assign usages");
            affectUsages(context, "field assign", Iterators.flat(Iterators.asIterable(changedClass.getReferenceID()), propagated), id -> changedField.createAssignUsage(id.getNodeName()), constraint);
          }
          
        }
      }

      if (!diff.annotations().unchanged()) {
        // todo: AnnotationsTracker handling
      }
    }

    debug("End of changed fields processing");
    return true;
  }

  private boolean processModules(DifferentiateContext context, Difference.Specifier<JvmModule, JvmModule.Diff> modulesDiff, Utils future, Utils present) {
    if (modulesDiff.unchanged())  {
      return true;
    }
    for (JvmModule addedModule : modulesDiff.added()) {
      // after module has been added, the whole target should be rebuilt
      // because necessary 'require' directives may be missing from the newly added module-info file
      affectModule(context, future, addedModule);
    }

    for (JvmModule removedModule : modulesDiff.removed()) {
      affectDependentModules(context, present, removedModule, true, null);
    }

    for (Difference.Change<JvmModule, JvmModule.Diff> change : modulesDiff.changed()) {
      JvmModule changedModule = change.getPast();
      JvmModule.Diff diff = change.getDiff();
      boolean affectSelf = false;
      boolean affectDeps = false;
      Set<String> constraintPackageNames = new SmartHashSet<>();
      
      if (diff.versionChanged()) {
        String version = changedModule.getVersion();
        String moduleName = changedModule.getName();
        affectDependentModules(
          context, present, changedModule, false,
          mod -> mod instanceof JvmModule && !Iterators.isEmpty(Iterators.filter(((JvmModule)mod).getRequires(), req -> Objects.equals(moduleName, req.getName()) && Objects.equals(version, req.getVersion())))
        );
      }

      Difference.Specifier<ModuleRequires, ModuleRequires.Diff> requiresDiff = diff.requires();
      for (ModuleRequires removedRequires : requiresDiff.removed()) {
        affectSelf = true;
        if (removedRequires.isTransitive()) {
          affectDeps = true;
          break;
        }
      }

      for (Difference.Change<ModuleRequires, ModuleRequires.Diff> rChange : requiresDiff.changed()) {
        affectSelf |= rChange.getDiff().versionChanged();
        if (rChange.getDiff().becameNonTransitive()) {
          affectDeps = true;
          // we could have created more precise constraint here: analyze if required module (recursively)
          // has only qualified exports that include given module's name. But this seems to be excessive since
          // in most cases module's exports are unqualified, so that any other module can access the exported API.
        }
      }

      Difference.Specifier<ModulePackage, ModulePackage.Diff> exportsDiff = diff.exports();
      if (!affectDeps) {
        if (!Iterators.isEmpty(exportsDiff.removed())) {
          affectDeps = true;
          if (Iterators.isEmpty(Iterators.filter(exportsDiff.removed(), modPackage -> !modPackage.isQualified()))) {
            // all removed exports are qualified
            Iterators.collect(Iterators.flat(Iterators.map(exportsDiff.removed(), modPackage -> modPackage.getModules())), constraintPackageNames);
          }
        }
      }

      if (!affectDeps || !constraintPackageNames.isEmpty()) {
        for (Difference.Change<ModulePackage, ModulePackage.Diff> exportChange : exportsDiff.changed()) {
          Iterable<String> removedModuleNames = exportChange.getDiff().targetModules().removed();
          affectDeps |= !Iterators.isEmpty(removedModuleNames);
          if (affectDeps) {
            Iterators.collect(removedModuleNames, constraintPackageNames);
          }
        }
      }

      if (affectSelf) {
        affectModule(context, present, changedModule);
      }

      if (affectDeps) {
        affectDependentModules(
          context, present, changedModule, true, constraintPackageNames.isEmpty()? null : node -> node instanceof JvmModule && constraintPackageNames.contains(((JvmModule)node).getName())
        );
      }
    }

    return true;
  }

  private static void affectMemberUsages(DifferentiateContext context, JvmNodeReferenceID clsId, ProtoMember member, Iterable<JvmNodeReferenceID> propagated) {
    affectMemberUsages(context, clsId, member, propagated, null);
  }

  private static void affectMemberUsages(DifferentiateContext context, JvmNodeReferenceID clsId, ProtoMember member, Iterable<JvmNodeReferenceID> propagated, @Nullable Predicate<Node<?, ?>> constraint) {
    affectUsages(
      context,
      member instanceof JvmMethod? "method" : member instanceof JvmField? "field" : "member",
      Iterators.flat(Iterators.asIterable(clsId), propagated),
      id -> member.createUsage(id),
      constraint
    );
  }

  private static void affectStaticMemberOnDemandUsages(DifferentiateContext context, JvmNodeReferenceID clsId, Iterable<JvmNodeReferenceID> propagated) {
    affectUsages(
      context,
      "static member on-demand import usage",
      Iterators.flat(Iterators.asIterable(clsId), propagated),
      id -> new ImportStaticOnDemandUsage(id),
      null
    );
  }

  private static void affectStaticMemberImportUsages(DifferentiateContext context, JvmNodeReferenceID clsId, String memberName, Iterable<JvmNodeReferenceID> propagated) {
    affectUsages(
      context,
      "static member import",
      Iterators.flat(Iterators.asIterable(clsId), propagated),
      id -> new ImportStaticMemberUsage(id.getNodeName(), memberName),
      null
    );
  }

  private static void affectUsages(DifferentiateContext context, String usageKind, Iterable<JvmNodeReferenceID> usageOwners, Function<? super JvmNodeReferenceID, ? extends Usage> usageFactory, @Nullable Predicate<Node<?, ?>> constraint) {
    for (JvmNodeReferenceID id : usageOwners) {
      if (constraint != null) {
        context.affectUsage(usageFactory.apply(id), constraint);
      }
      else {
        context.affectUsage(usageFactory.apply(id));
      }
      debug("Affect ", usageKind, " usage referenced of class ", id.getNodeName());
    }
  }

  private static void affectSubclasses(DifferentiateContext context, Utils utils, ReferenceID fromClass, boolean affectUsages) {
    debug("Affecting subclasses of class: ", fromClass, "; with usages affection: ", affectUsages);
    for (ReferenceID cl : utils.withAllSubclasses(fromClass)) {
      affectNodeSources(context, cl, "Affecting source file: ");
      if (affectUsages) {
        String nodeName = utils.getNodeName(cl);
        if (nodeName != null) {
          context.affectUsage(new ClassUsage(nodeName));
          debug("Affect usage of class ", nodeName);
        }
      }
    }
  }

  private static void affectLambdaInstantiations(DifferentiateContext context, Utils utils, ReferenceID fromClass) {
    for (ReferenceID id : utils.withAllSubclasses(fromClass)) {
      if (utils.isLambdaTarget(id)) {
        String clsName = utils.getNodeName(id);
        if (clsName != null) {
          debug("The interface could be not a SAM interface anymore or lambda target method name has changed => affecting lambda instantiations for ", clsName);
          context.affectUsage(new ClassNewUsage(clsName));
        }
      }
    }
  }

  // todo: probably support a file filter over a module structure
  private static boolean affectOnNonIncrementalChange(DifferentiateContext context, JvmNodeReferenceID owner, Proto proto, Utils utils) {
    if (proto.isPublic()) {
      debug("Public access, switching to a non-incremental mode");
      return false;
    }

    if (proto.isProtected()) {
      debug("Protected access, softening non-incremental decision: adding all relevant subclasses for a recompilation");
      debug("Root class: ", owner);
      for (ReferenceID id : proto instanceof JvmField? utils.collectSubclassesWithoutField(owner, proto.getName()) : utils.allSubclasses(owner)) {
        affectNodeSources(context, id, "Adding ");
      }
    }

    String packageName = JvmClass.getPackageName(owner.getNodeName());
    debug("Softening non-incremental decision: adding all package classes for a recompilation");
    debug("Package name: ", packageName);
    for (ReferenceID nodeWithinPackage : Iterators.filter(context.getGraph().getRegisteredNodes(), id -> id instanceof JvmNodeReferenceID && packageName.equals(JvmClass.getPackageName(((JvmNodeReferenceID)id).getNodeName())))) {
      affectNodeSources(context, nodeWithinPackage, "Adding ");
    }
    
    return true;
  }

  private static void affectNodeSources(DifferentiateContext context, ReferenceID clsId, String affectReason) {
    affectNodeSources(context, clsId, affectReason, false);
  }
  
  private static void affectNodeSources(DifferentiateContext context, ReferenceID clsId, String affectReason, boolean forceAffect) {
    Set<NodeSource> deletedSources = context.getDelta().getDeletedSources();
    Predicate<? super NodeSource> affectionFilter = context.getParams().affectionFilter();
    for (NodeSource source : Iterators.filter(context.getGraph().getSources(clsId), affectionFilter::test)) {
      if (forceAffect || !context.isCompiled(source) && !deletedSources.contains(source)) {
        context.affectNodeSource(source);
        debug(affectReason, source.getPath());
      }
    }
  }

  private static void affectModule(DifferentiateContext context, Utils utils, JvmModule mod) {
    debug("Affecting module ", mod.getName());
    for (NodeSource source : Iterators.filter(utils.getNodeSources(mod.getReferenceID()), context.getParams().affectionFilter()::test)) {
      context.affectNodeSource(source);
      debug("Affected source ", source.getPath());
    }
  }

  public void affectDependentModules(DifferentiateContext context, Utils utils, JvmModule fromModule, boolean checkTransitive, @Nullable Predicate<Node<?, ?>> constraint) {
    Iterable<JvmModule> dependent = !checkTransitive? Collections.emptyList() : Iterators.recurseDepth(
      fromModule,
      mod -> Iterators.filter(Iterators.flat(Iterators.map(context.getGraph().getDependingNodes(mod.getReferenceID()), id -> utils.getNodes(id, JvmModule.class))), m -> m.requiresTransitively(mod.getName())),
      false
    );

    for (JvmModule mod : Iterators.flat(Iterators.asIterable(fromModule), dependent)) {
      debug("Affecting modules depending on module ", mod.getName());
      ModuleUsage usage = new ModuleUsage(mod.getReferenceID());
      if (constraint != null) {
        context.affectUsage(usage, constraint);
      }
      else {
        context.affectUsage(usage);
      }
    }
  }

  private static void debug(String message, Object... details) {
    if (LOG.isDebugEnabled()) {
      StringBuilder msg = new StringBuilder(message);
      for (Object detail : details) {
        msg.append(detail);
      }
      debug(msg.toString());
    }
  }

  private static void debug(String message) {
    LOG.debug(message);
  }

}
