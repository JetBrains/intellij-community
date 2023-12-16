// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import com.intellij.util.containers.SmartHashSet;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.javac.Iterators;

import java.lang.annotation.RetentionPolicy;
import java.util.*;
import java.util.function.Predicate;

import static org.jetbrains.jps.javac.Iterators.*;

public class JavaDifferentiateStrategy extends JvmDifferentiateStrategyImpl {

  private static final Iterable<AnnotationChangesTracker> ourAnnotationChangeTrackers = collect(
    ServiceLoader.load(AnnotationChangesTracker.class, JavaDifferentiateStrategy.class.getClassLoader()),
    new SmartList<>()
  );

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
  public boolean processRemovedClass(DifferentiateContext context, JvmClass removedClass, Utils future, Utils present) {
    debug("Adding usages of removed class ", removedClass.getName());
    context.affectUsage(new ClassUsage(removedClass.getReferenceID()));
    return true;
  }

  @Override
  public boolean processAddedClasses(DifferentiateContext context, Iterable<JvmClass> addedClasses, Utils future, Utils present) {
    if (isEmpty(addedClasses)) {
      return true;
    }

    debug("Processing added classes:");

    BackDependencyIndex index = context.getGraph().getIndex(ClassShortNameIndex.NAME);
    assert index != null;

    // affecting dependencies on all other classes with the same short name
    Set<ReferenceID> affectedNodes = new HashSet<>();

    for (JvmClass addedClass : addedClasses) {
      debug("Class name: ", addedClass.getName());

      if (!processAddedClass(context, addedClass, future, present)) {
        return false;
      }
      
      // class duplication checks
      if (!addedClass.isAnonymous() && !addedClass.isLocal() && addedClass.getOuterFqName().isEmpty()) {
        Set<NodeSource> deletedSources = context.getDelta().getDeletedSources();
        Predicate<? super NodeSource> belongsToChunk = context.getParams().belongsToCurrentCompilationChunk();
        Set<NodeSource> candidates = collect(
          filter(present.getNodeSources(addedClass.getReferenceID()), s -> !deletedSources.contains(s) && belongsToChunk.test(s)), new HashSet<>()
        );

        if (!isEmpty(filter(candidates, src -> !context.isCompiled(src)))) {
          collect(context.getDelta().getSources(addedClass.getReferenceID()), candidates);
          final StringBuilder msg = new StringBuilder();
          msg.append("Possibly duplicated classes in the same compilation chunk; Scheduling for recompilation sources: ");
          for (NodeSource candidate : candidates) {
            context.affectNodeSource(candidate);
            msg.append(candidate).append("; ");
          }
          debug(msg.toString());
          continue; // if duplicates are found, do not perform further checks for classes with the same short name
        }
      }

      if (!addedClass.isAnonymous() && !addedClass.isLocal()) {
        collect(index.getDependencies(new JvmNodeReferenceID(addedClass.getShortName())), affectedNodes);
        affectedNodes.add(addedClass.getReferenceID());
      }
    }

    for (ReferenceID id : unique(flat(map(affectedNodes, id -> context.getGraph().getDependingNodes(id))))) {
      affectNodeSources(context, id, "Affecting dependencies on class with the same short name: " + id + " ", true);
    }
    debug("End of added classes processing.");
    return true;
  }

  @Override
  public boolean processChangedClass(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    JvmClass.Diff classDiff = change.getDiff();
    debug("Processing changed class ", changedClass.getName());
    
    if (classDiff.superClassChanged() || classDiff.signatureChanged() || !classDiff.interfaces().unchanged()) {
      boolean extendsChanged = classDiff.superClassChanged() && !classDiff.extendsAdded();
      boolean affectUsages = classDiff.signatureChanged() || extendsChanged || !isEmpty(classDiff.interfaces().removed());
      affectSubclasses(context, future, change.getNow().getReferenceID(), affectUsages);

      if (extendsChanged) {
        TypeRepr.ClassType exClass = new TypeRepr.ClassType(changedClass.getName());
        for (JvmClass depClass : flat(map(context.getGraph().getDependingNodes(changedClass.getReferenceID()), dep -> present.getNodes(dep, JvmClass.class)))) {
          for (JvmMethod method : depClass.getMethods()) {
            if (contains(method.getExceptions(), exClass)) {
              context.affectUsage(method.createUsage(depClass.getReferenceID()));
              debug("Affecting usages of methods throwing ", exClass.getJvmName(), " exception; class ", depClass.getName());
            }
          }
        }
      }

      if (!changedClass.isAnonymous()) {
        Set<JvmNodeReferenceID> parents = collect(present.allSupertypes(changedClass.getReferenceID()), new HashSet<>());
        parents.removeAll(collect(future.allSupertypes(changedClass.getReferenceID()), new HashSet<>()));
        for (JvmNodeReferenceID parent : parents) {
          debug("Affecting usages in generic type parameter bounds of class: ", parent);
          context.affectUsage(new ClassAsGenericBoundUsage(parent));
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
        Set<ElemType> removedTargets = collect(targetsDiff.removed(), EnumSet.noneOf(ElemType.class));

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
          context.affectUsage(asIterable(changedClass.getReferenceID()), (node, usage) -> {
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

    if (changedClass.getFlags().isEnum() && !isEmpty(classDiff.fields().added()))  {
      debug("Constants added to enum, affecting class usages " + changedClass.getName());
      // only mark synthetic classes used to implement switch statements: this will limit the number of recompiled classes to those where switch statements on changed enum are used
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()), n -> n instanceof JVMClassNode<?, ?> && ((JVMClassNode<?, ?>)n).isSynthetic());
    }

    boolean wasLambdaTarget = present.isLambdaTarget(change.getPast());
    boolean isLambdaTarget = future.isLambdaTarget(change.getNow());
    if (wasLambdaTarget && !isLambdaTarget) {
      // affectLambdaInstantiations
      for (ReferenceID id : present.withAllSubclasses(changedClass.getReferenceID())) {
        if (id.equals(changedClass.getReferenceID()) || present.isLambdaTarget(id)) {
          String clsName = present.getNodeName(id);
          if (clsName != null) {
            debug("The interface could be not a SAM interface anymore => affecting lambda instantiations for ", clsName);
            context.affectUsage(new ClassNewUsage(clsName));
          }
        }
      }
    }
    else if (!wasLambdaTarget && isLambdaTarget) {
      // should affect lambda instantiations on overloads, because some calls may have become ambiguous
      TypeRepr.ClassType samType = new TypeRepr.ClassType(changedClass.getName());
      for (JvmClass depClass : flat(map(context.getGraph().getDependingNodes(changedClass.getReferenceID()), dep -> present.getNodes(dep, JvmClass.class)))) {
        JvmMethod methodWithSAMType = find(depClass.getMethods(), m -> contains(m.getArgTypes(), samType));
        if (methodWithSAMType == null) {
          continue;
        }
        Iterable<Utils.OverloadDescriptor> overloaded = future.findAllOverloads(depClass, m -> {
          if (!Objects.equals(methodWithSAMType.getName(), m.getName()) || m.isSame(methodWithSAMType)) {
            return null;
          }
          // find methods with the same name and arg count as a pattern method, but different signature.
          // calls to found method should look like a call to a pattern method
          Iterator<TypeRepr> patternSignatureTypes = methodWithSAMType.getArgTypes().iterator();
          for (TypeRepr arg : m.getArgTypes()) {
            if (!patternSignatureTypes.hasNext()) {
              return null;
            }
            TypeRepr patternArg = patternSignatureTypes.next();
            if (patternArg.equals(samType)) {
              if (arg.equals(samType) || !(arg instanceof TypeRepr.ClassType)) {
                return null;
              }
            }
            else {
              if (Boolean.FALSE.equals(future.isSubtypeOf(arg, patternArg)) && Boolean.FALSE.equals(future.isSubtypeOf(patternArg, arg))) {
                return null;
              }
            }
          }
          return patternSignatureTypes.hasNext()? null : m.getFlags();
        });
        for (Utils.OverloadDescriptor descr : overloaded) {
          debug("Found method ", methodWithSAMType, " that uses SAM interface ", samType.getJvmName(), " in its signature --- affect potential lambda-target usages of overloaded method: ", descr.overloadMethod);
          affectMemberUsages(
            context,
            descr.owner.getReferenceID(),
            descr.overloadMethod,
            future.collectSubclassesWithoutMethod(descr.owner.getReferenceID(), descr.overloadMethod),
            n -> n instanceof JvmClass && future.isVisibleIn(depClass, methodWithSAMType, (JvmClass)n)
          );
        }
      }
    }

    Difference.Specifier<TypeRepr.ClassType, ?> annotationsDiff = classDiff.annotations();
    if (!annotationsDiff.unchanged()) {
      EnumSet<AnnotationChangesTracker.Recompile> toRecompile = EnumSet.noneOf(AnnotationChangesTracker.Recompile.class);
      for (AnnotationChangesTracker tracker : ourAnnotationChangeTrackers) {
        if (toRecompile.containsAll(AnnotationChangesTracker.RECOMPILE_ALL)) {
          break;
        }
        Set<AnnotationChangesTracker.Recompile> result = tracker.classAnnotationsChanged(changedClass, annotationsDiff);
        if (result.contains(AnnotationChangesTracker.Recompile.USAGES)) {
          debug("Extension ", tracker.getClass().getName(), " requested class usages recompilation because of changes in annotations list --- adding class usage to affected usages");
        }
        if (result.contains(AnnotationChangesTracker.Recompile.SUBCLASSES)) {
          debug("Extension ", tracker.getClass().getName(), " requested subclasses recompilation because of changes in annotations list --- adding subclasses to affected usages");
        }
        toRecompile.addAll(result);
      }
      boolean affectUsages = toRecompile.contains(AnnotationChangesTracker.Recompile.USAGES);
      if (affectUsages) {
        context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
      }
      if (toRecompile.contains(AnnotationChangesTracker.Recompile.SUBCLASSES)) {
        affectSubclasses(context, future, changedClass.getReferenceID(), affectUsages);
      }
    }

    return true;
  }

  @Override
  public boolean processChangedMethods(DifferentiateContext context, JvmClass changedClass, Iterable<Difference.Change<JvmMethod, JvmMethod.Diff>> changed, Utils future, Utils present) {
    debug("Processing changed methods: ");

    for (Difference.Change<JvmMethod, JvmMethod.Diff> change : changed) {
      JvmMethod changedMethod = change.getPast();
      JvmMethod.Diff diff = change.getDiff();

      debug("Method: ", changedMethod.getName());

      if (!processChangedMethod(context, changedClass, change, future, present)) {
        return false;
      }

      if (changedClass.isAnnotation()) {
        if (diff.valueRemoved())  {
          debug("Class is annotation, default value is removed => adding annotation query");
          String argName = changedMethod.getName();
          TypeRepr.ClassType annotType = new TypeRepr.ClassType(changedClass.getName());
          context.affectUsage(asIterable(changedClass.getReferenceID()), (node, usage) -> {
            if (usage instanceof AnnotationUsage) {
              // need to find annotation usages that do not use arguments this annotation uses
              AnnotationUsage au = (AnnotationUsage)usage;
              return annotType.equals(au.getClassType()) && isEmpty(filter(au.getUsedArgNames(), argName::equals));
            }
            return false;
          });
        }
        continue;
      }

      Iterable<JvmNodeReferenceID> propagated = lazy(() -> {
        return future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), changedMethod);
      });

      if (diff.becamePackageLocal()) {
        debug("Method became package-private, affecting method usages outside the package");
        affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, propagated, new PackageConstraint(changedClass.getPackageName()));
      }

      if (diff.typeChanged() || diff.signatureChanged() || !diff.exceptions().unchanged()) {
        debug("Return type, throws list or signature changed --- affecting method usages");
        affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, propagated);

        for (JvmNodeReferenceID subClass : unique(map(future.getOverridingMethods(changedClass, changedMethod, changedMethod::isSameByJavaRules), p -> p.getFirst().getReferenceID()))) {
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
          }

          if (addedFlags.isProtected() && !removedFlags.isPrivate()) {
            debug("Added public or package-private method became protected --- affect method usages with protected constraint");
            affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, propagated, new InheritanceConstraint(future, changedClass));
          }
        }
      }

      Difference.Specifier<TypeRepr.ClassType, ?> annotationsDiff = diff.annotations();
      Difference.Specifier<ParamAnnotation, ?> paramAnnotationsDiff = diff.paramAnnotations();
      if (!annotationsDiff.unchanged() || !paramAnnotationsDiff.unchanged()) {
        EnumSet<AnnotationChangesTracker.Recompile> toRecompile = EnumSet.noneOf(AnnotationChangesTracker.Recompile.class);
        for (AnnotationChangesTracker tracker : ourAnnotationChangeTrackers) {
          if (toRecompile.containsAll(AnnotationChangesTracker.RECOMPILE_ALL)) {
            break;
          }
          Set<AnnotationChangesTracker.Recompile> result = tracker.methodAnnotationsChanged(changedMethod, annotationsDiff, paramAnnotationsDiff);
          if (result.contains(AnnotationChangesTracker.Recompile.USAGES)) {
            debug("Extension ", tracker.getClass().getName(), " requested recompilation because of changes in annotations list --- affecting method usages");
          }
          if (result.contains(AnnotationChangesTracker.Recompile.SUBCLASSES)) {
            debug("Extension ", tracker.getClass().getName(), " requested recompilation because of changes in method annotations or method parameter annotations list --- affecting subclasses");
          }
          toRecompile.addAll(result);
        }
        if (toRecompile.contains(AnnotationChangesTracker.Recompile.USAGES)) {
          affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, propagated);
          if (changedMethod.isAbstract()) {
            for (Pair<JvmClass, JvmMethod> impl : future.getOverridingMethods(changedClass, changedMethod, changedMethod::isSameByJavaRules)) {
              affectMemberUsages(context, impl.first.getReferenceID(), impl.getSecond(), Collections.emptyList());
            }
          }
        }
        if (toRecompile.contains(AnnotationChangesTracker.Recompile.SUBCLASSES)) {
          affectSubclasses(context, future, changedClass.getReferenceID(), false);
        }
      }
    }

    Iterable<Difference.Change<JvmMethod, JvmMethod.Diff>> moreAccessible = collect(filter(changed, ch -> ch.getDiff().accessExpanded()), new SmartList<>());
    if (!isEmpty(moreAccessible)) {
      Iterable<Utils.OverloadDescriptor> overloaded = future.findAllOverloads(changedClass, method -> {
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
      for (Utils.OverloadDescriptor descr : overloaded) {
        debug("Method became more accessible --- affect usages of overloading methods: ", descr.overloadMethod.getName());
        Predicate<Node<?, ?>> constr;
        if (descr.accessScope.isPackageLocal()) {
          constr = new PackageConstraint(changedClass.getPackageName()).negate();
        }
        else {
          if (descr.accessScope.isProtected()) {
            constr = new InheritanceConstraint(future, changedClass).negate();
          }
          else {
            constr = null;
          }
        }

        affectMemberUsages(context, descr.owner.getReferenceID(), descr.overloadMethod, future.collectSubclassesWithoutMethod(descr.owner.getReferenceID(), descr.overloadMethod), constr);
      }
    }

    debug("End of changed methods processing");
    return true;
  }

  @Override
  public boolean processRemovedMethods(DifferentiateContext context, JvmClass changedClass, Iterable<JvmMethod> removed, Utils future, Utils present) {
    debug("Processing removed methods: ");
    Iterators.Provider<Boolean> extendsLibraryClass = Utils.lazyValue(() -> {
      return future.inheritsFromLibraryClass(changedClass);
    });
    for (JvmMethod removedMethod : removed) {
      debug("Method ", removedMethod.getName());

      if (!processRemovedMethod(context, changedClass, removedMethod, future, present)) {
        return false;
      }

      Iterable<JvmNodeReferenceID> propagated = lazy(() -> {
        return future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), removedMethod);
      });

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
        Iterable<Pair<JvmClass, JvmMethod>> overridden = removedMethod.isConstructor()? Collections.emptyList() : lazy(() -> {
          return future.getOverriddenMethods(changedClass, removedMethod::isSameByJavaRules);
        });
        boolean isClearlyOverridden = removedMethod.getSignature().isEmpty() && !extendsLibraryClass.get() && !isEmpty(overridden) && isEmpty(
          filter(overridden, p -> !p.getSecond().getType().equals(removedMethod.getType()) || !p.getSecond().getSignature().isEmpty() || removedMethod.isMoreAccessibleThan(p.getSecond()))
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
            Iterable<Pair<JvmClass, JvmMethod>> overriddenForSubclass = filter(future.getOverriddenMethods(subClass, removedMethod::isSameByJavaRules), p -> p.getSecond().isAbstract() || removedMethod.isSame(p.getSecond()));
            boolean allOverriddenAbstract = !isEmpty(overriddenForSubclass) && isEmpty(filter(overriddenForSubclass, p -> !p.getSecond().isAbstract()));
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
    return true;
  }

  @Override
  public boolean processAddedMethods(DifferentiateContext context, JvmClass changedClass, Iterable<JvmMethod> added, Utils future, Utils present) {
    if (changedClass.isAnnotation()) {
      debug("Class is annotation, skipping method analysis for added methods");
      return true;
    }

    debug("Processing added methods: ");
    for (JvmMethod addedMethod : added) {
      if (!addedMethod.isPrivate() && (changedClass.isInterface() || changedClass.isAbstract() || addedMethod.isAbstract())) {
        debug("Method: " + addedMethod.getName());
        debug("Class is abstract, or is interface, or added non-private method is abstract => affecting all subclasses");
        affectSubclasses(context, future, changedClass.getReferenceID(), false);
        break;
      }
    }

    for (JvmMethod addedMethod : added) {
      debug("Method: ", addedMethod.getName());

      if (!processAddedMethod(context, changedClass, addedMethod, future, present)) {
        return false;
      }

      if (addedMethod.isPrivate()) {
        continue;
      }

      Iterable<JvmNodeReferenceID> propagated = lazy(() -> {
        return future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), addedMethod);
      });

      if (!isEmpty(addedMethod.getArgTypes()) && !present.hasOverriddenMethods(changedClass, addedMethod)) {
        debug("Conservative case on overriding methods, affecting method usages");
        context.affectUsage(asIterable(changedClass.getReferenceID()), addedMethod.createUsageQuery(changedClass.getReferenceID()));
        if (!addedMethod.isConstructor()) { // do not propagate constructors access, since constructors are always concrete and not accessible via references to subclasses
          for (JvmNodeReferenceID id : propagated) {
            context.affectUsage(asIterable(id), addedMethod.createUsageQuery(id));
          }
        }
      }

      if (addedMethod.isStatic()) {
        affectStaticMemberOnDemandUsages(context, changedClass.getReferenceID(), propagated);
      }

      Predicate<JvmMethod> lessSpecificCond = future.lessSpecific(addedMethod);
      for (JvmMethod lessSpecific : filter(changedClass.getMethods(), lessSpecificCond::test)) {
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
        if (!isEmpty(filter(sources, s -> !context.isCompiled(s)))) { // has non-compiled sources
          for (JvmClass outerClass : flat(map(future.getNodes(subClassId, JvmClass.class), cl -> {
            return future.getNodes(new JvmNodeReferenceID(cl.getOuterFqName()), JvmClass.class);
          }))) {
            if (future.isMethodVisible(outerClass, addedMethod)  || future.inheritsFromLibraryClass(outerClass)) {
              for (NodeSource source : filter(sources, context.getParams().affectionFilter()::test)) {
                debug("Affecting file due to local overriding: ", source);
                context.affectNodeSource(source);
              }
            }
          }
        }
      }

    }
    debug("End of added methods processing");
    return true;
  }

  @Override
  public boolean processAddedFields(DifferentiateContext context, JvmClass changedClass, Iterable<JvmField> added, Utils future, Utils present) {
    if (!isEmpty(added)) {
      debug("Processing added fields: ");
    }
    return super.processAddedFields(context, changedClass, added, future, present);
  }

  @Override
  public boolean processAddedField(DifferentiateContext context, JvmClass changedClass, JvmField addedField, Utils future, Utils present) {
    debug("Field: " + addedField.getName());
    Set<JvmNodeReferenceID> changedClassWithSubclasses = future.collectSubclassesWithoutField(changedClass.getReferenceID(), addedField);
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
              Iterable<JvmClass> outerClasses = collect(future.getClassesByName(outerClassName), new SmartList<>());
              if (isEmpty(outerClasses) || !isEmpty(filter(outerClasses, ocl -> {
                return future.isFieldVisible(ocl, addedField);
              }))) {
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
    }

    context.affectUsage(changedClassWithSubclasses, (n, u) -> {
      // affect all clients that access fields with the same name via subclasses,
      // if the added field is not visible to the client
      if (!(u instanceof FieldUsage) || !(n instanceof JvmClass)) {
        return false;
      }
      FieldUsage fieldUsage = (FieldUsage)u;
      return Objects.equals(fieldUsage.getName(), addedField.getName()) && changedClassWithSubclasses.contains(fieldUsage.getElementOwner());
    });
    return true;
  }

  @Override
  public boolean processRemovedFields(DifferentiateContext context, JvmClass changedClass, Iterable<JvmField> removed, Utils future, Utils present) {
    if (!isEmpty(removed)) {
      debug("Process removed fields: ");
    }
    return super.processRemovedFields(context, changedClass, removed, future, present);
  }

  @Override
  public boolean processRemovedField(DifferentiateContext context, JvmClass changedClass, JvmField removedField, Utils future, Utils present) {
    debug("Field: ", removedField.getName());

    if (!context.getParams().isProcessConstantsIncrementally() && !removedField.isPrivate() && removedField.isInlinable() && removedField.getValue() != null) {
      debug("Field had value and was (non-private) final => a switch to non-incremental mode requested");
      if (!affectOnNonIncrementalChange(context, changedClass.getReferenceID(), removedField, present)) {
        debug("End of Differentiate, returning false");
        return false;
      }
    }

    Set<JvmNodeReferenceID> propagated = present.collectSubclassesWithoutField(changedClass.getReferenceID(), removedField);
    affectMemberUsages(context, changedClass.getReferenceID(), removedField, propagated);
    if (!removedField.isPrivate() && removedField.isStatic()) {
      debug("The field was static --- affecting static field import usages");
      affectStaticMemberImportUsages(context, changedClass.getReferenceID(), removedField.getName(), propagated);
    }
    return true;
  }

  @Override
  public boolean processChangedFields(DifferentiateContext context, JvmClass changedClass, Iterable<Difference.Change<JvmField, JvmField.Diff>> changed, Utils future, Utils present) {
    if (!isEmpty(changed)) {
      debug("Process changed fields: ");
    }
    return super.processChangedFields(context, changedClass, changed, future, present);
  }

  @Override
  public boolean processChangedField(DifferentiateContext context, JvmClass changedClass, Difference.Change<JvmField, JvmField.Diff> change, Utils future, Utils present) {
    JvmField changedField = change.getPast();
    JvmField.Diff diff = change.getDiff();

    debug("Field: ", changedField.getName());

    Iterable<JvmNodeReferenceID> propagated = lazy(() -> {
      return future.collectSubclassesWithoutField(changedClass.getReferenceID(), changedField);
    });
    JVMFlags addedFlags = diff.getAddedFlags();
    JVMFlags removedFlags = diff.getRemovedFlags();

    if (!changedField.isPrivate() && changedField.isInlinable() && changedField.getValue() != null) { // if the field was a compile-time constant
      boolean harmful = !isEmpty(filter(List.of(addedFlags, removedFlags), f -> f.isStatic() || f.isFinal()));
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
          if (addedFlags.isProtected()) {
            constraint = new InheritanceConstraint(future, changedClass);
          }
          else {
            constraint = new PackageConstraint(changedClass.getPackageName());
          }
          affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated, constraint);
        }
        else if (removedFlags.isProtected() && diff.accessRestricted()){
          debug("Removed protected modifier and the field became less accessible, affecting field usages with package constraint");
          constraint = new PackageConstraint(changedClass.getPackageName());
          affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated, constraint);
        }

        if (addedFlags.isFinal()) {
          debug("Added final modifier --- affecting field assign usages");
          affectUsages(context, "field assign", flat(asIterable(changedClass.getReferenceID()), propagated), id -> changedField.createAssignUsage(id.getNodeName()), constraint);
        }

      }
    }

    Difference.Specifier<TypeRepr.ClassType, ?> annotationsDiff = diff.annotations();
    if (!annotationsDiff.unchanged()) {
      EnumSet<AnnotationChangesTracker.Recompile> toRecompile = EnumSet.noneOf(AnnotationChangesTracker.Recompile.class);
      for (AnnotationChangesTracker tracker : ourAnnotationChangeTrackers) {
        if (toRecompile.containsAll(AnnotationChangesTracker.RECOMPILE_ALL)) {
          break;
        }
        Set<AnnotationChangesTracker.Recompile> result = tracker.fieldAnnotationsChanged(changedField, annotationsDiff);
        if (result.contains(AnnotationChangesTracker.Recompile.USAGES)) {
          debug("Extension ", tracker.getClass().getName(), " requested recompilation because of changes in annotations list --- affecting field usages");
        }
        if (result.contains(AnnotationChangesTracker.Recompile.SUBCLASSES)) {
          debug("Extension ", tracker.getClass().getName(), " requested recompilation because of changes in field annotations list --- affecting subclasses");
        }
        toRecompile.addAll(result);
      }
      if (toRecompile.contains(AnnotationChangesTracker.Recompile.USAGES)) {
        affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated);
      }
      if (toRecompile.contains(AnnotationChangesTracker.Recompile.SUBCLASSES)) {
        affectSubclasses(context, future, changedClass.getReferenceID(), false);
      }
    }

    return true;
  }

  @Override
  public boolean processAddedModule(DifferentiateContext context, JvmModule addedModule, Utils future, Utils present) {
    // after module has been added, the whole target should be rebuilt
    // because necessary 'require' directives may be missing from the newly added module-info file
    affectModule(context, future, addedModule);
    return true;
  }

  @Override
  public boolean processRemovedModule(DifferentiateContext context, JvmModule removedModule, Utils future, Utils present) {
    affectDependentModules(context, present, removedModule, true, null);
    return true;
  }

  @Override
  public boolean processChangedModule(DifferentiateContext context, Difference.Change<JvmModule, JvmModule.Diff> change, Utils future, Utils present) {
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
        mod -> mod instanceof JvmModule && !isEmpty(filter(((JvmModule)mod).getRequires(), req -> Objects.equals(moduleName, req.getName()) && Objects.equals(version, req.getVersion())))
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
      if (!isEmpty(exportsDiff.removed())) {
        affectDeps = true;
        if (isEmpty(filter(exportsDiff.removed(), modPackage -> !modPackage.isQualified()))) {
          // all removed exports are qualified
          collect(flat(map(exportsDiff.removed(), modPackage -> modPackage.getModules())), constraintPackageNames);
        }
      }
    }

    if (!affectDeps || !constraintPackageNames.isEmpty()) {
      for (Difference.Change<ModulePackage, ModulePackage.Diff> exportChange : exportsDiff.changed()) {
        Iterable<String> removedModuleNames = exportChange.getDiff().targetModules().removed();
        affectDeps |= !isEmpty(removedModuleNames);
        if (affectDeps) {
          collect(removedModuleNames, constraintPackageNames);
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
    return true;
  }

  private void affectSubclasses(DifferentiateContext context, Utils utils, ReferenceID fromClass, boolean affectUsages) {
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

  private boolean affectOnNonIncrementalChange(DifferentiateContext context, JvmNodeReferenceID owner, Proto proto, Utils utils) {
    if (proto.isPublic()) {
      debug("Public access, switching to a non-incremental mode");
      return false;
    }

    if (proto.isProtected()) {
      debug("Protected access, softening non-incremental decision: adding all relevant subclasses for a recompilation");
      debug("Root class: ", owner);
      for (ReferenceID id : proto instanceof JvmField? utils.collectSubclassesWithoutField(owner, ((JvmField)proto)) : utils.allSubclasses(owner)) {
        affectNodeSources(context, id, "Adding ");
      }
    }

    String packageName = JvmClass.getPackageName(owner.getNodeName());
    debug("Softening non-incremental decision: adding all package classes for a recompilation");
    debug("Package name: ", packageName);
    for (ReferenceID nodeWithinPackage : filter(context.getGraph().getRegisteredNodes(), id -> id instanceof JvmNodeReferenceID && packageName.equals(JvmClass.getPackageName(((JvmNodeReferenceID)id).getNodeName())))) {
      affectNodeSources(context, nodeWithinPackage, "Adding ");
    }
    
    return true;
  }

  private void affectNodeSources(DifferentiateContext context, ReferenceID clsId, String affectReason) {
    affectNodeSources(context, clsId, affectReason, false);
  }
  
  private void affectNodeSources(DifferentiateContext context, ReferenceID clsId, String affectReason, boolean forceAffect) {
    Set<NodeSource> deletedSources = context.getDelta().getDeletedSources();
    Predicate<? super NodeSource> affectionFilter = context.getParams().affectionFilter();
    for (NodeSource source : filter(context.getGraph().getSources(clsId), affectionFilter::test)) {
      if (forceAffect || !context.isCompiled(source) && !deletedSources.contains(source)) {
        context.affectNodeSource(source);
        debug(affectReason, source);
      }
    }
  }

  private void affectModule(DifferentiateContext context, Utils utils, JvmModule mod) {
    debug("Affecting module ", mod.getName());
    for (NodeSource source : utils.getNodeSources(mod.getReferenceID())) {
      context.affectNodeSource(source);
      debug("Affected source ", source);
    }
  }

  public void affectDependentModules(DifferentiateContext context, Utils utils, JvmModule fromModule, boolean checkTransitive, @Nullable Predicate<Node<?, ?>> constraint) {
    Iterable<JvmModule> dependent = !checkTransitive? Collections.emptyList() : recurseDepth(
      fromModule,
      mod -> filter(flat(map(context.getGraph().getDependingNodes(mod.getReferenceID()), id -> utils.getNodes(id, JvmModule.class))), m -> m.requiresTransitively(mod.getName())),
      false
    );

    for (JvmModule mod : flat(asIterable(fromModule), dependent)) {
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

}
