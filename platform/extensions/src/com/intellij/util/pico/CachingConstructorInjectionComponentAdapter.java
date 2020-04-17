// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.pico;

import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoInitializationException;
import org.picocontainer.PicoIntrospectionException;
import org.picocontainer.defaults.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * @deprecated Use {@link com.intellij.openapi.components.ComponentManager#instantiateClassWithConstructorInjection}
 */
@Deprecated
public final class CachingConstructorInjectionComponentAdapter extends AbstractComponentAdapter {
  private static final ThreadLocal<Set<CachingConstructorInjectionComponentAdapter>> ourGuard = new ThreadLocal<>();
  private Object myInstance;

  public CachingConstructorInjectionComponentAdapter(@NotNull Object componentKey, @NotNull Class componentImplementation) {
    super(componentKey, componentImplementation);
  }

  @Override
  public Object getComponentInstance(@NotNull PicoContainer container) {
    Object instance = myInstance;
    if (instance == null) {
      myInstance = instance = instantiateGuarded(container, getComponentImplementation());
    }
    return instance;
  }

  private @NotNull Object instantiateGuarded(@NotNull PicoContainer container, @NotNull Class<?> stackFrame) {
    Set<CachingConstructorInjectionComponentAdapter> currentStack = ourGuard.get();
    if (currentStack == null) {
      ourGuard.set(currentStack = ContainerUtil.newIdentityTroveSet());
    }

    if (currentStack.contains(this)) {
      throw new CyclicDependencyException(stackFrame);
    }

    try {
      currentStack.add(this);
      return doGetComponentInstance(container);
    }
    catch (final CyclicDependencyException e) {
      e.push(stackFrame);
      throw e;
    }
    finally {
      currentStack.remove(this);
    }
  }

  private @NotNull Object doGetComponentInstance(@NotNull PicoContainer guardedContainer) {
    Constructor<?> constructor;
    try {
      constructor = getGreediestSatisfiableConstructor(guardedContainer);
    }
    catch (AmbiguousComponentResolutionException e) {
      e.setComponent(getComponentImplementation());
      throw e;
    }

    try {
      constructor.setAccessible(true);
      if (constructor.getParameterCount() == 0) {
        return constructor.newInstance();
      }
      else {
        Class<?>[] parameterTypes = constructor.getParameterTypes();
        Object[] result = new Object[parameterTypes.length];
        for (int i = 0; i < parameterTypes.length; i++) {
          result[i] = ComponentParameter.DEFAULT.resolveInstance(guardedContainer, this, parameterTypes[i]);
        }
        return constructor.newInstance(result);
      }
    }
    catch (InvocationTargetException e) {
      ExceptionUtil.rethrowUnchecked(e.getTargetException());
      throw new PicoInvocationTargetInitializationException(e.getTargetException());
    }
    catch (InstantiationException e) {
      throw new PicoInitializationException("Should never get here");
    }
    catch (IllegalAccessException e) {
      throw new PicoInitializationException(e);
    }
  }

  private @NotNull Constructor<?> getGreediestSatisfiableConstructor(@NotNull PicoContainer container) throws
                                                                                    PicoIntrospectionException,
                                                                                    AssignabilityRegistrationException {
    final Set<Constructor<?>> conflicts = new HashSet<>();
    final Set<List<Class<?>>> unsatisfiableDependencyTypes = new HashSet<>();
    List<Constructor<?>> sortedMatchingConstructors = getSortedMatchingConstructors();
    Constructor<?> greediestConstructor = null;
    int lastSatisfiableConstructorSize = -1;
    Class<?> unsatisfiedDependencyType = null;
    for (Constructor<?> constructor : sortedMatchingConstructors) {
      if (constructor.isSynthetic() || isNonInjectable(constructor)) {
        continue;
      }

      boolean failedDependency = false;
      Class<?>[] parameterTypes = constructor.getParameterTypes();
      // remember: all constructors with less arguments than the given parameters are filtered out already
      for (Class<?> type : parameterTypes) {
        // check whether this constructor is satisfiable
        if (ComponentParameter.DEFAULT.isResolvable(container, this, type)) {
          continue;
        }
        unsatisfiableDependencyTypes.add(Arrays.asList(parameterTypes));
        unsatisfiedDependencyType = type;
        failedDependency = true;
        break;
      }

      if (greediestConstructor != null && parameterTypes.length != lastSatisfiableConstructorSize) {
        if (conflicts.isEmpty()) {
          // we found our match [aka. greedy and satisfied]
          return greediestConstructor;
        }
        else {
          // fits although not greedy
          conflicts.add(constructor);
        }
      }
      else if (!failedDependency && lastSatisfiableConstructorSize == parameterTypes.length) {
        // satisfied and same size as previous one?
        conflicts.add(constructor);
        conflicts.add(greediestConstructor);
      }
      else if (!failedDependency) {
        greediestConstructor = constructor;
        lastSatisfiableConstructorSize = parameterTypes.length;
      }
    }
    if (!conflicts.isEmpty()) {
      throw new TooManySatisfiableConstructorsException(conflicts);
    }
    if (greediestConstructor == null && !unsatisfiableDependencyTypes.isEmpty()) {
      throw new UnsatisfiableDependenciesException(this, unsatisfiedDependencyType, unsatisfiableDependencyTypes, container);
    }
    if (greediestConstructor == null) {
      // be nice to the user, show all constructors that were filtered out
      final Set<Constructor<?>> nonMatching = ContainerUtil.newHashSet(getConstructors());
      throw new PicoInitializationException("Either do the specified parameters not match any of the following constructors: " +
                                            nonMatching + " or the constructors were not accessible for '" + getComponentImplementation() + "'");
    }
    return greediestConstructor;
  }

  private static boolean isNonInjectable(@NotNull Constructor<?> constructor) {
    for (Annotation o : constructor.getAnnotations()) {
      String name = o.annotationType().getName();
      if ("com.intellij.serviceContainer.NonInjectable".equals(name) || "java.lang.Deprecated".equals(name)) {
        return true;
      }
    }
    return false;
  }

  private List<Constructor<?>> getSortedMatchingConstructors() {
    // filter out all constructors that will definitely not match
    List<Constructor<?>> matchingConstructors = new ArrayList<>(Arrays.asList(getConstructors()));
    // optimize list of constructors moving the longest at the beginning
    matchingConstructors.sort((arg0, arg1) -> arg1.getParameterCount() - arg0.getParameterCount());
    return matchingConstructors;
  }

  private Constructor<?> @NotNull [] getConstructors() {
    return AccessController.doPrivileged((PrivilegedAction<Constructor<?>[]>)() -> getComponentImplementation().getDeclaredConstructors());
  }
}