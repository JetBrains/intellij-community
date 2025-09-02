// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.util.Pair;

import java.lang.annotation.RetentionPolicy;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.jetbrains.jps.util.Iterators.*;

public final class JavaDifferentiateStrategy extends JvmDifferentiateStrategyImpl {

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

    for (JvmClass addedClass : addedClasses) {
      debug("Class name: ", addedClass.getName());

      // class duplication checks
      if (!addedClass.isAnonymous() && !addedClass.isLocal() && !addedClass.isInnerClass()) {
        if (affectNodeSourcesIfNotCompiled(context, asIterable(addedClass.getReferenceID()), present, "Possibly duplicated classes in the same compilation chunk; Scheduling for recompilation sources: ")) {
          affectSources(context, context.getDelta().getSources(addedClass.getReferenceID()), "Found conflicting class declarations ", true);
          continue; // if duplicates are found, do not perform further checks for classes with the same short name
        }
      }

      String shortName = addedClass.getShortName();
      String scope = addedClass.isInnerClass()? addedClass.getOuterFqName().replace('$', '/') : addedClass.getPackageName();
      debug("Affecting dependencies importing package/class '", scope, "' on-demand and having class-usages with the same short name: '", shortName, "' ");
      context.affectUsage(
        new ImportPackageOnDemandUsage(scope),
        n -> find(n.getUsages(), u -> {
          if (u instanceof ClassUsage || u instanceof MemberUsage) {
            String ownerName = ((JvmElementUsage)u).getElementOwner().getNodeName();
            if (ownerName.endsWith(shortName) && (ownerName.length() == shortName.length() || ownerName.charAt(ownerName.length() - shortName.length() - 1) == '/')) {
              return true;
            }
          }
          return false;
        }) != null
      );
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
      boolean affectUsages = classDiff.signatureChanged() || extendsChanged || !classDiff.interfaces().unchanged();
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
    else if (change.getNow().isSealed()) {
      ReferenceID fromClass = change.getNow().getReferenceID();
      Set<ReferenceID> permitted = collect(map(filter(change.getNow().getUsages(), u -> u instanceof ClassPermitsUsage), Usage::getElementOwner), new HashSet<>());
      debug("Affecting non-permitted subclasses of a sealed class: ", fromClass);
      for (ReferenceID cl : filter(future.directSubclasses(fromClass), c -> !permitted.contains(c))) {
        affectNodeSources(context, cl, "Affecting source file of a non-permitted subclass: ", future);
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
          context.affectUsage(asIterable(changedClass.getReferenceID()), node -> {
            for (Usage usage : node.getUsages()) {
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

    return super.processChangedClass(context, change, future, present);
  }

  @Override
  public boolean processChangedMethods(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Iterable<Difference.Change<JvmMethod, JvmMethod.Diff>> methodChanges, Utils future, Utils present) {
    JvmClass changedClass = clsChange.getPast();
    debug("Processing changed methods: ");

    for (Difference.Change<JvmMethod, JvmMethod.Diff> change : methodChanges) {
      JvmMethod changedMethod = change.getPast();
      JvmMethod.Diff diff = change.getDiff();

      debug("Method: ", changedMethod.getName());

      if (changedClass.isAnnotation()) {
        if (diff.valueRemoved())  {
          debug("Class is annotation, default value is removed => adding annotation query");
          String argName = changedMethod.getName();
          TypeRepr.ClassType annotType = new TypeRepr.ClassType(changedClass.getName());
          context.affectUsage(asIterable(changedClass.getReferenceID()), node -> {
            for (Usage usage : node.getUsages()) {
              if (usage instanceof AnnotationUsage) {
                // need to find annotation usages that do not use arguments this annotation uses
                AnnotationUsage au = (AnnotationUsage)usage;
                return annotType.equals(au.getClassType()) && isEmpty(filter(au.getUsedArgNames(), argName::equals));
              }
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

        if (!changedMethod.isPrivate() && !changedMethod.isConstructor() && !changedMethod.isStatic()) {
          if (!changedMethod.isFinal()) {
            for (JvmNodeReferenceID subClass : unique(map(future.getOverridingMethods(changedClass, changedMethod, changedMethod::isSameByJavaRules), p -> p.first.getReferenceID()))) {
              affectNodeSources(context, subClass, "Affect source file of a class which overrides the changed method: ", future);
            }
          }
          for (JvmNodeReferenceID id : propagated) {
            for (JvmClass subClass : future.getNodes(id, JvmClass.class)) {
              Iterable<Pair<JvmClass, JvmMethod>> overriddenInSubclass = filter(future.getOverriddenMethods(subClass, changedMethod::isSameByJavaRules), p -> !Objects.equals(p.first.getReferenceID(), id));
              if (!isEmpty(overriddenInSubclass)) {
                debug("Changed method is inherited in some subclass & overrides/implements some interface method which this subclass implements. ", subClass.getName());
                affectNodeSources(context, subClass.getReferenceID(), "Affecting subclass source file: ", future);
                break;
              }
            }
          }
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
    }

    Iterable<Difference.Change<JvmMethod, JvmMethod.Diff>> moreAccessible = collect(filter(methodChanges, ch -> ch.getDiff().accessExpanded()), new ArrayList<>());
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
    return super.processChangedMethods(context, clsChange, methodChanges, future, present);
  }

  @Override
  public boolean processRemovedMethods(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Iterable<JvmMethod> removed, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    debug("Processing removed methods: ");
    Supplier<Boolean> extendsLibraryClass = Utils.lazyValue(() -> {
      return future.inheritsFromLibraryClass(changedClass);
    });
    for (JvmMethod removedMethod : removed) {
      debug("Method ", removedMethod.getName());

      Iterable<JvmNodeReferenceID> propagated = lazy(() -> {
        return future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), removedMethod);
      });

      if (!removedMethod.isPrivate() && removedMethod.isStatic() && !removedMethod.isStaticInitializer()) {
        debug("The method was static --- affecting static method import usages");
        affectStaticMemberImportUsages(context, changedClass.getReferenceID(), removedMethod.getName(), propagated);
      }

      if (removedMethod.isPackageLocal()) {
        if (!removedMethod.isStaticInitializer()) {
          // Sometimes javac cannot find an overridden package local method in superclasses, when superclasses are defined in different packages.
          // This results in compilation error when the code is compiled from the very beginning.
          // So even if we correctly find a corresponding overridden method and the bytecode compatibility remains,
          // we still need to affect package local method usages to behave similar to javac.
          debug("Removed method is package-local, affecting method usages");
          affectMemberUsages(context, changedClass.getReferenceID(), removedMethod, propagated);
        }
      }
      else {
        Iterable<Pair<JvmClass, JvmMethod>> overridden = removedMethod.isConstructor()? Collections.emptyList() : lazy(() -> {
          return future.getOverriddenMethods(changedClass, removedMethod::isSameByJavaRules);
        });
        boolean isClearlyOverridden = removedMethod.getSignature().isEmpty() && !extendsLibraryClass.get() && !isEmpty(overridden) && isEmpty(
          filter(overridden, p -> !p.second.getType().equals(removedMethod.getType()) || !p.second.getSignature().isEmpty() || removedMethod.isMoreAccessibleThan(p.second))
        );
        if (!isClearlyOverridden) {
          debug("No overridden methods found, affecting method usages");
          affectMemberUsages(context, changedClass.getReferenceID(), removedMethod, propagated);
        }
      }

      if (removedMethod.isOverridable()) {
        for (Pair<JvmClass, JvmMethod> overriding : future.getOverridingMethods(changedClass, removedMethod, removedMethod::isSameByJavaRules)) {
          affectNodeSources(context, overriding.first.getReferenceID(), "Affecting file by overriding: ", future);
        }
      }

      if (!removedMethod.isConstructor() && !removedMethod.isAbstract() && !removedMethod.isStatic()) {
        for (JvmNodeReferenceID id : propagated) {
          for (JvmClass subClass : future.getNodes(id, JvmClass.class)) {
            Iterable<Pair<JvmClass, JvmMethod>> overriddenForSubclass = filter(future.getOverriddenMethods(subClass, removedMethod::isSameByJavaRules), p -> p.second.isAbstract() || removedMethod.isSame(p.second));
            boolean allOverriddenAbstract = !isEmpty(overriddenForSubclass) && isEmpty(filter(overriddenForSubclass, p -> !p.second.isAbstract()));
            if (allOverriddenAbstract || future.inheritsFromLibraryClass(subClass)) {
              debug("Removed method is not abstract & overrides some abstract method which is not then over-overridden in subclass ", subClass.getName());
              affectNodeSources(context, subClass.getReferenceID(), "Affecting subclass source file: ", future);
              break;
            }
          }
        }
      }
    }
    debug("End of removed methods processing");
    return true;
  }

  @Override
  public boolean processAddedMethods(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Iterable<JvmMethod> added, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
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

      if (addedMethod.isStatic() && !addedMethod.isStaticInitializer()) {
        affectStaticMemberOnDemandUsages(context, changedClass.getReferenceID(), propagated);
      }

      Predicate<JvmMethod> lessSpecificCond = future.lessSpecific(addedMethod);
      for (JvmMethod lessSpecific : filter(changedClass.getMethods(), lessSpecificCond::test)) {
        debug("Found less specific method, affecting method usages; ", lessSpecific.getName(), lessSpecific.getDescriptor());
        affectMemberUsages(context, changedClass.getReferenceID(), lessSpecific, present.collectSubclassesWithoutMethod(changedClass.getReferenceID(), lessSpecific));
      }

      debug("Processing affected by specificity methods");

      for (Pair<JvmClass, JvmMethod> pair : future.getOverriddenMethods(changedClass, lessSpecificCond)) {
        JvmClass cls = pair.first;
        JvmMethod overriddenMethod = pair.second;
        // isInheritor(cls, changedClass) == false

        debug("Method: ", overriddenMethod.getName());
        debug("Class : ", cls.getName());
        debug("Affecting method usages for that found");
        affectMemberUsages(context, changedClass.getReferenceID(), overriddenMethod, present.collectSubclassesWithoutMethod(changedClass.getReferenceID(), overriddenMethod));
      }

      for (Pair<JvmClass, JvmMethod> pair : future.getOverridingMethods(changedClass, addedMethod, lessSpecificCond)) {
        JvmClass cls = pair.first;
        JvmMethod overridingMethod = pair.second;
        // isInheritor(cls, changedClass) == true

        debug("Method: ", overridingMethod.getName());
        debug("Class : ", cls.getName());

        if (overridingMethod.isSameByJavaRules(addedMethod)) {
          debug("Current method overrides the added method");
          affectNodeSources(context, cls.getReferenceID(), "Affecting source ", future);
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
  public boolean processAddedFields(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Iterable<JvmField> added, Utils future, Utils present) {
    if (!isEmpty(added)) {
      debug("Processing added fields: ");
    }
    return super.processAddedFields(context, change, added, future, present);
  }

  @Override
  public boolean processAddedField(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, JvmField addedField, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
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
              Iterable<JvmClass> outerClasses = collect(future.getClassesByName(outerClassName), new ArrayList<>());
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
        affectNodeSources(context, subClass, affectReason, future);
      }

      if (!addedField.isPrivate() && addedField.isStatic()) {
        affectStaticMemberOnDemandUsages(context, subClass, Collections.emptyList());
      }
    }

    context.affectUsage(changedClassWithSubclasses, node -> {
      if (node instanceof JvmClass) {
        for (Usage usage : node.getUsages()) {
          // affect all clients that access fields with the same name via subclasses,
          // if the added field is not visible to the client
          if (usage instanceof FieldUsage) {
            if (Objects.equals(((FieldUsage)usage).getName(), addedField.getName()) && changedClassWithSubclasses.contains(usage.getElementOwner())) {
              return true;
            }
          }
        }
      }
      return false;
    });
    return true;
  }

  @Override
  public boolean processRemovedFields(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Iterable<JvmField> removed, Utils future, Utils present) {
    if (!isEmpty(removed)) {
      debug("Process removed fields: ");
    }
    return super.processRemovedFields(context, change, removed, future, present);
  }

  @Override
  public boolean processRemovedField(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, JvmField removedField, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
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
  public boolean processChangedFields(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> chng, Iterable<Difference.Change<JvmField, JvmField.Diff>> fieldChanges, Utils future, Utils present) {
    if (!isEmpty(fieldChanges)) {
      debug("Process changed fields: ");
    }
    return super.processChangedFields(context, chng, fieldChanges, future, present);
  }

  @Override
  public boolean processChangedField(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmField, JvmField.Diff> fieldChange, Utils future, Utils present) {
    JvmClass changedClass = clsChange.getPast();
    JvmField changedField = fieldChange.getPast();
    JvmField.Diff diff = fieldChange.getDiff();

    debug("Field: ", changedField.getName());

    Iterable<JvmNodeReferenceID> propagated = lazy(() -> {
      return future.collectSubclassesWithoutField(changedClass.getReferenceID(), changedField);
    });
    JVMFlags addedFlags = diff.getAddedFlags();
    JVMFlags removedFlags = diff.getRemovedFlags();

    if (!changedField.isPrivate() && changedField.isInlinable() && changedField.getValue() != null) { // if the field was a compile-time constant
      boolean harmful = find(List.of(addedFlags, removedFlags), f -> f.isStatic() || f.isFinal()) != null;
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

    return super.processChangedField(context, clsChange, fieldChange, future, present);
  }

  @Override
  public boolean processAddedModule(DifferentiateContext context, JvmModule addedModule, Utils future, Utils present) {
    // after module has been added, the whole target should be rebuilt
    // because necessary 'require' directives may be missing from the newly added module-info file
    if (!addedModule.isLibrary()) {
      affectModule(context, future, addedModule);
    }
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
    Set<String> constraintPackageNames = new HashSet<>();

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

    if (affectSelf && !change.getNow().isLibrary()) {
      affectModule(context, present, changedModule);
    }

    if (affectDeps) {
      affectDependentModules(
        context, present, changedModule, true, constraintPackageNames.isEmpty()? null : node -> node instanceof JvmModule && constraintPackageNames.contains(((JvmModule)node).getName())
      );
    }
    return true;
  }

  @Override
  public boolean processNodesWithErrors(DifferentiateContext context, Iterable<JVMClassNode<?, ?>> nodes, Utils present) {
    for (JvmClass jvmClass : Graph.getNodesOfType(nodes, JvmClass.class)) {
      for (JvmField field : filter(jvmClass.getFields(), f -> !f.isPrivate() && f.isInlinable() && f.getValue() != null)) {
        if (context.getParams().isProcessConstantsIncrementally()) {
          debug("Potentially inlined field is contained in a source compiled with errors => affecting field usages and static member import usages");
          var propagated = present.collectSubclassesWithoutField(jvmClass.getReferenceID(), field);
          affectMemberUsages(context, jvmClass.getReferenceID(), field, propagated);
          affectStaticMemberImportUsages(context, jvmClass.getReferenceID(), field.getName(), propagated);
        }
        else {
          debug("Potentially inlined field is contained in a source compiled with errors => a switch to non-incremental mode requested");
          if (!affectOnNonIncrementalChange(context, jvmClass.getReferenceID(), field, present)) {
            return false;
          }
        }
      }
    }
    return true;
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
        affectNodeSources(context, id, "Adding ", utils);
      }
    }

    String packageName = JvmClass.getPackageName(owner.getNodeName());
    debug("Softening non-incremental decision: adding all package classes for a recompilation");
    debug("Package name: ", packageName);
    for (ReferenceID nodeWithinPackage : filter(context.getGraph().getRegisteredNodes(), id -> id instanceof JvmNodeReferenceID && packageName.equals(JvmClass.getPackageName(((JvmNodeReferenceID)id).getNodeName())))) {
      affectNodeSources(context, nodeWithinPackage, "Adding ", utils);
    }
    
    return true;
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
