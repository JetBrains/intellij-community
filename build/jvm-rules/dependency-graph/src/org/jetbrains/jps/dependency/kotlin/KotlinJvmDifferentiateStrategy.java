// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.kotlin;

import kotlin.metadata.*;
import kotlin.metadata.jvm.JvmExtensionsKt;
import kotlin.metadata.jvm.JvmMethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.*;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.java.*;
import org.jetbrains.jps.util.Pair;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.jetbrains.jps.util.Iterators.*;

/**
 * This strategy augments Java strategy with some Kotlin-specific rules. Should be used in projects containing both Java and Kotlin code.
 */
public final class KotlinJvmDifferentiateStrategy extends JvmDifferentiateStrategyImpl {
  private static final TypeRepr.ClassType JVM_OVERLOADS_ANNOTATION = new TypeRepr.ClassType("kotlin/jvm/JvmOverloads");

  private static final List<AnnotationGroup> ourTrackedAnnotations = List.of(
    AnnotationGroup.of(
      "Nullability annotations",
      EnumSet.of(AnnotationGroup.AnnTarget.field, AnnotationGroup.AnnTarget.method, AnnotationGroup.AnnTarget.method_parameter),
      EnumSet.of(AnnotationGroup.AffectionKind.added, AnnotationGroup.AffectionKind.removed),
      EnumSet.of(AnnotationGroup.AffectionScope.usages),
      Set.of(
        new TypeRepr.ClassType("org/jetbrains/annotations/Nullable"),
        new TypeRepr.ClassType("androidx/annotation/Nullable"),
        new TypeRepr.ClassType("android/support/annotation/Nullable"),
        new TypeRepr.ClassType("android/annotation/Nullable"),
        new TypeRepr.ClassType("com/android/annotations/Nullable"),
        new TypeRepr.ClassType("org/eclipse/jdt/annotation/Nullable"),
        new TypeRepr.ClassType("org/checkerframework/checker/nullness/qual/Nullable"),
        new TypeRepr.ClassType("javax/annotation/Nullable"),
        new TypeRepr.ClassType("javax/annotation/CheckForNull"),
        new TypeRepr.ClassType("edu/umd/cs/findbugs/annotations/CheckForNull"),
        new TypeRepr.ClassType("edu/umd/cs/findbugs/annotations/Nullable"),
        new TypeRepr.ClassType("edu/umd/cs/findbugs/annotations/PossiblyNull"),
        new TypeRepr.ClassType("io/reactivex/annotations/Nullable"),
        new TypeRepr.ClassType("io/reactivex/rxjava3/annotations/Nullable"),

        new TypeRepr.ClassType("javax/annotation/Nonnull"),
        new TypeRepr.ClassType("org/jetbrains/annotations/NotNull"),
        new TypeRepr.ClassType("edu/umd/cs/findbugs/annotations/NonNull"),
        new TypeRepr.ClassType("androidx/annotation/NonNull"),
        new TypeRepr.ClassType("android/support/annotation/NonNull"),
        new TypeRepr.ClassType("android/annotation/NonNull"),
        new TypeRepr.ClassType("com/android/annotations/NonNull"),
        new TypeRepr.ClassType("org/eclipse/jdt/annotation/NonNull"),
        new TypeRepr.ClassType("org/checkerframework/checker/nullness/qual/NonNull"),
        new TypeRepr.ClassType("lombok/NonNull"),
        new TypeRepr.ClassType("io/reactivex/annotations/NonNull"),
        new TypeRepr.ClassType("io/reactivex/rxjava3/annotations/NonNull")
      )
    ),

    AnnotationGroup.of(
      "Deprecation annotations",
      EnumSet.of(AnnotationGroup.AnnTarget.type, AnnotationGroup.AnnTarget.field, AnnotationGroup.AnnTarget.method),
      EnumSet.of(AnnotationGroup.AffectionKind.added, AnnotationGroup.AffectionKind.changed),
      EnumSet.of(AnnotationGroup.AffectionScope.usages),
      Set.of(
        new TypeRepr.ClassType("kotlin/Deprecated"),
        new TypeRepr.ClassType("kotlin/DeprecatedSinceKotlin")
      )
    ),

    AnnotationGroup.of(
      "Kotlin JVM specific annotations",
      EnumSet.of(AnnotationGroup.AnnTarget.type),
      EnumSet.of(AnnotationGroup.AffectionKind.added, AnnotationGroup.AffectionKind.changed),
      EnumSet.of(AnnotationGroup.AffectionScope.usages),
      Set.of(
        new TypeRepr.ClassType("kotlin/jvm/PurelyImplements")
      )
    )
  );

  @Override
  protected Iterable<AnnotationGroup> getTrackedAnnotations() {
    return ourTrackedAnnotations;
  }

  @Override
  public boolean processAddedClasses(DifferentiateContext context, Iterable<JvmClass> addedClasses, Utils future, Utils present) {
    for (JvmNodeReferenceID sealedSuperClass : unique(map(filter(flat(map(filter(addedClasses, cl -> !cl.isLibrary()), future::allDirectSupertypes)), KJvmUtils::isSealed), JVMClassNode::getReferenceID))) {
      affectSealedClass(context, sealedSuperClass, "Subclass of a sealed class was added, affecting ", future, true /*affectUsages*/);
    }
    return super.processAddedClasses(context, addedClasses, future, present);
  }

  @Override
  public boolean processAddedClass(DifferentiateContext context, JvmClass addedClass, Utils future, Utils present) {
    if (!addedClass.isPrivate()) {
      KmDeclarationContainer container = KJvmUtils.getDeclarationContainer(addedClass);
      if (container == null || container instanceof KmClass) {
        // calls to newly added class' constructors may shadow calls to functions named similarly
        debug("Affecting lookup usages for added class ", addedClass.getName());
        affectClassLookupUsages(context, addedClass);

        if (!addedClass.isAnonymous() && !addedClass.isLocal() && !addedClass.isInnerClass()) {
          if (affectConflictingTypeAliasDeclarations(context, addedClass.getReferenceID(), present)) { // if there exists a type alias with the same fq name
            affectSources(context, context.getDelta().getSources(addedClass.getReferenceID()), "Found conflicting type alias declarations", true);
          }
        }
      }
      else {
        debug("Affecting lookup usages for top-level functions properties and type aliases in a newly added file ", addedClass.getName());
        String scopeName = addedClass.getPackageName();
        for (String symbolName : unique(flat(List.of(
          map(filter(container.getFunctions(), f -> !KJvmUtils.isPrivate(f)), KmFunction::getName),
          map(filter(container.getProperties(), p -> !KJvmUtils.isPrivate(p)), KmProperty::getName),
          map(filter(container.getTypeAliases(), ta -> !KJvmUtils.isPrivate(ta)), KmTypeAlias::getName)
        )))) {
          context.affectUsage(new LookupNameUsage(scopeName, symbolName));
          debug("Affect ", "lookup '" + symbolName + "'", " usage owned by node '", addedClass.getName(), "'");
        }

        boolean conflictsFound = false;
        for (KmTypeAlias alias : filter(container.getTypeAliases(), ta -> !KJvmUtils.isPrivate(ta))) {
          JvmNodeReferenceID conflictingNodeId = new JvmNodeReferenceID(scopeName.isBlank() ? alias.getName() : scopeName + "." + alias.getName());

          conflictsFound |= affectConflictingTypeAliasDeclarations(
            context, conflictingNodeId, present
          );

          conflictsFound |= affectNodeSourcesIfNotCompiled(
            context, asIterable(conflictingNodeId), present, "Possible conflict with an equally named class in the same compilation chunk; Scheduling for recompilation sources: "
          );
        }
        if (conflictsFound) {
          affectSources(context, context.getDelta().getSources(addedClass.getReferenceID()), "Found conflicting type alias / class declarations", true);
        }
      }
    }

    return true;
  }

  @Override
  public boolean processRemovedClass(DifferentiateContext context, JvmClass removedClass, Utils future, Utils present) {
    if (!removedClass.isLibrary()) {
      for (JvmClass superClass : filter(future.allDirectSupertypes(removedClass), KJvmUtils::isSealed)) {
        affectSealedClass(context, superClass.getReferenceID(), "Subclass of a sealed class was removed, affecting ", future, true /*affectUsages*/);
      }
    }

    KmDeclarationContainer container = KJvmUtils.getDeclarationContainer(removedClass);

    if (!removedClass.isInnerClass()) {
      // this will affect all imports of this class in kotlin sources
      if ((container == null /*is non-kotlin node*/ && !removedClass.isPrivate()) || (container instanceof KmClass && !KJvmUtils.isPrivate(((KmClass)container)))) {
        debug("Affecting lookup usages for removed class ", removedClass.getName());
        affectClassLookupUsages(context, removedClass);
      }
    }

    if (!removedClass.isPrivate()) {
      Map<JvmNodeReferenceID, Iterable<JvmNodeReferenceID>> cache = new HashMap<>();
      boolean isDeclarationImportable = container instanceof KmPackage || container instanceof KmClass && Attributes.getKind((KmClass)container) == ClassKind.COMPANION_OBJECT;
      for (KmFunction kmFunction : filter(KJvmUtils.allKmFunctions(removedClass), f -> !KJvmUtils.isPrivate(f))) {
        if (isDeclarationImportable || Attributes.isInline(kmFunction)) {
          debug("Function in a removed class was either importable (a top-level one or a companion object member) or inlineable, affecting method usages ", kmFunction.getName());
          affectMemberLookupUsages(context, removedClass, kmFunction.getName(), present, cache);
        }
      }

      for (KmProperty prop : filter(KJvmUtils.allKmProperties(removedClass), p -> !KJvmUtils.isPrivate(p))) {
        if (isDeclarationImportable || KJvmUtils.isInlinable(prop)) {
          debug("Property in a removed class was a constant or had inlineable accessors or was importable (a top-level one or a companion object member), affecting property usages ", prop.getName());
          affectMemberLookupUsages(context, removedClass, prop.getName(), present, cache);
        }
      }
    }
    return true;
  }

  @Override
  public boolean processChangedClass(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    JvmClass.Diff diff = change.getDiff();
    Iterable<JvmMethod> removedMethods = diff.methods().removed();
    Iterable<JvmField> addedNonPrivateFields = filter(diff.fields().added(), f -> !f.isPrivate());
    Iterable<JvmField> exposedFields = filter(map(diff.fields().changed(), ch -> ch.getDiff().accessExpanded()? ch.getPast() : null), Objects::nonNull);

    boolean hierarchyChanged = diff.superClassChanged() || !diff.interfaces().unchanged();
    if (hierarchyChanged) {
      boolean extendsChanged = diff.superClassChanged() && !diff.extendsAdded();
      if (extendsChanged || !isEmpty(diff.interfaces().removed())) {
        debug("Affecting class lookups due to changes in class hierarchy");
        for (JvmClass sub : flat(map(future.withAllSubclasses(change.getNow().getReferenceID()), id -> future.getNodes(id, JvmClass.class)))) {
          affectClassLookupUsages(context, sub);
        }
      }
    }
    
    if (KJvmUtils.isKotlinNode(changedClass)) {
      if (hierarchyChanged && !changedClass.isLibrary()) {
        Difference.Specifier<JvmNodeReferenceID, ?> sealedDiff = Difference.diff(
          map(filter(present.allDirectSupertypes(change.getPast()), KJvmUtils::isSealed), JVMClassNode::getReferenceID),
          map(filter(future.allDirectSupertypes(change.getNow()), KJvmUtils::isSealed), JVMClassNode::getReferenceID)
        );
        for (JvmNodeReferenceID id : sealedDiff.added()) {
          affectSealedClass(context, id, "Subclass of a sealed class was added, affecting ", future, true /*affectUsages*/);
        }
        for (JvmNodeReferenceID id : sealedDiff.removed()) {
          affectSealedClass(context, id, "Subclass of a sealed class was removed, affecting ", future, true /*affectUsages*/);
        }
      }
      if (KJvmUtils.isSealed(change.getNow())) {
        // for sealed classes check if the list of direct subclasses found by the graph is the same as in the class' metadata
        KmClass kmClass = (KmClass)KJvmUtils.getDeclarationContainer(change.getNow());
        assert  kmClass != null;
        Difference.Specifier<String, ?> subclassesDiff = Difference.diff(
          filter(map(future.directSubclasses(changedClass.getReferenceID()), subId -> subId instanceof JvmNodeReferenceID? KJvmUtils.getKotlinName((JvmNodeReferenceID)subId, future) : null), Objects::nonNull),
          map(kmClass.getSealedSubclasses(), name -> name.replace('.', '/'))
        );
        if (!subclassesDiff.unchanged()) {
          affectSealedClass(
            context,
            changedClass.getReferenceID(),
            "Subclasses registered in the metadata of a sealed class differ from the list of subclasses found in the dependency graph. Recompiling the sealed class with its subclasses",
            future, false /*affectUsages*/
          );
        }
      }
      else if (KJvmUtils.isSealed(change.getPast())) { // was sealed, but not anymore
        for (ReferenceID id : present.directSubclasses(changedClass.getReferenceID())) {
          String nodeName = present.getNodeName(id);
          if (nodeName != null) {
            // to track uses in 'when' expressions
            debug("A sealed class is not sealed anymore, affecting its subclass usages ", nodeName);
            context.affectUsage(new ClassUsage(nodeName));
          }
        }
      }
    }

    if (!isEmpty(removedMethods) || !isEmpty(addedNonPrivateFields) || !isEmpty(exposedFields)) {
      for (PropertyDescriptor property : findProperties(changedClass)) {

        // KT-46743 Incremental compilation doesn't process usages of Java property in Kotlin code if getter is removed
        for (JvmMethod removedMethod : removedMethods) {
          if (removedMethod.isSame(property.getter) && property.setter != null) {
            debug("Kotlin interop: a property getter ", removedMethod.getName(), " was removed => affecting usages of corresponding setter ", property.setter.getName());
            affectMemberUsages(context, changedClass.getReferenceID(), property.setter, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), property.setter));
            break;
          }
        }

        // KT-55393 JPS: Java synthetic properties incremental compilation is broken
        for (JvmField field : flat(addedNonPrivateFields, exposedFields)) {
          if (Objects.equals(field.getName(), property.getter.getName()) || property.name.equalsIgnoreCase(field.getName())) {
            debug("Kotlin interop: a non-private field with name ", field.getName(), " was added, or the field became more accessible");
            debug(" => affecting usages of corresponding property getter ", property.getter.getName());
            affectMemberUsages(context, changedClass.getReferenceID(), property.getter, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), property.getter));
            if (property.setter != null) {
              debug(" => affecting usages of corresponding property setter ", property.setter.getName());
              affectMemberUsages(context, changedClass.getReferenceID(), property.setter, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), property.setter));
            }
            break;
          }
        }
      }
    }

    if (!present.isLambdaTarget(change.getPast()) && future.isLambdaTarget(change.getNow())) {
      // should affect lambda instantiations on overloads, because some calls may have become ambiguous
      TypeRepr.ClassType samType = new TypeRepr.ClassType(changedClass.getName());
      for (JvmClass depClass : flat(map(context.getGraph().getDependingNodes(changedClass.getReferenceID()), dep -> present.getNodes(dep, JvmClass.class)))) {
        for (JvmMethod methodWithSAMType : filter(depClass.getMethods(), m -> contains(m.getArgTypes(), samType))) {
          affectConflictingCallExpressions(context, depClass, methodWithSAMType, present, null);
        }
      }
    }

    Map<JvmNodeReferenceID, Iterable<JvmNodeReferenceID>> cache = new HashMap<>();
    for (Difference.Change<KotlinMeta, KotlinMeta.Diff> metaChange : diff.metadata(KotlinMeta.class).changed()) {
      KotlinMeta.Diff metaDiff = metaChange.getDiff();

      if (metaDiff.typeParametersVarianceChanged()) {
        debug("Kotlin class' type parameters' variance changed; affecting class usage ", changedClass.getName());
        affectSubclasses(context, future, change.getNow().getReferenceID(), true);
      }

      if (metaDiff.containerAccessRestricted()) {
        debug("Kotlin class' visibility restricted; affecting class lookup usage ", changedClass.getName());
        affectClassLookupUsages(context, changedClass);
      }

      KmDeclarationContainer container = metaChange.getPast().getDeclarationContainer();

      if (container != null && metaDiff.kindChanged() && metaChange.getPast().isTopLevelDeclarationContainer() != metaChange.getNow().isTopLevelDeclarationContainer()) {
        // => container members were top-level and became class members or vice versa
        debug("Declaration container has changed its kind => affecting lookup usages of containing declarations ", changedClass.getName());
        for (KmFunction f : filter(container.getFunctions(), f -> !KJvmUtils.isPrivate(f))) {
          affectMemberLookupUsages(context, changedClass, f.getName(), present, cache);
        }
        for (KmProperty p : filter(container.getProperties(), p -> !KJvmUtils.isPrivate(p))) {
          affectMemberLookupUsages(context, changedClass, p.getName(), present, cache);
        }
        for (KmTypeAlias ta : filter(container.getTypeAliases(), ta -> !KJvmUtils.isPrivate(ta))) {
          affectMemberLookupUsages(context, changedClass, ta.getName(), present, cache);
        }
      }

      boolean isDeclarationImportable = container instanceof KmPackage || container instanceof KmClass && Attributes.getKind((KmClass)container) == ClassKind.COMPANION_OBJECT;
      for (KmFunction removedFunction : metaDiff.functions().removed()) {
        if (KJvmUtils.isPrivate(removedFunction)) {
          continue;
        }
        if (isDeclarationImportable || Attributes.isInline(removedFunction)) {
          debug("Removed function was either importable (a top-level one or a companion object member) or inlineable, affecting function usages ", removedFunction.getName());
          affectMemberLookupUsages(context, changedClass, removedFunction.getName(), present, cache);
        }

        JvmMethod method = getJvmMethod(change.getNow(), JvmExtensionsKt.getSignature(removedFunction));
        if (method != null) {
          // a function in kotlin code was replaced with a property, but at the bytecode level corresponding methods are preserved
          for (JvmClass subClass : filter(flat(map(future.allSubclasses(changedClass.getReferenceID()), id -> future.getNodes(id, JvmClass.class))), KJvmUtils::isKotlinNode)) {
            if (find(subClass.getMethods(), m -> !m.isPrivate() && method.isSameByJavaRules(m)) != null) {
              affectNodeSources(context, subClass.getReferenceID(), "Kotlin function " + removedFunction.getName() + " has been removed. Affecting corresponding method in subclasses: ", future);
            }
          }
        }
      }

      for (KmFunction func : flat(metaDiff.functions().removed(), metaDiff.functions().added())) {
        if (KJvmUtils.isPrivate(func) || find(func.getValueParameters(), Attributes::getDeclaresDefaultValue) == null) {
          continue;
        }
        debug("Removed or added function declares default values: ", changedClass.getName());
        affectMemberLookupUsages(context, changedClass, func.getName(), future, cache);
      }

      for (Difference.Change<KmFunction, KotlinMeta.KmFunctionsDiff> funChange : metaDiff.functions().changed()) {
        KmFunction changedKmFunction = funChange.getPast();
        if (KJvmUtils.isPrivate(changedKmFunction)) {
          continue;
        }
        KotlinMeta.KmFunctionsDiff funDiff = funChange.getDiff();
        if (funDiff.accessRestricted() || funDiff.becameNullable() || funDiff.argsBecameNotNull() || funDiff.parameterArgumentsChanged()) {
          debug("One of function's parameters or return value has become non-nullable, or the function has become less accessible or type parameter's arguments changed ", changedKmFunction.getName());
          JvmMethod jvmMethod = getJvmMethod(changedClass, JvmExtensionsKt.getSignature(changedKmFunction));
          if (jvmMethod != null) {
            // this will affect all usages from both java and kotlin code
            for (JvmMethod method : withJvmOverloads(changedClass, jvmMethod)) {
              for (Pair<JvmClass, JvmMethod> pair : future.getOverridingMethods(changedClass, method, method::isSameByJavaRules)) {
                affectNodeSources(context, pair.first.getReferenceID(), "Affect class where the function is overridden: ", future);
              }
              affectMemberUsages(context, changedClass.getReferenceID(), method, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), method));
            }
          }
          if (KJvmUtils.isDeclaresDefaultValue(changedKmFunction)) {
            // additionally: functions with default parameters produce several methods in bytecode, so need to affect by lookup usage
            debug("One of method's parameters or method's return value has become non-nullable; or function has become less accessible: ", changedKmFunction.getName());
            affectMemberLookupUsages(context, changedClass, changedKmFunction.getName(), future, cache);
          }
        }
        if (funDiff.receiverParameterChanged() || funDiff.hasDefaultDeclarationChanges()) {
          debug("Function's receiver parameter changed or function has breaking changes in default value declarations: ", changedKmFunction.getName());
          affectMemberLookupUsages(context, changedClass, changedKmFunction.getName(), future, cache);
        }
      }

      for (KmConstructor con : flat(metaDiff.constructors().removed(), metaDiff.constructors().added())) {
        if (KJvmUtils.isPrivate(Attributes.getVisibility(con)) || find(con.getValueParameters(), Attributes::getDeclaresDefaultValue) == null) {
          continue;
        }
        debug("Removed or added constructor declares default values: ", changedClass.getName());
        affectClassLookupUsages(context, changedClass);
      }
      
      for (Difference.Change<KmConstructor, KotlinMeta.KmConstructorsDiff> conChange : metaDiff.constructors().changed()) {
        KmConstructor changedCons = conChange.getPast();
        if (KJvmUtils.isPrivate(Attributes.getVisibility(changedCons))) {
          continue;
        }
        KotlinMeta.KmConstructorsDiff conDiff = conChange.getDiff();
        if (conDiff.argsBecameNotNull() || conDiff.accessRestricted() || conDiff.hasDefaultDeclarationChanges()) {
          debug("Constructor's args became non-nullable; or the constructor has become less accessible or has breaking changes in default value declarations: ", changedClass.getName());
          affectClassLookupUsages(context, changedClass);
        }
      }

      for (KmProperty removedProp : metaDiff.properties().removed()) {
        if (KJvmUtils.isPrivate(removedProp)) {
          continue;
        }
        if (KJvmUtils.isInlinable(removedProp)) {
          debug("Removed property was inlineable, affecting property usages ", removedProp.getName());
          affectMemberLookupUsages(context, changedClass, removedProp.getName(), present, cache);
        }

        List<JvmMethodSignature> propertyAccessors = Arrays.asList(JvmExtensionsKt.getGetterSignature(removedProp), JvmExtensionsKt.getSetterSignature(removedProp));
        List<JvmMethod> accessorMethods = collect(filter(map(propertyAccessors, acc -> acc != null? getJvmMethod(change.getNow(), acc) : null), m -> m != null && !m.isPrivate()), new ArrayList<>());

        if (!accessorMethods.isEmpty()) {
          // property in kotlin code was replaced with a function(s), but at the bytecode level corresponding methods are preserved
          for (JvmClass subClass : filter(flat(map(future.allSubclasses(changedClass.getReferenceID()), id -> future.getNodes(id, JvmClass.class))), KJvmUtils::isKotlinNode)) {
            if (find(subClass.getMethods(), m -> !m.isPrivate() && find(accessorMethods, m::isSameByJavaRules) != null) != null) {
              affectNodeSources(context, subClass.getReferenceID(), "Kotlin property " + removedProp.getName() + " has been removed. Affecting corresponding accessor method(s) in subclasses: ", future);
            }
          }
        }
      }

      for (Difference.Change<KmProperty, KotlinMeta.KmPropertiesDiff> propChange : metaDiff.properties().changed()) {
        KmProperty changedProp = propChange.getPast();
        KotlinMeta.KmPropertiesDiff propDiff = propChange.getDiff();
        if (propDiff.accessRestricted() || propDiff.customAccessorAdded()) {
          debug("A property has become less accessible or got custom accessors; affecting its lookup usages ", changedProp.getName());
          affectMemberLookupUsages(context, changedClass, changedProp.getName(), future, cache);
        }

        if (!KJvmUtils.isPrivate(Attributes.getVisibility(changedProp.getGetter()))) {
          if (propDiff.becameNullable() || propDiff.getterAccessRestricted()) {
            JvmMethod getter = getJvmMethod(changedClass, JvmExtensionsKt.getGetterSignature(changedProp));
            if (getter != null && !getter.getFlags().isPrivate()) {
              debug("A property has become nullable or its getter has become less accessible; affecting getter usages ", getter);
              affectMemberUsages(context, changedClass.getReferenceID(), getter, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), getter));
            }
          }
        }

        KmPropertyAccessorAttributes propSetter = changedProp.getSetter();
        if (propSetter != null && !KJvmUtils.isPrivate(Attributes.getVisibility(propSetter))) {
          if (propDiff.becameNotNull() || propDiff.setterAccessRestricted()) {
            JvmMethod setter = getJvmMethod(changedClass, JvmExtensionsKt.getSetterSignature(changedProp));
            if (setter != null) {
              debug("A property has become not-null or its setter has become less accessible; affecting setter usages ", setter);
              affectMemberUsages(context, changedClass.getReferenceID(), setter, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), setter));
            }
          }
        }
      }

      for (KmTypeAlias alias : filter(metaDiff.typeAliases().removed(), ta -> !KJvmUtils.isPrivate(ta))) {
        debug("A type alias declaration was removed; affecting lookup usages ", alias.getName());
        affectMemberLookupUsages(context, changedClass, alias.getName(), future, cache);
      }

      boolean conflictsFound = false;
      for (KmTypeAlias alias : filter(metaDiff.typeAliases().added(), ta -> !KJvmUtils.isPrivate(ta))) {
        debug("A type alias declaration was added; affecting lookup usages ", alias.getName());
        affectMemberLookupUsages(context, changedClass, alias.getName(), future, cache);

        String scopeName = changedClass.getPackageName();
        JvmNodeReferenceID conflictingNodeId = new JvmNodeReferenceID(scopeName.isBlank() ? alias.getName() : scopeName + "." + alias.getName());
        conflictsFound |= affectConflictingTypeAliasDeclarations(
          context, conflictingNodeId, present
        );
        conflictsFound |= affectNodeSourcesIfNotCompiled(
          context, asIterable(conflictingNodeId), present, "Possible conflict with an equally named class in the same compilation chunk; Scheduling for recompilation sources: "
        );
      }
      if (conflictsFound) {
        affectSources(context, context.getDelta().getSources(changedClass.getReferenceID()), "Found conflicting type alias declarations", true);
      }

      for (Difference.Change<KmTypeAlias, KotlinMeta.KmTypeAliasDiff> aChange : metaDiff.typeAliases().changed()) {
        KotlinMeta.KmTypeAliasDiff aDiff = aChange.getDiff();
        if (aDiff.accessRestricted() || aDiff.underlyingTypeChanged()) {
          KmTypeAlias changedAlias = aChange.getPast();
          debug("A type alias declaration has access restricted or underlying type has changed; affecting lookup usages ", changedAlias.getName());
          affectMemberLookupUsages(context, changedClass, changedAlias.getName(), future, cache);
        }
      }

    }

    return super.processChangedClass(context, change, future, present);
  }

  @Override
  public boolean processAddedMethod(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, JvmMethod addedMethod, Utils future, Utils present) {
    JvmClass changedClass = change.getNow();

    // any added method may conflict with an extension method to this class, defined elsewhere
    MethodUsage addedMethodUsage = addedMethod.createUsage(changedClass.getReferenceID());
    // Do not affect nodes that already use this method. Since the method is just added, already existing usage in some node means the node has been already compiled against the most recent version of this class
    affectConflictingCallExpressions(context, changedClass, addedMethod, future, n -> !contains(n.getUsages(), addedMethodUsage));

    if (!changedClass.isPrivate() && "invoke".equals(addedMethod.getName())) {
      KmFunction kmFunction = getKmFunction(changedClass, addedMethod);
      if (kmFunction != null && Attributes.isOperator(kmFunction)) {
        debug("Operator method invoke() has been added. Affecting classes instantiations '", changedClass.getName());
        context.affectUsage(new ClassNewUsage(changedClass.getReferenceID()), KJvmUtils::isKotlinNode);
      }
    }

    return true;
  }

  @Override
  public boolean processChangedMethod(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmMethod, JvmMethod.Diff> methodChange, Utils future, Utils present) {
    JvmClass changedClass = clsChange.getPast();
    JvmMethod changedMethod = methodChange.getPast();

    if (!changedMethod.isPrivate() && methodChange.getDiff().valueChanged()) {
      String name = KJvmUtils.getMethodKotlinName(changedClass, changedMethod);
      debug("Function was inlineable, or has become inlineable or a body of inline method has changed; affecting method usages ", name);
      affectMemberLookupUsages(context, changedClass, name, future, null);
    }
    return super.processChangedMethod(context, clsChange, methodChange, future, present);
  }

  @Override
  public boolean processRemovedField(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, JvmField removedField, Utils future, Utils present) {
    if (!removedField.isPrivate() && removedField.isInlinable() && removedField.getValue() != null) {
      debug("Field had value and was (non-private) final; affecting usages in Kotlin sources ");
      JvmClass changedClass = change.getPast();
      affectLookupUsages(
        context,
        flat(asIterable(changedClass.getReferenceID()), present.collectSubclassesWithoutField(changedClass.getReferenceID(), removedField)),
        removedField.getName(),
        present,
        null
      );
    }

    return true;
  }

  @Override
  public boolean processChangedField(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmField, JvmField.Diff> fieldChange, Utils future, Utils present) {
    JvmClass changedClass = clsChange.getPast();
    JvmField changedField = fieldChange.getPast();
    if (!changedField.isPrivate() && changedField.isInlinable() && changedField.getValue() != null) { // if the field was a compile-time constant
      JvmField.Diff diff = fieldChange.getDiff();
      if (diff.valueChanged() || diff.accessRestricted() || find(List.of(diff.getAddedFlags(), diff.getRemovedFlags()), f -> f.isStatic() || f.isFinal()) != null) {
        debug("Potentially inlined field changed its access or value; affecting usages in Kotlin sources ");
        affectLookupUsages(
          context,
          flat(asIterable(changedClass.getReferenceID()), present.collectSubclassesWithoutField(changedClass.getReferenceID(), changedField)),
          changedField.getName(),
          present,
          null
        );
      }
    }

    return super.processChangedField(context, clsChange, fieldChange, future, present);
  }

  @Override
  public boolean processNodesWithErrors(DifferentiateContext context, Iterable<JVMClassNode<?, ?>> nodes, Utils present) {
    Map<JvmNodeReferenceID, Iterable<JvmNodeReferenceID>> cache = new HashMap<>();
    for (JvmClass jvmClass : Graph.getNodesOfType(nodes, JvmClass.class)) {

      // affect lookups on field constants
      for (JvmField field : filter(jvmClass.getFields(), f -> !f.isPrivate() && f.isInlinable() && f.getValue() != null)) {
        debug("Potentially inlined field is contained in a source compiled with errors; affecting lookup usages in Kotlin sources ");
        affectLookupUsages(
          context,
          flat(asIterable(jvmClass.getReferenceID()), present.collectSubclassesWithoutField(jvmClass.getReferenceID(), field)),
          field.getName(),
          present,
          null
        );
      }
      
      // affect lookups on methods
      for (JvmMethod method : filter(jvmClass.getMethods(), m -> !m.isPrivate() && m.getValue() != null)) {
        String name = KJvmUtils.getMethodKotlinName(jvmClass, method);
        debug("Inlinable function is contained in a source compiled with errors; affecting lookup usages in Kotlin sources ", name);
        affectMemberLookupUsages(context, jvmClass, name, present, cache);
      }

      // in case this is a Kotlin container, affect lookups in Kotlin terms
      
      for (KmFunction kmFunction : filter(KJvmUtils.allKmFunctions(jvmClass), f -> !KJvmUtils.isPrivate(f))) {
        if (Attributes.isInline(kmFunction)) {
          debug("Inlinable function is contained in a source compiled with errors; affecting lookup usages ", kmFunction.getName());
          affectMemberLookupUsages(context, jvmClass, kmFunction.getName(), present, cache);
        }
      }

      for (KmProperty prop : filter(KJvmUtils.allKmProperties(jvmClass), p -> !KJvmUtils.isPrivate(p))) {
        if (KJvmUtils.isInlinable(prop)) {
          debug("Inlinable property or its accessors is contained in a source compiled with errors; affecting property lookup usages ", prop.getName());
          affectMemberLookupUsages(context, jvmClass, prop.getName(), present, cache);
        }
      }

      KotlinMeta meta = KJvmUtils.getKotlinMeta(jvmClass);
      for (KmTypeAlias alias : filter(meta != null? meta.getKmTypeAliases() : List.of(), a -> !KJvmUtils.isPrivate(Attributes.getVisibility(a)))) {
        debug("A type alias declaration is contained in a source compiled with errors; affecting lookup usages ", alias.getName());
        affectMemberLookupUsages(context, jvmClass, alias.getName(), present, cache);
      }
    }
    
    return true;
  }

  @Override
  protected void affectMethodAnnotationUsages(DifferentiateContext context, Set<AnnotationGroup.AffectionScope> toRecompile, Difference.Change<JvmClass, JvmClass.Diff> clsChange, JvmMethod changedMethod, Utils future, Utils present) {
    super.affectMethodAnnotationUsages(context, toRecompile, clsChange, changedMethod, future, present);
    if (toRecompile.contains(AnnotationGroup.AffectionScope.usages)) {
      JvmClass changedClass = clsChange.getPast();
      affectMemberLookupUsages(context, changedClass, KJvmUtils.getMethodKotlinName(changedClass, changedMethod), present, null);
    }
  }

  @Override
  protected void affectClassAnnotationUsages(DifferentiateContext context, Set<AnnotationGroup.AffectionScope> toRecompile, Difference.Change<JvmClass, JvmClass.Diff> change, Utils future, Utils present) {
    super.affectClassAnnotationUsages(context, toRecompile, change, future, present);
    if (toRecompile.contains(AnnotationGroup.AffectionScope.usages)) {
      affectClassLookupUsages(context, change.getPast());
    }
  }

  private void affectConflictingCallExpressions(DifferentiateContext context, JvmClass cls, JvmMethod clsMethod, Utils utils, @Nullable Predicate<Node<?, ?>> constraint) {
    if (clsMethod.isPrivate() || clsMethod.isStaticInitializer()) {
      return;
    }
    if (clsMethod.isConstructor()) {
      affectClassLookupUsages(context, cls);
    }
    else {
      Set<JvmNodeReferenceID> targets = collect(
        flat(utils.allSupertypes(cls.getReferenceID()), utils.collectSubclassesWithoutMethod(cls.getReferenceID(), clsMethod)), new HashSet<>()
      );
      targets.add(cls.getReferenceID());
      affectLookupUsages(context, targets, KJvmUtils.getMethodKotlinName(cls, clsMethod), utils, constraint);
    }
  }

  private static final class PropertyDescriptor{
    final @NotNull String name;
    final @NotNull JvmMethod getter;
    final @Nullable JvmMethod setter;

    PropertyDescriptor(@NotNull String name, @NotNull JvmMethod getter, @Nullable JvmMethod setter) {
      this.name = name;
      this.getter = getter;
      this.setter = setter;
    }
  }

  private static Iterable<PropertyDescriptor> findProperties(JvmClass cls) {
    Map<String, JvmMethod> getters = new HashMap<>();
    Map<String, List<JvmMethod>> setters = new HashMap<>();
    for (JvmMethod method : cls.getMethods()) {
      String methodName = method.getName();
      if (isGetter(method)) {
        getters.put(methodName.substring(methodName.startsWith("is")? 2 : 3), method);
      }
      else if (isSetter(method)) {
        setters.computeIfAbsent(methodName.substring(3), k -> new ArrayList<>()).add(method);
      }
    }
    return map(getters.entrySet(), e -> {
      String propName = e.getKey();
      JvmMethod getter = e.getValue();
      for (JvmMethod setter : filter(setters.get(propName), s -> Objects.equals(s.getArgTypes().iterator().next(), getter.getType()))) {
        return new PropertyDescriptor(propName, getter, setter);
      }
      return new PropertyDescriptor(propName, getter, null);
    });
  }

  private static boolean isSetter(JvmMethod method) {
    String name = method.getName();
    return name.length() > 3 && name.startsWith("set") && "V".equals(method.getType().getDescriptor()) && sizeEqual(method.getArgTypes(), 1);
  }

  private static boolean isGetter(JvmMethod method) {
    if (!isEmpty(method.getArgTypes())) {
      return false;
    }
    String name = method.getName();
    if (name.length() > 3 && name.startsWith("get")) {
      return true;
    }
    if (name.length() > 2 && name.startsWith("is")) {
      TypeRepr returnType = method.getType();
      return TypeRepr.PrimitiveType.BOOLEAN.equals(returnType) || TypeRepr.ClassType.BOOLEAN.equals(returnType);
    }
    return false;
  }

  private static boolean sizeEqual(Iterable<?> it, int expectedSize) {
    if (it instanceof Collection) {
      return expectedSize == ((Collection<?>)it).size();
    }
    Iterator<?> iterator = it.iterator();
    while (expectedSize-- > 0) {
      if (!iterator.hasNext()) {
        return false;
      }
      iterator.next();
    }
    return !iterator.hasNext();
  }

  private void affectClassLookupUsages(DifferentiateContext context, JvmClass cls) {
    String scope;
    String name;
    KmDeclarationContainer container = KJvmUtils.getDeclarationContainer(cls);
    if (container != null && !(container instanceof KmClass)) {
      return;
    }
    String ktName = container != null? KJvmUtils.getKotlinName(cls) : null;
    if (ktName != null) {
      scope = JvmClass.getPackageName(ktName);
      name = scope.isEmpty()? ktName : ktName.substring(scope.length() + 1);
    }
    else { // not a kotlin-compiled class or a synthetic kotlin class
      scope = cls.isInnerClass()? cls.getOuterFqName().replace('$', '/') : cls.getPackageName();
      name = cls.getShortName();
    }
    affectUsages(context, "lookup '" + name + "'" , asIterable(new JvmNodeReferenceID(scope)), id -> new LookupNameUsage(id, name), null);
  }

  private void affectMemberLookupUsages(DifferentiateContext context, JvmClass cls, String name, Utils utils, @Nullable Map<JvmNodeReferenceID, Iterable<JvmNodeReferenceID>> cache) {
    if (!"<init>".equals(name) && !"<clinit>".equals(name)) { // there should be no lookups on jvm-special names
      Function<JvmNodeReferenceID, Iterable<JvmNodeReferenceID>> mapper =
        clsId -> collect(filter(map(utils.withAllSubclasses(clsId), id -> id instanceof JvmNodeReferenceID? ((JvmNodeReferenceID)id) : null), Objects::nonNull), new HashSet<>());
      affectLookupUsages(context, cache != null? cache.computeIfAbsent(cls.getReferenceID(), mapper) : mapper.apply(cls.getReferenceID()), name, utils, null);
    }
  }

  private void affectLookupUsages(DifferentiateContext context, Iterable<JvmNodeReferenceID> symbolOwners, String symbolName, Utils utils, @Nullable Predicate<Node<?, ?>> constraint) {
    // since '$' is both a valid bytecode name symbol and inner class name separator, for every class name containing '$' use additional classname with '/'
    Iterable<JvmNodeReferenceID> owners = filter(flat(symbolOwners, map(symbolOwners, o -> {
      String original = o.getNodeName();
      String normalized = original.replace('$', '/'); // inner class names on Kotlin lookups level use '/' separators instead of '$'
      return normalized.equals(original)? null : new JvmNodeReferenceID(normalized);
    })), Objects::nonNull);

    affectUsages(context, "lookup '" + symbolName + "'" , owners, id -> {
      String kotlinName = KJvmUtils.getKotlinName(id, utils);
      return new LookupNameUsage(kotlinName != null ? new JvmNodeReferenceID(kotlinName) : id, symbolName);
    }, constraint);
  }

  private void affectSealedClass(DifferentiateContext context, JvmNodeReferenceID sealedClassId, String affectReason, Utils utils, boolean affectSubclassUsages) {
    // for sealed classes all direct subclasses must be affected too
    Set<NodeSource> sourcesToAffect = collect(flat(map(KJvmUtils.withAllSubclassesIfSealed(utils, sealedClassId), utils::getNodeSources)), new HashSet<>());
    if (find(sourcesToAffect, src -> !context.isCompiled(src)) != null) {
      // only affect if at least one file in the set is not yet compiled in this session
      affectSources(context, sourcesToAffect, affectReason, true);
    }

    if (affectSubclassUsages) { // to track uses in 'when' expressions
      for (ReferenceID id : utils.directSubclasses(sealedClassId)) {
        String nodeName = utils.getNodeName(id);
        if (nodeName != null) {
          context.affectUsage(new ClassUsage(nodeName));
        }
      }
    }
  }

  private static KmFunction getKmFunction(JvmClass cls, JvmMethod method) {
    JvmMethodSignature methodSignature = new JvmMethodSignature(method.getName(), method.getDescriptor());
    return find(KJvmUtils.allKmFunctions(cls), f -> methodSignature.equals(JvmExtensionsKt.getSignature(f)));
  }

  private static @Nullable JvmMethod getJvmMethod(JvmClass cls, JvmMethodSignature sig) {
    return sig != null? find(cls.getMethods(), m -> Objects.equals(m.getName(), sig.getName()) && Objects.equals(m.getDescriptor(), sig.getDescriptor())) : null;
  }
  
  private static Iterable<JvmMethod> withJvmOverloads(JvmClass cls, JvmMethod method) {
    return unique(flat(
      asIterable(method),
      filter(cls.getMethods(), m -> Objects.equals(m.getName(), method.getName()) && Objects.equals(m.getType(), method.getType()) && contains(map(m.getAnnotations(), AnnotationInstance::getAnnotationClass), JVM_OVERLOADS_ANNOTATION))
    ));
  }

  private boolean affectConflictingTypeAliasDeclarations(DifferentiateContext context, JvmNodeReferenceID typeAliasId, Utils utils) {
    BackDependencyIndex aliasIndex = Objects.requireNonNull(context.getGraph().getIndex(TypealiasesIndex.NAME));
    return affectNodeSourcesIfNotCompiled(context, aliasIndex.getDependencies(typeAliasId), utils, "Possible conflict with an equally named type alias in the same compilation chunk; Scheduling for recompilation sources: ");
  }

}
