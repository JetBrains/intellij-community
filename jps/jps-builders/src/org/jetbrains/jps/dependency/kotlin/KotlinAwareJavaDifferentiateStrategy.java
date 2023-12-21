// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.kotlin;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.dependency.DifferentiateContext;
import org.jetbrains.jps.dependency.diff.Difference;
import org.jetbrains.jps.dependency.java.*;
import org.jetbrains.org.objectweb.asm.Type;

import java.util.*;

import static org.jetbrains.jps.javac.Iterators.*;

/**
 * This strategy augments Java strategy with some Kotlin-specific rules. Should be used in projects containing both Java and Kotlin code.
 */
public final class KotlinAwareJavaDifferentiateStrategy extends JvmDifferentiateStrategyImpl {

  @Override
  public boolean processChangedClass(DifferentiateContext context, Difference.Change<JvmClass, JvmClass.Diff> change, Utils future, Utils present) {
    JvmClass changedClass = change.getPast();
    Iterable<JvmMethod> removedMethods = change.getDiff().methods().removed();
    Iterable<JvmField> addedNonPrivateFields = filter(change.getDiff().fields().added(), f -> !f.isPrivate());
    Iterable<JvmField> exposedFields = filter(map(change.getDiff().fields().changed(), ch -> ch.getDiff().accessExpanded()? ch.getPast() : null), Objects::nonNull);
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
        JvmMethod methodWithSAMType = find(depClass.getMethods(), m -> contains(m.getArgTypes(), samType));
        if (methodWithSAMType != null) {
          affectConflictingExtensionMethods(context, depClass, methodWithSAMType, samType, future);
        }
      }
    }

    return true;
  }

  @Override
  public boolean processAddedMethod(DifferentiateContext context, JvmClass changedClass, JvmMethod addedMethod, Utils future, Utils present) {
    // any added method may conflict with an extension method to this class, defined elsewhere
    affectConflictingExtensionMethods(context, changedClass, addedMethod, null, future);
    return true;
  }

  @Override
  public boolean processChangedMethod(DifferentiateContext context, JvmClass changedClass, Difference.Change<JvmMethod, JvmMethod.Diff> change, Utils future, Utils present) {
    if (
      find(change.getDiff().paramAnnotations().removed(), annot -> KotlinMeta.KOTLIN_NULLABLE.equals(annot.type)) != null ||
      find(change.getDiff().annotations().added(), annot -> KotlinMeta.KOTLIN_NULLABLE.equals(annot)) != null) {

      JvmMethod changedMethod = change.getPast();
      debug("One of method's parameters or method's return value has become non-nullable; affecting method usages ", changedMethod);
      affectMemberUsages(context, changedClass.getReferenceID(), changedMethod, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), changedMethod));
    }
    return true;
  }

  private static void affectConflictingExtensionMethods(DifferentiateContext context, JvmClass cls, JvmMethod clsMethod, @Nullable TypeRepr.ClassType samType, Utils utils) {
    if (clsMethod.isPrivate() || clsMethod.isConstructor()) {
      return;
    }
    // the first arg is always the class being extended
    Set<String> firstArgTypes = collect(
      map(flat(utils.allSupertypes(cls.getReferenceID()), utils.collectSubclassesWithoutMethod(cls.getReferenceID(), clsMethod)), id -> id.getNodeName()), new HashSet<>()
    );
    firstArgTypes.add(cls.getName());
    context.affectUsage(map(firstArgTypes, JvmNodeReferenceID::new), (n, u) -> {
      if (!(u instanceof MethodUsage) || !(n instanceof JvmClass)) {
        return false;
      }
      MethodUsage methodUsage = (MethodUsage)u;
      JvmClass contextCls = (JvmClass)n;
      if (firstArgTypes.contains(methodUsage.getElementOwner().getNodeName()) || !Objects.equals(methodUsage.getName(), clsMethod.getName())) {
        return false;
      }
      Type calledMethodType = Type.getType(methodUsage.getDescriptor());
      if (!Objects.equals(clsMethod.getType(), TypeRepr.getType(calledMethodType.getReturnType()))) {
        return false;
      }
      Iterator<TypeRepr> usageArgTypes = map(Arrays.asList(calledMethodType.getArgumentTypes()), TypeRepr::getType).iterator();
      if (!usageArgTypes.hasNext()) {
        return false;
      }
      TypeRepr firstUsageArgType = usageArgTypes.next();
      if (!(firstUsageArgType instanceof TypeRepr.ClassType) || !firstArgTypes.contains(((TypeRepr.ClassType)firstUsageArgType).getJvmName())) {
        return false;
      }
      for (TypeRepr methodArgType : clsMethod.getArgTypes()) {
        if (!usageArgTypes.hasNext()) {
          return false;
        }
        TypeRepr usageArgType = usageArgTypes.next();
        if (samType != null && samType.equals(methodArgType) && !(usageArgType instanceof TypeRepr.ClassType)) {
          return false;
        }
      }
      if (usageArgTypes.hasNext()) {
        return false;
      }
      return utils.isVisibleIn(cls, clsMethod, contextCls);
    });
  }

  private static class PropertyDescriptor{
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
        String propName = methodName.substring(3);
        List<JvmMethod> candidates = setters.get(propName);
        if (candidates == null) {
          setters.put(propName, candidates = new SmartList<>());
        }
        candidates.add(method);
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
}
