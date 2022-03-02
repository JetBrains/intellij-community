// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.pico;

import com.intellij.util.ExceptionUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.ComponentAdapter;
import org.picocontainer.PicoContainer;
import org.picocontainer.PicoInitializationException;
import org.picocontainer.PicoIntrospectionException;
import org.picocontainer.defaults.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * @deprecated Use {@link com.intellij.openapi.components.ComponentManager#instantiateClassWithConstructorInjection}
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
@ApiStatus.ScheduledForRemoval
public final class CachingConstructorInjectionComponentAdapter implements ComponentAdapter {
  private static final ThreadLocal<Set<Class<?>>> ourGuard = new ThreadLocal<>();
  private Object myInstance;

  private final Object key;
  private final Class<?> componentImplementation;

  public CachingConstructorInjectionComponentAdapter(@NotNull Object key, @NotNull Class<?> componentImplementation) {
    this.key = key;
    this.componentImplementation = componentImplementation;
  }

  @Override
  public Object getComponentKey() {
    return key;
  }

  @Override
  public Class<?> getComponentImplementation() {
    return componentImplementation;
  }

  public String toString() {
    return getClass().getName() + "[" + key + "]";
  }

  @Override
  public Object getComponentInstance(@NotNull PicoContainer container) {
    Object instance = myInstance;
    if (instance == null) {
      instance = instantiateGuarded(this, container, getComponentImplementation());
      myInstance = instance;
    }
    return instance;
  }

  public static @NotNull Object instantiateGuarded(@Nullable CachingConstructorInjectionComponentAdapter adapter, @NotNull PicoContainer container, @NotNull Class<?> componentImplementation) {
    Set<Class<?>> currentStack = ourGuard.get();
    if (currentStack == null) {
      currentStack = Collections.newSetFromMap(new IdentityHashMap<>(1));
      ourGuard.set(currentStack);
    }

    if (!currentStack.add(componentImplementation)) {
      throw new CyclicDependencyException(componentImplementation);
    }

    try {
      return doGetComponentInstance(adapter, (DefaultPicoContainer)container, componentImplementation);
    }
    catch (final CyclicDependencyException e) {
      e.push(componentImplementation);
      throw e;
    }
    finally {
      currentStack.remove(componentImplementation);
    }
  }

  private static @NotNull Object doGetComponentInstance(@Nullable ComponentAdapter adapter, @NotNull DefaultPicoContainer container, @NotNull Class<?> componentImplementation) {
    Constructor<?> constructor;
    try {
      constructor = getGreediestSatisfiableConstructor(adapter, container, componentImplementation);
    }
    catch (AmbiguousComponentResolutionException e) {
      e.setComponent(componentImplementation);
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
          // type check is done in isResolvable
          ComponentAdapter componentAdapter = ComponentParameter.resolveAdapter(container, adapter, parameterTypes[i]);
          if (componentAdapter != null) {
            result[i] = container.getComponentInstance(componentAdapter.getComponentKey());
          }
        }
        return constructor.newInstance(result);
      }
    }
    catch (InvocationTargetException e) {
      ExceptionUtilRt.rethrowUnchecked(e.getTargetException());
      throw new PicoInvocationTargetInitializationException(e.getTargetException());
    }
    catch (InstantiationException e) {
      throw new PicoInitializationException("Should never get here");
    }
    catch (IllegalAccessException e) {
      throw new PicoInitializationException(e);
    }
  }

  private static @NotNull Constructor<?> getGreediestSatisfiableConstructor(@Nullable ComponentAdapter adapter,
                                                                            @NotNull DefaultPicoContainer container,
                                                                            @NotNull Class<?> componentImplementation) throws
                                                                                                                       PicoIntrospectionException,
                                                                                                                       AssignabilityRegistrationException {
    Set<Constructor<?>> conflicts = new HashSet<>();
    Set<Class<?>[]> unsatisfiableDependencyTypes = new HashSet<>();
    // filter out all constructors that will definitely not match
    Constructor<?>[] constructors = componentImplementation.getDeclaredConstructors();
    // optimize list of constructors moving the longest at the beginning
    Arrays.sort(constructors, (arg0, arg1) -> arg1.getParameterCount() - arg0.getParameterCount());
    Constructor<?> greediestConstructor = null;
    int lastSatisfiableConstructorSize = -1;
    Class<?> unsatisfiedDependencyType = null;
    for (Constructor<?> constructor : constructors) {
      if (constructor.isSynthetic() || isNonInjectable(constructor)) {
        continue;
      }

      boolean failedDependency = false;
      Class<?>[] parameterTypes = constructor.getParameterTypes();
      // remember: all constructors with less arguments than the given parameters are filtered out already
      for (Class<?> type : parameterTypes) {
        // check whether this constructor is satisfiable
        if (ComponentParameter.resolveAdapter(container, adapter, type) != null) {
          continue;
        }
        unsatisfiableDependencyTypes.add(parameterTypes);
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
      throw new UnsatisfiableDependenciesException(componentImplementation, unsatisfiedDependencyType, unsatisfiableDependencyTypes, container);
    }
    if (greediestConstructor == null) {
      // be nice to the user, show all constructors that were filtered out
      Set<Constructor<?>> nonMatching = new HashSet<>(Arrays.asList(componentImplementation.getDeclaredConstructors()));
      throw new PicoInitializationException("Either do the specified parameters not match any of the following constructors: " +
                                            nonMatching + " or the constructors were not accessible for '" + componentImplementation + "'");
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
}

final class ComponentParameter {
  public static ComponentAdapter resolveAdapter(@NotNull DefaultPicoContainer container, @Nullable ComponentAdapter excludeAdapter, @NotNull Class<?> expectedType) {
    if (excludeAdapter == null) {
      return container.getComponentAdapter(expectedType);
    }

    ComponentAdapter result = getTargetAdapter(container, expectedType, excludeAdapter.getComponentKey());
    return result == null ? null : expectedType.isAssignableFrom(result.getComponentImplementation()) ? result : null;
  }

  private static ComponentAdapter getTargetAdapter(@NotNull DefaultPicoContainer container, Class<?> expectedType, @NotNull Object excludeKey) {
    ComponentAdapter byKey = container.getComponentAdapter(expectedType);
    if (byKey != null && !excludeKey.equals(byKey.getComponentKey())) {
      return byKey;
    }

    List<ComponentAdapter> found = container.getComponentAdaptersOfType(expectedType);
    ComponentAdapter exclude = null;
    for (ComponentAdapter work : found) {
      if (work.getComponentKey().equals(excludeKey)) {
        exclude = work;
      }
    }
    found.remove(exclude);
    if (found.size() == 0) {
      return container.getParent() == null ? null : container.getParent().getComponentAdapterOfType(expectedType);
    }
    else if (found.size() == 1) {
      return found.get(0);
    }
    else {
      Class<?>[] foundClasses = new Class[found.size()];
      for (int i = 0; i < foundClasses.length; i++) {
        foundClasses[i] = found.get(i).getComponentImplementation();
      }
      throw new AmbiguousComponentResolutionException(expectedType, foundClasses);
    }
  }
}