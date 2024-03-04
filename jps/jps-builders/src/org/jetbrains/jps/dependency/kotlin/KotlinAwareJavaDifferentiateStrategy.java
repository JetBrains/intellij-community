// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.kotlin;

import com.intellij.util.SmartList;
import com.intellij.util.containers.SmartHashSet;
import kotlinx.metadata.*;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmMethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.DifferentiateContext;
import org.jetbrains.jps.dependency.Node;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.java.*;

import java.util.*;
import java.util.function.Predicate;

import static org.jetbrains.jps.javac.Iterators.*;

/**
 * This strategy augments Java strategy with some Kotlin-specific rules. Should be used in projects containing both Java and Kotlin code.
 */
public final class KotlinAwareJavaDifferentiateStrategy extends JvmDifferentiateStrategyImpl {

  @Override
  public boolean processAddedClass(DifferentiateContext context, JvmClass addedClass, Utils future, Utils present) {
    for (JvmClass superClass : filter(future.allDirectSupertypes(addedClass), KotlinAwareJavaDifferentiateStrategy::isSealed)) {
      affectNodeSources(context, superClass.getReferenceID(), "Subclass of a sealed class was added, affecting ");
    }

    if (!addedClass.isPrivate()) {
      // calls to newly added class' constructors may shadow calls to functions named similarly
      String ktName = getKotlinName(addedClass);
      affectLookupUsages(context, asIterable(new JvmNodeReferenceID(addedClass.getPackageName())), ktName != null? JvmClass.getShortName(ktName) : addedClass.getShortName(), future, null);
    }

    return super.processAddedClass(context, addedClass, future, present);
  }

  @Override
  public boolean processRemovedClass(DifferentiateContext context, JvmClass removedClass, Utils future, Utils present) {
    for (JvmClass superClass : filter(future.allDirectSupertypes(removedClass), KotlinAwareJavaDifferentiateStrategy::isSealed)) {
      affectNodeSources(context, superClass.getReferenceID(), "Subclass of a sealed class was removed, affecting ");
    }
    return super.processRemovedClass(context, removedClass, future, present);
  }

  @Override
  public boolean processChangedClass(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    JvmClass.Diff diff = change.getDiff();
    Iterable<JvmMethod> removedMethods = diff.methods().removed();
    Iterable<JvmField> addedNonPrivateFields = filter(diff.fields().added(), f -> !f.isPrivate());
    Iterable<JvmField> exposedFields = filter(map(diff.fields().changed(), ch -> ch.getDiff().accessExpanded()? ch.getPast() : null), Objects::nonNull);

    if (isKotlinNode(changedClass) && (diff.superClassChanged() || !diff.interfaces().unchanged())) {
      Difference.Specifier<JvmNodeReferenceID, ?> sealedDiff = Difference.diff(
        map(filter(present.allDirectSupertypes(change.getPast()), KotlinAwareJavaDifferentiateStrategy::isSealed), JVMClassNode::getReferenceID),
        map(filter(future.allDirectSupertypes(change.getNow()), KotlinAwareJavaDifferentiateStrategy::isSealed), JVMClassNode::getReferenceID)
      );
      for (JvmNodeReferenceID id : sealedDiff.added()) {
        affectNodeSources(context, id, "Subclass of a sealed class was added, affecting ");
      }
      for (JvmNodeReferenceID id : sealedDiff.removed()) {
        affectNodeSources(context, id, "Subclass of a sealed class was removed, affecting ");
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

    for (Difference.Change<KotlinMeta, KotlinMeta.Diff> metaChange : diff.metadata(KotlinMeta.class).changed()) {
      KotlinMeta.Diff metaDiff = metaChange.getDiff();

      for (Difference.Change<KmFunction, KotlinMeta.KmFunctionsDiff> funChange : metaDiff.functions().changed()) {
        KmFunction changedKmFunction = funChange.getPast();
        Visibility visibility = Attributes.getVisibility(changedKmFunction);
        if (visibility == Visibility.PRIVATE || visibility == Visibility.PRIVATE_TO_THIS) {
          continue;
        }
        KotlinMeta.KmFunctionsDiff funDiff = funChange.getDiff();
        if (funDiff.becameNullable() || funDiff.argsBecameNotNull() || funDiff.receiverParameterChanged()) {
          JvmMethod jvmMethod = getJvmMethod(changedClass, JvmExtensionsKt.getSignature(changedKmFunction));
          if (!isDeclaresDefaultValue(changedKmFunction) && jvmMethod != null) {
            debug("One of method's parameters or method's return value has become non-nullable; or function's receiver parameter changed: affecting method usages ", changedKmFunction.getName());
            affectMemberUsages(context, changedClass.getReferenceID(), jvmMethod, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), jvmMethod));
          }
          else {
            // functions with default parameters produce several methods in bytecode, so need to affect by lookup usage
            debug("One of method's parameters or method's return value has become non-nullable; or function's receiver parameter changed: ", changedKmFunction.getName());
            affectLookupUsages(context, filter(map(future.withAllSubclasses(changedClass.getReferenceID()), id -> id instanceof JvmNodeReferenceID? ((JvmNodeReferenceID)id) : null), Objects::nonNull), changedKmFunction.getName(), future, null);
          }
        }
      }

      for (Difference.Change<KmProperty, KotlinMeta.KmPropertiesDiff> propChange : metaDiff.properties().changed()) {
        KmProperty prop = propChange.getPast();
        KotlinMeta.KmPropertiesDiff propDiff = propChange.getDiff();
        if (propDiff.becameNullable()) {
          JvmMethod getter = getJvmMethod(changedClass, JvmExtensionsKt.getGetterSignature(prop));
          if (getter != null && !getter.getFlags().isPrivate()) {
            debug("A property has become nullable; affecting getter usages ", getter);
            affectMemberUsages(context, changedClass.getReferenceID(), getter, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), getter));
          }
        }
        else if (propDiff.becameNotNull()) {
          JvmMethod setter = getJvmMethod(changedClass, JvmExtensionsKt.getSetterSignature(prop));
          if (setter != null && !setter.getFlags().isPrivate()) {
            debug("A property has become not-null; affecting setter usages ", setter);
            affectMemberUsages(context, changedClass.getReferenceID(), setter, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), setter));
          }
        }
      }
    }

    return true;
  }

  private static boolean isSealed(JvmClass cls) {
    KmDeclarationContainer container = getDeclarationContainer(cls);
    return container instanceof KmClass && Attributes.getModality(((KmClass)container)) == Modality.SEALED;
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
        context.affectUsage(new ClassNewUsage(changedClass.getReferenceID()), n -> isKotlinNode(n));
      }
    }

    return true;
  }

  @Override
  public boolean processChangedMethod(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> clsChange, Difference.Change<JvmMethod, JvmMethod.Diff> methodChange, Utils future, Utils present) {
    JvmClass changedClass = clsChange.getPast();
    JvmMethod changedMethod = methodChange.getPast();
    JvmNodeReferenceID clsId = changedClass.getReferenceID();

    if (methodChange.getDiff().valueChanged()) {
      KmFunction kmFunction = getKmFunction(changedClass, changedMethod);
      if (kmFunction != null) {
        debug("Method was inlineable, or has become inlineable or a body of inline method has changed; affecting method usages ", changedMethod);
        affectLookupUsages(context, flat(asIterable(clsId), future.collectSubclassesWithoutMethod(clsId, changedMethod)), kmFunction.getName(), future, null);
      }
    }
    return true;
  }

  private void affectConflictingCallExpressions(DifferentiateContext context, JvmClass cls, JvmMethod clsMethod, Utils utils, @Nullable Predicate<Node<?, ?>> constraint) {
    if (clsMethod.isPrivate()) {
      return;
    }
    Set<JvmNodeReferenceID> targets = collect(
      flat(utils.allSupertypes(cls.getReferenceID()), utils.collectSubclassesWithoutMethod(cls.getReferenceID(), clsMethod)), new SmartHashSet<>()
    );
    targets.add(cls.getReferenceID());
    affectLookupUsages(context, targets, getMethodKotlinName(cls, clsMethod), utils, constraint);
  }

  private static final class PropertyDescriptor{
    @NotNull
    final String name;
    @NotNull
    final JvmMethod getter;
    @Nullable
    final JvmMethod setter;

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
        setters.computeIfAbsent(methodName.substring(3), k -> new SmartList<>()).add(method);
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

  private void affectLookupUsages(DifferentiateContext context, Iterable<JvmNodeReferenceID> symbolOwners, String symbolName, Utils utils, @Nullable Predicate<Node<?, ?>> constraint) {
    affectUsages(context, "lookup '" + symbolName + "'" , symbolOwners, id -> {
      String kotlinName = getKotlinName(id, utils);
      return new LookupNameUsage(kotlinName != null ? new JvmNodeReferenceID(kotlinName) : id, symbolName);
    }, constraint);
  }

  private static KmFunction getKmFunction(JvmClass cls, JvmMethod method) {
    JvmMethodSignature methodSignature = new JvmMethodSignature(method.getName(), method.getDescriptor());
    return find(allKmFunctions(cls), f -> methodSignature.equals(JvmExtensionsKt.getSignature(f)));
  }

  private static @Nullable JvmMethod getJvmMethod(JvmClass cls, JvmMethodSignature sig) {
    return sig != null? find(cls.getMethods(), m -> Objects.equals(m.getName(), sig.getName()) && Objects.equals(m.getDescriptor(), sig.getDescriptor())) : null;
  }

  private static Iterable<KmFunction> allKmFunctions(Node<?, ?> node) {
    KotlinMeta meta = getKotlinMeta(node);
    return meta != null? meta.getKmFunctions() : Collections.emptyList();
  }

  @Nullable
  private static String getKotlinName(JvmNodeReferenceID cls, Utils utils) {
    return find(map(utils.getNodes(cls, JvmClass.class), c -> getKotlinName(c)), Objects::nonNull);
  }

  @Nullable
  private static String getKotlinName(JvmClass cls) {
    KmDeclarationContainer container = getDeclarationContainer(cls);
    if (container instanceof KmPackage) {
      return cls.getPackageName();
    }
    if (container instanceof KmClass) {
      return ((KmClass)container).getName();
    }
    return null;
  }

  private static String getMethodKotlinName(JvmClass cls, JvmMethod method) {
    KmFunction kmFunction = getKmFunction(cls, method);
    return kmFunction != null? kmFunction.getName() : method.getName();
  }

  private static boolean isDeclaresDefaultValue(KmFunction f) {
    return find(f.getValueParameters(), Attributes::getDeclaresDefaultValue) != null;
  }

  private static KmDeclarationContainer getDeclarationContainer(Node<?, ?> node) {
    KotlinMeta meta = getKotlinMeta(node);
    return meta != null? meta.getDeclarationContainer() : null;
  }

  private static boolean isKotlinNode(Node<?, ?> node) {
    return getKotlinMeta(node) != null;
  }

  private static @Nullable KotlinMeta getKotlinMeta(Node<?, ?> node) {
    return node instanceof JVMClassNode? (KotlinMeta)find(((JVMClassNode<?, ?>)node).getMetadata(), mt -> mt instanceof KotlinMeta) : null;
  }

}
