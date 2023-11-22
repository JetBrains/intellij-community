// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.kotlin;

import org.jetbrains.jps.dependency.DifferentiateContext;
import org.jetbrains.jps.dependency.java.JavaDifferentiateStrategy;
import org.jetbrains.jps.dependency.java.JvmClass;
import org.jetbrains.jps.dependency.java.JvmMethod;
import org.jetbrains.jps.dependency.java.Utils;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;

import static org.jetbrains.jps.javac.Iterators.filter;
import static org.jetbrains.jps.javac.Iterators.isEmpty;

/**
 * This strategy augments Java strategy with some Kotlin-specific rules. Should be used in projects containing both Java and Kotlin code.
 */
public final class KotlinAwareJavaDifferentiateStrategy extends JavaDifferentiateStrategy {

  @Override
  protected boolean processRemovedMethod(DifferentiateContext context, JvmClass changedClass, JvmMethod removedMethod, Utils future, Utils present) {
    if (isGetter(removedMethod)) {
      // KT-46743 Incremental compilation doesn't process usages of Java property in Kotlin code if getter is removed
      // affect corresponding setter if exists
      String name = removedMethod.getName();
      String setterName = "set" + name.substring(name.startsWith("get")? 3 : 2);
      for (JvmMethod setter : filter(changedClass.getMethods(), m -> isSetter(m) && setterName.equals(m.getName())) ) {
        if (Objects.equals(setter.getArgTypes().iterator().next(), removedMethod.getType())) {
          debug("Kotlin interop: a property getter ", removedMethod.getName(), " was removed => affecting usages of corresponding setter ", setter.getName());
          affectMemberUsages(context, changedClass.getReferenceID(), setter, future.collectSubclassesWithoutMethod(changedClass.getReferenceID(), setter));
          break;
        }
      }
    }
    return true;
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
      String returnType = method.getType().getDescriptor();
      return "Z".equals(returnType) || "Ljava/lang/Boolean;".equals(returnType);
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
