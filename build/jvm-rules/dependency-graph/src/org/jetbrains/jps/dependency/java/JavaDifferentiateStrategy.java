// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.java;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.DifferentiateContext;
import org.jetbrains.jps.dependency.Graph;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.NodeSource;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.Usage;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.util.Pair;

import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.jetbrains.jps.util.Iterators.asIterable;
import static org.jetbrains.jps.util.Iterators.collect;
import static org.jetbrains.jps.util.Iterators.contains;
import static org.jetbrains.jps.util.Iterators.filter;
import static org.jetbrains.jps.util.Iterators.find;
import static org.jetbrains.jps.util.Iterators.flat;
import static org.jetbrains.jps.util.Iterators.isEmpty;
import static org.jetbrains.jps.util.Iterators.lazyIterable;
import static org.jetbrains.jps.util.Iterators.map;
import static org.jetbrains.jps.util.Iterators.recurseDepth;
import static org.jetbrains.jps.util.Iterators.unique;

public final class JavaDifferentiateStrategy extends JvmDifferentiateStrategyImpl {
  private static final TypeRepr.ClassType REPEATABLE_ANNOTATION = new TypeRepr.ClassType("java/lang/annotation/Repeatable");

  @Override
  public boolean isIncremental(DifferentiateContext context, Node<?, ?> affectedNode) {
    if (affectedNode instanceof JvmClass && ((JvmClass)affectedNode).getFlags().isGenerated()) {
      // If among affected files are annotation processor-generated, then we might need to re-generate them.
      // To achieve this, we need to recompile the whole chunk which will cause processors to re-generate these affected files
      debug(context, "Turning non-incremental for the BuildTarget because dependent class is annotation-processor generated: ", affectedNode.getReferenceID());
      return false;
    }
    return true;
  }

  @Override
  public boolean processRemovedClass(DifferentiateContext context, JvmClass removedClass, Utils future, Utils present) {
    debug(context, "Adding usages of removed class ", removedClass.getName());
    context.affectUsage(new ClassUsage(removedClass.getReferenceID()));
    if (isPackageInfo(removedClass) && !isEmpty(removedClass.getAnnotations())) {
      // a zero-annotation package-info node (possible under -Xpkginfo:always) carries no compilation defaults
      affectPackageScope(context, removedClass);
    }
    return true;
  }

  private static boolean isPackageInfo(JvmClass cls) {
    // 'package-info' is not a valid identifier, so the name can only denote the synthetic package-annotations holder
    return "package-info".equals(cls.getShortName());
  }

  /**
   * Package annotations act as compilation defaults (e.g. nullability defaults like JSR-305 or JSpecify) for every
   * declaration in the package
   */
  private void affectPackageScope(DifferentiateContext context, JvmClass packageInfo) {
    String packageName = packageInfo.getPackageName();
    JvmNodeReferenceID packageId = new JvmNodeReferenceID(packageName);
    debug(context, "package-info of the package '", packageName, "' is added, removed or changed => affecting the package scope");
    // affection scope: the package's own classes (package membership) and the nodes that lookup any name in the package scope
    context.affectUsage(asIterable(packageId), node ->
      (node instanceof JvmClass && packageName.equals(((JvmClass)node).getPackageName()))
      || find(node.getUsages(), u -> u instanceof LookupNameUsage && packageId.equals(u.getElementOwner())) != null
    );
  }

  @Override
  public boolean processAddedClasses(DifferentiateContext context, Iterable<JvmClass> addedClasses, Utils future, Utils present) {
    if (isEmpty(addedClasses)) {
      return true;
    }

    debug(context, "Processing added classes:");

    for (JvmClass addedClass : addedClasses) {
      debug(context, "Class name: ", addedClass.getName());

      // class duplication checks                                                           
      if (!addedClass.isLibrary() && !addedClass.isAnonymous() && !addedClass.isLocal() && !addedClass.isInnerClass()) {
        if (affectNodeSourcesIfNotCompiled(context, asIterable(addedClass.getReferenceID()), present, "Possibly duplicated classes in the same compilation chunk; Scheduling for recompilation sources: ")) {
          affectSources(context, context.getDelta().getSources(addedClass.getReferenceID()), "Found conflicting class declarations ", true);
          continue; // if duplicates are found, do not perform further checks for classes with the same short name
        }
      }

      affectShortNameCapture(context, addedClass, present);

      if (isPackageInfo(addedClass) && !isEmpty(addedClass.getAnnotations())) {
        // a zero-annotation package-info node (possible under -Xpkginfo:always) carries no compilation defaults
        affectPackageScope(context, addedClass);
      }
    }

    debug(context, "End of added classes processing.");
    return true;
  }

  @Override
  public boolean processChangedClass(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    JvmClass.Diff classDiff = change.getDiff();
    debug(context, "Processing changed class ", changedClass.getName());

    if (isPackageInfo(changedClass)) {
      affectPackageScope(context, changedClass);
      return true;
    }

    if (classDiff.superClassChanged() || classDiff.signatureChanged() || !classDiff.interfaces().unchanged()) {
      boolean affectUsages =
        classDiff.signatureChanged()
        || !classDiff.interfaces().unchanged()
        || (classDiff.superClassChanged() && (!classDiff.extendsAdded() || !isEmpty(changedClass.getInterfaces())));

      affectSubclasses(context, future, change.getNow().getReferenceID(), affectUsages);

      if (classDiff.superClassChanged() && !classDiff.extendsAdded()) {
        TypeRepr.ClassType exClass = new TypeRepr.ClassType(changedClass.getName());
        for (JvmClass depClass : flat(map(context.getGraph().getDependingNodes(changedClass.getReferenceID()), dep -> present.getNodes(dep, JvmClass.class)))) {
          for (JvmMethod method : depClass.getMethods()) {
            if (contains(method.getExceptions(), exClass)) {
              context.affectUsage(method.createUsage(depClass.getReferenceID()));
              debug(context, "Affecting usages of methods throwing ", exClass.getJvmName(), " exception; class ", depClass.getName());
            }
          }
        }
      }

      if (!changedClass.isAnonymous()) {
        Set<JvmNodeReferenceID> parents = collect(present.allSupertypes(changedClass.getReferenceID()), new HashSet<>());
        parents.removeAll(collect(future.allSupertypes(changedClass.getReferenceID()), new HashSet<>()));
        for (JvmNodeReferenceID parent : parents) {
          debug(context, "Affecting usages in generic type parameter bounds of class: ", parent);
          context.affectUsage(new ClassAsGenericBoundUsage(parent));
        }
      }
    }
    else if (classDiff.getAddedFlags().isSealed()) {
      // the class became sealed: every direct subclass must now declare exactly one of final/sealed/non-sealed,
      ReferenceID fromClass = change.getNow().getReferenceID();
      debug(context, "Class became sealed => affecting all direct subclasses: ", fromClass);
      for (ReferenceID cl : future.directSubclasses(fromClass)) {
        affectNodeSources(context, cl, "Affecting source file of a direct subclass: ", future);
      }
    }

    JVMFlags addedFlags = classDiff.getAddedFlags();

    if (addedFlags.isInterface() || classDiff.getRemovedFlags().isInterface()) {
      debug(context, "Class-to-interface or interface-to-class conversion detected, added class usage to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
    }

    if (addedFlags.isSealed() && present.isLambdaTarget(changedClass)) {
      // a sealed interface is no longer a functional interface (JLS 9.8) => re-check lambda and method-reference sites,
      // which register a ClassNewUsage of the SAM type ("dynamic proxy instantiation")
      debug(context, "SAM Interface became sealed => affecting lambda instantiation sites");
      context.affectUsage(new ClassNewUsage(changedClass.getReferenceID()));
    }

    for (Difference.Change<SealedMetadata, SealedMetadata.Diff> smChange : classDiff.metadata(SealedMetadata.class).changed()) {
      SealedMetadata.Diff diff = smChange.getDiff();
      if (!isEmpty(diff.permittedSubclasses().added())) {
        // the permits set grew: exhaustive pattern switches reference only the pre-existing case-label subclasses => re-check them
        debug(context, "Permits clause of a sealed class extended => affecting usages of the pre-existing permitted subclasses");
        for (String sibling : smChange.getPast().getPermittedSubclasses()) {
          context.affectUsage(new ClassUsage(sibling));
        }
      }
      for (String removed : diff.permittedSubclasses().removed()) {
        // an ex-permitted subclass may not extend the sealed class anymore => re-check its sources
        affectNodeSources(context, new JvmNodeReferenceID(removed), "Affecting source file of a subclass dropped from the permits clause: ", future);
      }
    }

    for (SealedMetadata removedMeta : classDiff.metadata(SealedMetadata.class).removed()) {
      // The class is not sealed anymore;
      debug(context, "Class is not sealed anymore => affecting formerly 'non-sealed' subclass sources and ex-permitted subclass usages");
      for (String exPermitted : removedMeta.getPermittedSubclasses()) {
        // A direct subclass of a sealed class declares exactly one of final/sealed/non-sealed
        // => affect ex-permitted non-sealed classes, as non-sealed declaration does not make sense anymore
        for (JvmClass subClass : future.getNodes(new JvmNodeReferenceID(exPermitted), JvmClass.class)) {
          if (!subClass.getFlags().isFinal() && !subClass.getFlags().isSealed()) {
            affectNodeSources(context, subClass.getReferenceID(), "Affecting source file of a formerly 'non-sealed' subclass: ", future);
          }
        }
        // Pattern switches over the class may lose exhaustiveness while referencing only the case-label subclasses
        // => affect usages of every ex-permitted subclass
        context.affectUsage(new ClassUsage(exPermitted));
      }
    }

    for (Difference.Change<RecordMetadata, RecordMetadata.Diff> rmChange : classDiff.metadata(RecordMetadata.class).changed()) {
      if (rmChange.getDiff().componentsChanged()) {
        // record deconstruction patterns bind components positionally
        debug(context, "Record component list changed, adding class usage to affected usages");
        context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
        break;
      }
    }

    if (!classDiff.typeAnnotations().unchanged()) {
      // type annotations on the class declaration (type parameters, bounds, supertypes) may change how consumers
      // read the type: e.g. kotlinc derives nullability of type arguments from JSpecify-style type-use annotations
      debug(context, "Type annotations in the class declaration changed, adding class usage to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
    }

    if (classDiff.outerClassChanged()) {
      // same binary name, different source-level owner (e.g. top-level 'A$B' <-> nested 'B' in 'A'):
      // In sources references resolve through different names: A$B <-> A.B, so every referencing dependent must be re-checked.
      debug(context, "Class nesting changed while retaining the binary name, adding class usage to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
      // force recompilation of the declaring side, so that compiler resolves against sources and not compiled code
      affectSources(context, context.getDelta().getSources(changedClass.getReferenceID()), "Class nesting changed; recompiling the declaring source together with its dependents: ", true);
    }

    if (changedClass.isAnnotation() && changedClass.getRetentionPolicy() == RetentionPolicy.SOURCE) {
      debug(context, "Annotation, retention policy = SOURCE => a switch to non-incremental mode requested");
      if (!affectOnNonIncrementalChange(context, changedClass.getReferenceID(), changedClass, present)) {
        debug(context, "End of Differentiate, returning false");
        return false;
      }
    }

    if (addedFlags.isProtected()) {
      debug(context, "Introduction of 'protected' modifier detected, adding class usage + inheritance constraint to affected usages");
      affectUsagesOfLessAccessibleClass(context, changedClass, new InheritanceConstraint(future, changedClass));
    }

    if (!changedClass.getFlags().isPackageLocal() && change.getNow().getFlags().isPackageLocal()) {
      debug(context, "Introduction of 'package-private' access detected, adding class usage + package constraint to affected usages");
      affectUsagesOfLessAccessibleClass(context, changedClass, new PackageConstraint(changedClass.getPackageName()));
    }

    if (addedFlags.isPrivate()) {
      debug(context, "Introduction of 'private' modifier(s) detected, adding class usage to affected usages");
      affectUsagesOfLessAccessibleClass(context, changedClass, null);
    }
    else if (addedFlags.isFinal()) {
      debug(context, "Introduction of 'final' modifier(s) detected, adding class usage to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
    }

    if (classDiff.accessExpanded()) {
      debug(context, "Class access widened: the class becomes visible in scopes that could not see it before and can reference it by its short-name");
      affectShortNameCapture(context, change.getNow(), present);
    }

    if (addedFlags.isAbstract() || addedFlags.isStatic()) {
      debug(context, "Introduction of 'abstract' or 'static' modifier(s) detected, adding class new usage to affected usages");
      context.affectUsage(new ClassNewUsage(changedClass.getReferenceID()));
    }

    if (!changedClass.isAnonymous() && !changedClass.isPrivate() && classDiff.flagsChanged() && changedClass.isInnerClass()) {
      debug(context, "Some modifiers (access flags) were changed for non-private inner class, adding class usage to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
    }

    Difference.Specifier<KotlinMeta, KotlinMeta.Diff> kotlinMetaDiff = classDiff.metadata(KotlinMeta.class);
    if (!isEmpty(kotlinMetaDiff.added()) || !isEmpty(kotlinMetaDiff.removed())) {
      debug(context, "Kotlin metadata has been added or removed, added class usage to affected usages");
      context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
    }

    if (changedClass.isAnnotation()) {
      debug(context, "Class is annotation, performing annotation-specific analysis");

      if (find(classDiff.annotations().removed(), annot -> REPEATABLE_ANNOTATION.equals(annot.getAnnotationClass())) != null) {
        // '@Repeatable' removed: repeated applications of the annotation become illegal
        debug(context, "'@Repeatable' was removed from the annotation declaration, adding annotation query");
        TypeRepr.ClassType repeatableType = new TypeRepr.ClassType(changedClass.getName());
        context.affectUsage(asIterable(changedClass.getReferenceID()), node ->
          find(node.getUsages(), u -> u instanceof AnnotationUsage && repeatableType.equals(((AnnotationUsage)u).getClassType())) != null
        );
      }

      if (classDiff.retentionPolicyChanged()) {
        debug(context, "Retention policy change detected, adding class usage to affected usages");
        context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
      }
      else if (classDiff.targetAttributeCategoryMightChange()) {
        debug(context, "Annotation's attribute category in bytecode might be affected because of TYPE_USE or RECORD_COMPONENT target, adding class usage to affected usages");
        context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
      }
      else {
        Difference.Specifier<ElemType, ?> targetsDiff = classDiff.annotationTargets();
        Set<ElemType> removedTargets = collect(targetsDiff.removed(), EnumSet.noneOf(ElemType.class));

        if (removedTargets.contains(ElemType.LOCAL_VARIABLE)) {
          debug(context, "Removed target contains LOCAL_VARIABLE => a switch to non-incremental mode requested");
          if (!affectOnNonIncrementalChange(context, changedClass.getReferenceID(), changedClass, present)) {
            debug(context, "End of Differentiate, returning false");
            return false;
          }
        }

        if (!removedTargets.isEmpty()) {
          debug(context, "Removed some annotation targets, adding annotation query");
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

        if (isEmpty(change.getPast().getAnnotationTargets()) && !isEmpty(change.getNow().getAnnotationTargets())) {
          // a '@Target' clause was added to a previously unrestricted annotation: affect annotation usages on yet not allowed targets
          debug(context, "'@Target' clause added to a previously unrestricted annotation, adding annotation query");
          Set<ElemType> nowTargets = collect(change.getNow().getAnnotationTargets(), EnumSet.noneOf(ElemType.class));
          TypeRepr.ClassType classType = new TypeRepr.ClassType(changedClass.getName());
          context.affectUsage(asIterable(changedClass.getReferenceID()), node -> {
            for (ElemType target : flat(map(node.getUsages(), u -> u instanceof AnnotationUsage && classType.equals(((AnnotationUsage)u).getClassType())? ((AnnotationUsage)u).getTargets() : List.of() ))) {
              if (!nowTargets.contains(target)) {
                return true;
              }
            }
            return false;
          });
        }

        for (JvmMethod m : classDiff.methods().added()) {
          if (m.getValue() == null) {
            debug(context, "Added method with no default value: ", m.getName());
            debug(context, "Adding class usage to affected usages");
            context.affectUsage(new ClassUsage(changedClass.getReferenceID()));
            break;
          }
        }
      }
      debug(context, "End of annotation-specific analysis");
    }

    if (changedClass.getFlags().isEnum() && !isEmpty(classDiff.fields().added()))  {
      debug(context, "Constants added to enum, affecting class usages " + changedClass.getName());
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
            debug(context, "The interface could be not a SAM interface anymore => affecting lambda instantiations for ", clsName);
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
          debug(context, "Found method ", methodWithSAMType, " that uses SAM interface ", samType.getJvmName(), " in its signature --- affect potential lambda-target usages of overloaded method: ", descr.overloadMethod);
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
    debug(context, "Processing changed methods: ");

    for (Difference.Change<JvmMethod, JvmMethod.Diff> change : methodChanges) {
      JvmMethod changedMethod = change.getPast();
      JvmMethod.Diff diff = change.getDiff();

      debug(context, "Method: ", changedMethod.getName());

      if (changedClass.isAnnotation()) {
        if (diff.valueRemoved())  {
          debug(context, "Class is annotation, default value is removed => adding annotation query");
          String argName = changedMethod.getName();
          TypeRepr.ClassType annotType = new TypeRepr.ClassType(changedClass.getName());
          context.affectUsage(asIterable(changedClass.getReferenceID()), node -> {
            for (Usage usage : node.getUsages()) {
              if (usage instanceof AnnotationUsage) {
                // need to find annotation usages that do not use arguments this annotation uses;
                // a node may use several annotations => keep scanning until THIS annotation's usage is found
                AnnotationUsage au = (AnnotationUsage)usage;
                if (annotType.equals(au.getClassType()) && isEmpty(filter(au.getUsedArgNames(), argName::equals))) {
                  return true;
                }
              }
            }
            return false;
          });
        }
        continue;
      }

      Iterable<JvmNodeReferenceID> propagated = lazyIterable(() -> {
        return future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), changedMethod);
      });

      if (diff.becamePackageLocal()) {
        debug(context, "Method became package-private, affecting method usages outside the package");
        affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, propagated, new PackageConstraint(changedClass.getPackageName()));

        if (diff.accessRestricted() && !changedMethod.isConstructor() && !changedMethod.isStatic() && !changedMethod.isFinal()) {
          // a package-private method is not inherited outside its package: out-of-package methods with the same signature
          // silently stop overriding it => re-check their declarations
          for (Pair<JvmClass, JvmMethod> overriding : future.getOverridingMethods(changedClass, changedMethod, changedMethod::isSameByJavaRules)) {
            if (!Objects.equals(overriding.first.getPackageName(), changedClass.getPackageName())) {
              affectNodeSources(context, overriding.first.getReferenceID(), "Affect source file of an out-of-package class overriding the method that became package-private: ", future);
            }
          }
        }
      }

      if (!diff.typeAnnotations().unchanged()) {
        // type annotations in the method signature may change how consumers read the declared types
        // (e.g. kotlinc derives nullability from JSpecify-style type-use annotations) => re-check use sites
        debug(context, "Type annotations in the method signature changed --- affecting method usages");
        affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, propagated);
      }

      if (diff.typeChanged() || diff.signatureChanged() || !diff.exceptions().unchanged()) {
        debug(context, "Return type, throws list or signature changed --- affecting method usages");
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
                debug(context, "Changed method is inherited in some subclass & overrides/implements some interface method which this subclass implements. ", subClass.getName());
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
        if (addedFlags.isStatic() || addedFlags.isPrivate() || addedFlags.isSynthetic() || addedFlags.isBridge() || removedFlags.isStatic() || addedFlags.isVarargs() || removedFlags.isVarargs() || removedFlags.isSynthetic() || removedFlags.isBridge()) {

          // When synthetic or bridge flags are added, this effectively means that explicitly written in the code
          // method with the same signature and return type has been removed and a bridge method has been generated instead.
          // In some cases (e.g. using raw types) the presence of such synthetic methods in the bytecode is ignored by the compiler
          // so that the code that called such method via raw type reference might not compile anymore => to be on the safe side
          // we should recompile all places where the method was used

          // A varargs flag toggle keeps the descriptor unchanged, but changes source-level applicability:
          // vararg-style call sites (argument count != 1 or non-array argument) stop compiling when the flag is cleared, and method applicability widens when it is set => re-check all use sites

          debug(context, "Added {static | private | synthetic | bridge} specifier, removed {static | synthetic | bridge} specifier or toggled varargs --- affecting method usages");
          affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, propagated);

          if (removedFlags.isSynthetic() || removedFlags.isBridge()) {
            // the method enters source-level resolution (a synthetic member is unavailable to source code, JLS 13.1):
            // callers currently bound to another applicable same-name overload may re-bind to the appeared exact match
            debug(context, "Removed synthetic or bridge specifier --- affecting usages of all same-name overloads");
            context.affectUsage(asIterable(changedClass.getReferenceID()), changedMethod.createUsageQuery(changedClass.getReferenceID()));
          }

          if (addedFlags.isPrivate() && !changedMethod.isConstructor() && !changedMethod.isStatic() && !changedMethod.isFinal()) {
            // a private method is not inherited and cannot be overridden: same-signature methods in subclasses
            // silently stop overriding it => re-check their declarations
            for (JvmNodeReferenceID subClass : unique(map(future.getOverridingMethods(changedClass, changedMethod, changedMethod::isSameByJavaRules), p -> p.first.getReferenceID()))) {
              affectNodeSources(context, subClass, "Affect source file of a class overriding the method that became private: ", future);
            }
          }

          if (addedFlags.isStatic()) {
            debug(context, "Added static specifier --- affecting subclasses");
            affectSubclasses(context, future, changedClass.getReferenceID(), false);
            if (!changedMethod.isPrivate()) {
              debug(context, "Added static modifier --- affecting static member on-demand import usages");
              affectStaticMemberOnDemandUsages(context, changedClass.getReferenceID(), propagated);
            }
          }
          else if (removedFlags.isStatic()) {
            if (!changedMethod.isPrivate()) {
              debug(context, "Removed static modifier --- affecting static method import usages");
              affectStaticMemberImportUsages(context, changedClass.getReferenceID(), changedMethod.getName(), propagated);

              if (changedClass.isInterface()) {
                // the method becomes a default method inherited by implementors: a newly inherited default can clash
                // with an unrelated default from another interface (diamond) => re-check implementor sources
                debug(context, "Interface method became default --- affecting implementors");
                affectSubclasses(context, future, changedClass.getReferenceID(), false);
              }
              else {
                // a same-signature static method in a subclass legally hid this method while it was static,
                // but a static method cannot hide/override an instance method (JLS 8.4.8.2) => re-check the declaring subclasses
                for (Pair<JvmClass, JvmMethod> pair : future.getOverridingMethods(changedClass, changedMethod, changedMethod::isSameByJavaRules)) {
                  affectNodeSources(context, pair.first.getReferenceID(), "Affect source file of a class hiding the method that lost its static modifier: ", future);
                }
              }
            }
          }
        }
        else {
          if (addedFlags.isFinal() || addedFlags.isPublic() || addedFlags.isAbstract()) {
            debug(context, "Added final, public or abstract specifier --- affecting subclasses");
            affectSubclasses(context, future, changedClass.getReferenceID(), false);
          }
          else {
            boolean widenedFromPrivate = change.getPast().isPrivate();
            if (diff.accessExpanded() && (widenedFromPrivate || addedFlags.isProtected() && change.getPast().isPackageLocal())) {
              // the method becomes inherited into subclass scopes that could not see it before:
              // a same-signature subclass method can silently become an override and must be re-checked (return type compatibility, throws, dispatch).
              // The walk uses the NOW member, so its new visibility bounds the traversal; in-package matches were already
              // overrides for a package-private past method and need no re-check
              JvmMethod nowMethod = change.getNow();
              for (Pair<JvmClass, JvmMethod> pair : future.getOverridingMethods(changedClass, nowMethod, nowMethod::isSameByJavaRules)) {
                if (widenedFromPrivate || !Objects.equals(pair.first.getPackageName(), changedClass.getPackageName())) {
                  affectNodeSources(context, pair.first.getReferenceID(), "Affect source file of a class whose method becomes an override of the access-widened method: ", future);
                }
              }
            }
          }

          if (addedFlags.isProtected() && !removedFlags.isPrivate()) {
            debug(context, "Added public or package-private method became protected --- affect method usages with protected constraint");
            affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, propagated, new PackageConstraint(changedClass.getPackageName()));
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
        debug(context, "Method became more accessible --- affect usages of overloading methods: ", descr.overloadMethod.getName());
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

    debug(context, "End of changed methods processing");
    return super.processChangedMethods(context, clsChange, methodChanges, future, present);
  }

  @Override
  public boolean processRemovedMethods(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Iterable<JvmMethod> removed, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    debug(context, "Processing removed methods: ");
    Supplier<Boolean> extendsLibraryClass = Utils.lazyValue(() -> {
      return future.inheritsFromUnknownClass(changedClass);
    });
    for (JvmMethod removedMethod : removed) {
      debug(context, "Method ", removedMethod.getName());

      Iterable<JvmNodeReferenceID> propagated = lazyIterable(() -> {
        return future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), removedMethod);
      });

      if (!removedMethod.isPrivate() && removedMethod.isStatic() && !removedMethod.isStaticInitializer()) {
        debug(context, "The method was static --- affecting static method import usages");
        affectStaticMemberImportUsages(context, changedClass.getReferenceID(), removedMethod.getName(), propagated);
      }

      if (removedMethod.isPackageLocal()) {
        if (!removedMethod.isStaticInitializer()) {
          // Sometimes javac cannot find an overridden package local method in superclasses, when superclasses are defined in different packages.
          // This results in compilation error when the code is compiled from the very beginning.
          // So even if we correctly find a corresponding overridden method and the bytecode compatibility remains,
          // we still need to affect package local method usages to behave similar to javac.
          debug(context, "Removed method is package-local, affecting method usages");
          affectMemberUsages(context, changedClass.getReferenceID(), removedMethod, propagated);
        }
      }
      else {
        Iterable<Pair<JvmClass, JvmMethod>> overridden = removedMethod.isConstructor()? Collections.emptyList() : lazyIterable(() -> {
          return future.getOverriddenMethods(changedClass, removedMethod::isSameByJavaRules);
        });
        boolean isClearlyOverridden = removedMethod.getSignature().isEmpty() && !extendsLibraryClass.get() && !isEmpty(overridden) && isEmpty(
          filter(overridden, p -> !p.second.getType().equals(removedMethod.getType()) || !p.second.getSignature().isEmpty() || removedMethod.isMoreAccessibleThan(p.second))
        );
        if (!isClearlyOverridden) {
          debug(context, "No overridden methods found, affecting method usages");
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
            if (allOverriddenAbstract || future.inheritsFromUnknownClass(subClass)) {
              debug(context, "Removed method is not abstract & overrides some abstract method which is not then over-overridden in subclass ", subClass.getName());
              affectNodeSources(context, subClass.getReferenceID(), "Affecting subclass source file: ", future);
              break;
            }
          }
        }
      }
    }
    debug(context, "End of removed methods processing");
    return true;
  }

  @Override
  public boolean processAddedMethods(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Iterable<JvmMethod> added, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    if (changedClass.isAnnotation()) {
      debug(context, "Class is annotation, skipping method analysis for added methods");
      return true;
    }

    debug(context, "Processing added methods: ");
    for (JvmMethod addedMethod : added) {
      if (!addedMethod.isPrivate() && (changedClass.isInterface() || changedClass.isAbstract() || addedMethod.isAbstract())) {
        debug(context, "Method: " + addedMethod.getName());
        debug(context, "Class is abstract, or is interface, or added non-private method is abstract => affecting all subclasses");
        affectSubclasses(context, future, changedClass.getReferenceID(), false);
        break;
      }
    }

    for (JvmMethod addedMethod : added) {
      debug(context, "Method: ", addedMethod.getName());

      if (addedMethod.isPrivate()) {
        continue;
      }

      Iterable<JvmNodeReferenceID> propagated = lazyIterable(() -> {
        return future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), addedMethod);
      });

      if (!isEmpty(addedMethod.getArgTypes()) && !present.hasOverriddenMethods(changedClass, addedMethod)) {
        debug(context, "Conservative case on overriding methods, affecting method usages");
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

      if (addedMethod.isConstructor()) {
        // an added constructor may capture `new` invocations currently bound to a vararg constructor through (vararg) applicability;
        // Utils.lessSpecific models neither constructors nor varargs, and the
        // conservative usage-query above skips zero-argument methods => re-check vararg constructor use sites
        for (JvmMethod ctor : filter(changedClass.getMethods(), m -> m.isConstructor() && m.getFlags().isVarargs() && !m.isSame(addedMethod))) {
          debug(context, "Added constructor may capture invocations of a vararg constructor --- affecting the vararg constructor usages");
          affectMemberUsages(context, changedClass.getReferenceID(), ctor, Collections.emptyList());
        }
      }

      Predicate<JvmMethod> lessSpecificCond = future.lessSpecific(addedMethod);
      for (JvmMethod lessSpecific : filter(changedClass.getMethods(), lessSpecificCond)) {
        debug(context, "Found less specific method, affecting method usages; ", lessSpecific.getName(), lessSpecific.getDescriptor());
        affectMemberUsages(context, changedClass.getReferenceID(), lessSpecific, present.collectSubclassesWithoutMethod(changedClass.getReferenceID(), lessSpecific));
      }

      debug(context, "Processing affected by specificity methods");

      for (Pair<JvmClass, JvmMethod> pair : future.getOverriddenMethods(changedClass, lessSpecificCond)) {
        JvmClass cls = pair.first;
        JvmMethod overriddenMethod = pair.second;
        // isInheritor(cls, changedClass) == false

        debug(context, "Method: ", overriddenMethod.getName());
        debug(context, "Class : ", cls.getName());
        debug(context, "Affecting method usages for that found");
        affectMemberUsages(context, changedClass.getReferenceID(), overriddenMethod, present.collectSubclassesWithoutMethod(changedClass.getReferenceID(), overriddenMethod));
      }

      for (Pair<JvmClass, JvmMethod> pair : future.getOverridingMethods(changedClass, addedMethod, lessSpecificCond)) {
        JvmClass cls = pair.first;
        JvmMethod overridingMethod = pair.second;
        // isInheritor(cls, changedClass) == true

        debug(context, "Method: ", overridingMethod.getName());
        debug(context, "Class : ", cls.getName());

        if (overridingMethod.isSameByJavaRules(addedMethod)) {
          debug(context, "Current method overrides the added method");
          affectNodeSources(context, cls.getReferenceID(), "Affecting source ", future);
        }
        else {
          debug(context, "Current method does not override the added method");
          debug(context, "Affecting method usages for the method");
          affectMemberUsages(context, cls.getReferenceID(), overridingMethod, present.collectSubclassesWithoutMethod(cls.getReferenceID(), overridingMethod));
        }
      }

      for (ReferenceID subClassId : future.allSubclasses(changedClass.getReferenceID())) {
        Iterable<NodeSource> sources = context.getGraph().getSources(subClassId);
        if (!isEmpty(filter(sources, s -> !context.isCompiled(s)))) { // has non-compiled sources
          for (JvmClass outerClass : flat(map(future.getNodes(subClassId, JvmClass.class), cl -> {
            return future.getNodes(new JvmNodeReferenceID(cl.getOuterFqName()), JvmClass.class);
          }))) {
            if (future.isMethodVisible(outerClass, addedMethod)  || future.inheritsFromUnknownClass(outerClass)) {
              for (NodeSource source : filter(sources, context.getParams().affectionFilter())) {
                debug(context, "Affecting file due to local overriding: ", source);
                context.affectNodeSource(source);
              }
            }
          }
        }
      }

    }
    debug(context, "End of added methods processing");
    return true;
  }

  @Override
  public boolean processAddedFields(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Iterable<JvmField> added, Utils future, Utils present) {
    if (!isEmpty(added)) {
      debug(context, "Processing added fields: ");
    }
    return super.processAddedFields(context, change, added, future, present);
  }

  @Override
  public boolean processAddedField(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, JvmField addedField, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    debug(context, "Field: " + addedField.getName());
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
      debug(context, "Process removed fields: ");
    }
    return super.processRemovedFields(context, change, removed, future, present);
  }

  @Override
  public boolean processRemovedField(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, JvmField removedField, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    debug(context, "Field: ", removedField.getName());

    if (!context.getParams().isProcessConstantsIncrementally() && !removedField.isPrivate() && removedField.isInlinable() && removedField.getValue() != null) {
      debug(context, "Field had value and was (non-private) final => a switch to non-incremental mode requested");
      if (!affectOnNonIncrementalChange(context, changedClass.getReferenceID(), removedField, present)) {
        debug(context, "End of Differentiate, returning false");
        return false;
      }
    }

    Set<JvmNodeReferenceID> propagated = present.collectSubclassesWithoutField(changedClass.getReferenceID(), removedField);
    affectMemberUsages(context, changedClass.getReferenceID(), removedField, propagated);
    if (!removedField.isPrivate() && removedField.isStatic()) {
      debug(context, "The field was static --- affecting static field import usages");
      affectStaticMemberImportUsages(context, changedClass.getReferenceID(), removedField.getName(), propagated);
    }
    return true;
  }

  @Override
  public boolean processChangedFields(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> chng, Iterable<Difference.Change<JvmField, JvmField.Diff>> fieldChanges, Utils future, Utils present) {
    if (!isEmpty(fieldChanges)) {
      debug(context, "Process changed fields: ");
    }
    return super.processChangedFields(context, chng, fieldChanges, future, present);
  }

  @Override
  public boolean processChangedField(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmField, JvmField.Diff> fieldChange, Utils future, Utils present) {
    JvmClass changedClass = clsChange.getPast();
    JvmField changedField = fieldChange.getPast();
    JvmField.Diff diff = fieldChange.getDiff();

    debug(context, "Field: ", changedField.getName());

    Iterable<JvmNodeReferenceID> propagated = lazyIterable(() -> {
      return future.collectSubclassesWithoutField(changedClass.getReferenceID(), changedField);
    });
    JVMFlags addedFlags = diff.getAddedFlags();
    JVMFlags removedFlags = diff.getRemovedFlags();

    if (!changedField.isPrivate() && changedField.isInlinable() && changedField.getValue() != null) { // if the field was a compile-time constant
      boolean harmful = find(List.of(addedFlags, removedFlags), f -> f.isStatic() || f.isFinal()) != null;
      if (harmful || diff.valueChanged() || diff.accessRestricted()) {
        if (context.getParams().isProcessConstantsIncrementally()) {
          debug(context, "Potentially inlined field changed its access or value => affecting field usages and static member import usages");
          affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated);
          affectStaticMemberImportUsages(context, changedClass.getReferenceID(), changedField.getName(), propagated);
        }
        else {
          debug(context, "Potentially inlined field changed its access or value => a switch to non-incremental mode requested");
          if (!affectOnNonIncrementalChange(context, changedClass.getReferenceID(), changedField, present)) {
            debug(context, "End of Differentiate, returning false");
            return false;
          }
        }
      }
    }

    if (diff.accessExpanded()) {
      // the field becomes visible in scopes that could not see it before: for those scopes this is effectively a field ADDITION.
      debug(context, "Field access widened --- affecting dependents as if the field was added for the newly visible scopes");
      processAddedField(context, clsChange, fieldChange.getNow(), future, present);
    }
    else if (removedFlags.isSynthetic()) {
      // the field enters source-level resolution (a synthetic member is unavailable to source code, JLS 13.1):
      // for consumers this is effectively a field ADDITION (hiding/shadowing re-check included)
      debug(context, "Removed synthetic specifier --- affecting dependents as if the field was added");
      processAddedField(context, clsChange, fieldChange.getNow(), future, present);
    }

    if (!diff.typeAnnotations().unchanged()) {
      // type annotations on the field's type may change how consumers read it
      // (e.g. kotlinc derives nullability from JSpecify-style type-use annotations) => re-check use sites
      debug(context, "Type annotations on the field type changed --- affecting field usages");
      affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated);
    }

    if (diff.typeChanged() || diff.signatureChanged()) {
      debug(context, "Type or signature changed --- affecting field usages");
      affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated);
    }
    else if (diff.flagsChanged()) {
      if (addedFlags.isStatic() || removedFlags.isStatic() || addedFlags.isPrivate() || addedFlags.isVolatile() || addedFlags.isSynthetic() || removedFlags.isSynthetic()) {
        // an added synthetic flag hides the field from source-level resolution (JLS 13.1) --- for consumers this is
        // effectively a field REMOVAL, so existing references must be re-checked
        debug(context, "Added/removed static or synthetic modifier or added private/volatile modifier --- affecting field usages");
        affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated);
        if (!changedField.isPrivate()) {
          if (addedFlags.isStatic()) {
            debug(context, "Added static modifier --- affecting static member on-demand import usages");
            affectStaticMemberOnDemandUsages(context, changedClass.getReferenceID(), propagated);
          }
          else if (removedFlags.isStatic()) {
            debug(context, "Removed static modifier --- affecting static field import usages");
            affectStaticMemberImportUsages(context, changedClass.getReferenceID(), changedField.getName(), propagated);
          }
        }
      }
      else {
        Predicate<Node<?, ?>> constraint = null;

        if (removedFlags.isPublic()) {
          debug(context, "Removed public modifier, affecting field usages with package constraint");
          affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated, constraint = new PackageConstraint(changedClass.getPackageName()));
        }
        else if (removedFlags.isProtected() && diff.accessRestricted()){
          debug(context, "Removed protected modifier and the field became less accessible, affecting field usages with package constraint");
          affectMemberUsages(context, changedClass.getReferenceID(), changedField, propagated, constraint = new PackageConstraint(changedClass.getPackageName()));
        }

        if (addedFlags.isFinal()) {
          debug(context, "Added final modifier --- affecting field assign usages");
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
    // a module-info appearance turns an automatic module into an explicit one:
    // packages not listed in 'exports' become concealed for the modules that read this module
    affectDependentModules(context, present, addedModule, true, null);
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

    if (!affectDeps) {
      for (ModuleRequires addedRequires : requiresDiff.added()) {
        if (addedRequires.isTransitive()) {
          // reader modules gain implied readability of the newly required module and may observe new split-package conflicts
          affectDeps = true;
          break;
        }
      }
    }

    for (Difference.Change<ModuleRequires, ModuleRequires.Diff> rChange : requiresDiff.changed()) {
      affectSelf |= rChange.getDiff().versionChanged();
      if (rChange.getDiff().isTransitivePropertyChanged()) {
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

    Predicate<Node<?, ?>> constraint = null;
    if (!affectDeps || !constraintPackageNames.isEmpty()) {
      for (Difference.Change<ModulePackage, ModulePackage.Diff> exportChange : exportsDiff.changed()) {
        if (exportChange.getDiff().becameQualified()) {
          // an unqualified export became qualified: every reader module except the listed export targets loses access to the package
          affectDeps = true;
          Set<String> allowedModules = collect(exportChange.getNow().getModules(), new HashSet<>());
          Predicate<Node<?, ?>> allowedModulesConstraint = node -> node instanceof JvmModule && !allowedModules.contains(((JvmModule) node).getName());
          constraint = constraint != null? constraint.or(allowedModulesConstraint) : allowedModulesConstraint;
        }
        Iterable<String> removedModuleNames = exportChange.getDiff().targetModules().removed();
        affectDeps |= !isEmpty(removedModuleNames);
        if (affectDeps) {
          collect(removedModuleNames, constraintPackageNames);
        }
      }
    }

    if (!affectDeps || !constraintPackageNames.isEmpty() || constraint != null) {
      // skip this check if deps should be affected unconditionally
      if (!isEmpty(exportsDiff.added())) {
        // a newly exported package may collide with packages already visible to reader modules from elsewhere (split packages),
        // so the readers must be re-checked
        affectDeps = true;
        if (isEmpty(filter(exportsDiff.added(), modPackage -> !modPackage.isQualified()))) {
          // all added exports are qualified => only the listed target modules can observe the change
          collect(flat(map(exportsDiff.added(), ModulePackage::getModules)), constraintPackageNames);
        }
        else {
          constraint = null;
          constraintPackageNames.clear();
        }
      }
    }

    if (affectSelf && !change.getNow().isLibrary()) {
      affectModule(context, present, changedModule);
    }

    if (affectDeps) {
      if (!constraintPackageNames.isEmpty()) {
        Predicate<Node<?, ?>> packageConstraint = node -> node instanceof JvmModule && constraintPackageNames.contains(((JvmModule) node).getName());
        constraint = constraint != null? constraint.or(packageConstraint) : packageConstraint;
      }
      affectDependentModules(context, present, changedModule, true, constraint);
    }
    return true;
  }

  @Override
  public boolean processNodesWithErrors(DifferentiateContext context, Iterable<JVMClassNode<?, ?>> nodes, Utils present) {
    for (JvmClass jvmClass : Graph.getNodesOfType(nodes, JvmClass.class)) {
      if (!jvmClass.isPrivate() && jvmClass.isInnerClass()) {
        context.affectUsage(new ClassUsage(jvmClass.getReferenceID()));
        debug(context, "Affect usages of inner class defined in a source compiled with errors", jvmClass.getName());
      }
      for (JvmField field : filter(jvmClass.getFields(), f -> !f.isPrivate() && f.isInlinable() && f.getValue() != null)) {
        if (context.getParams().isProcessConstantsIncrementally()) {
          debug(context, "Potentially inlined field is contained in a source compiled with errors => affecting field usages and static member import usages");
          var propagated = present.collectSubclassesWithoutField(jvmClass.getReferenceID(), field);
          affectMemberUsages(context, jvmClass.getReferenceID(), field, propagated);
          affectStaticMemberImportUsages(context, jvmClass.getReferenceID(), field.getName(), propagated);
        }
        else {
          debug(context, "Potentially inlined field is contained in a source compiled with errors => a switch to non-incremental mode requested");
          if (!affectOnNonIncrementalChange(context, jvmClass.getReferenceID(), field, present)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  /**
   * A class that is added, or whose access is widened, becomes referenceable by its short name in scopes that could not see it before:
   * on-demand importers of its enclosing package/class scope and (for member types) subclasses of its outer class, where the
   * inherited member-type name shadows imported/enclosing-scope types (JLS 6.5.5). Affect the dependents whose existing
   * short-name references can be captured this way.
   */
  private void affectShortNameCapture(DifferentiateContext context, JvmClass cls, Utils present) {
    if (cls.isPrivate() || cls.isAnonymous() || cls.isLocal() || cls.isSynthetic()) {
      return; // the class is not referenceable from other sources
    }
    String shortName = cls.getShortName();
    Predicate<Node<?, ?>> referencesByShortName = new ReferencesByShortNamePredicate(shortName);

    String scope = cls.isInnerClass()? cls.getOuterFqName() : cls.getPackageName();
    debug(context, "Affecting dependencies importing package/class '", scope, "' on-demand and having class-usages with the same short name: '", shortName, "' ");
    context.affectUsage(new ImportPackageOnDemandUsage(scope), referencesByShortName);
    if (cls.isInnerClass()) {
      if (cls.isStatic()) {
        context.affectUsage(new ImportStaticOnDemandUsage(scope), referencesByShortName);
      }
      // additionally check outer class' subclasses, where the member class can be referenced by its short name;
      // the reference may be made from any class lexically nested in a subclass (a separate node), so inspect all classes sharing the subclass' sources
      for (NodeSource src : unique(flat(map(present.allSubclasses(new JvmNodeReferenceID(cls.getOuterFqName())), present::getNodeSources)))) {
        if (find(context.getGraph().getNodes(src, JvmClass.class), referencesByShortName) != null) {
          affectSources(context, asIterable(src), "Affecting dependencies across class hierarchy having class-usages with the same short name " + shortName, false);
        }
      }
    }
  }

  private void affectUsagesOfLessAccessibleClass(DifferentiateContext context, JvmClass lessAccessibleClass, @Nullable Predicate<Node<?, ?>> constraint) {
    JvmNodeReferenceID lessAccessibleClassID = lessAccessibleClass.getReferenceID();
    if (constraint != null) {
      context.affectUsage(new ClassUsage(lessAccessibleClassID), constraint);
    }
    else {
      context.affectUsage(new ClassUsage(lessAccessibleClassID));
    }
    debug(context, "Affecting dependents holding any usage owned by the access-narrowed class ", lessAccessibleClassID.getNodeName());
    context.affectUsage(asIterable(lessAccessibleClassID), node -> {
      if (constraint != null && !constraint.test(node)) {
        return false;
      }
      // affect any other kind of usage of the class
      return find(node.getUsages(), usage -> !(usage instanceof LookupNameUsage) && lessAccessibleClassID.equals(usage.getElementOwner())) != null;
    });
  }

  private boolean affectOnNonIncrementalChange(DifferentiateContext context, JvmNodeReferenceID owner, Proto proto, Utils utils) {
    if (proto.isPublic()) {
      debug(context, "Public access, switching to a non-incremental mode");
      return false;
    }

    if (proto.isProtected()) {
      debug(context, "Protected access, softening non-incremental decision: adding all relevant subclasses for a recompilation");
      debug(context, "Root class: ", owner);
      for (ReferenceID id : proto instanceof JvmField? utils.collectSubclassesWithoutField(owner, ((JvmField)proto)) : utils.allSubclasses(owner)) {
        affectNodeSources(context, id, "Adding ", utils);
      }
    }

    String packageName = JvmClass.getPackageName(owner.getNodeName());
    debug(context, "Softening non-incremental decision: adding all package classes for a recompilation");
    debug(context, "Package name: ", packageName);
    for (ReferenceID nodeWithinPackage : filter(context.getGraph().getRegisteredNodes(), id -> id instanceof JvmNodeReferenceID && packageName.equals(JvmClass.getPackageName(((JvmNodeReferenceID)id).getNodeName())))) {
      affectNodeSources(context, nodeWithinPackage, "Adding ", utils);
    }
    
    return true;
  }

  private void affectModule(DifferentiateContext context, Utils utils, JvmModule mod) {
    debug(context, "Affecting module ", mod.getName());
    for (NodeSource source : utils.getNodeSources(mod.getReferenceID())) {
      context.affectNodeSource(source);
      debug(context, "Affected source ", source);
    }
  }

  public void affectDependentModules(DifferentiateContext context, Utils utils, JvmModule fromModule, boolean checkTransitive, @Nullable Predicate<Node<?, ?>> constraint) {
    Iterable<JvmModule> dependent = !checkTransitive? Collections.emptyList() : recurseDepth(
      fromModule,
      mod -> filter(flat(map(context.getGraph().getDependingNodes(mod.getReferenceID()), id -> utils.getNodes(id, JvmModule.class))), m -> m.requiresTransitively(mod.getName())),
      false
    );

    for (JvmModule mod : flat(asIterable(fromModule), dependent)) {
      debug(context, "Affecting modules depending on module ", mod.getName());
      ModuleUsage usage = new ModuleUsage(mod.getReferenceID());
      if (constraint != null) {
        context.affectUsage(usage, constraint);
      }
      else {
        context.affectUsage(usage);
      }
    }
  }

  private static class ReferencesByShortNamePredicate implements Predicate<Node<?, ?>> {
    private static final Set<Character> ourNameDelimiters = Set.of('/', '$');
    private final String myShortName;

    ReferencesByShortNamePredicate(String shortName) {
      myShortName = shortName;
    }

    @Override
    public boolean test(Node<?, ?> n) {
      return find(n.getUsages(), u -> {
        if (u instanceof ClassUsage || u instanceof MemberUsage) {
          String ownerName = ((JvmElementUsage) u).getElementOwner().getNodeName();
          if (ownerName.endsWith(myShortName) && (ownerName.length() == myShortName.length() || ourNameDelimiters.contains(ownerName.charAt(ownerName.length() - myShortName.length() - 1)))) {
            return true;
          }
        }
        return false;
      }) != null;
    }
  }
}
