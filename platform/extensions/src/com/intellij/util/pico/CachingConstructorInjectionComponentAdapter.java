// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.pico;

import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.*;
import org.picocontainer.defaults.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;

/**
 * A drop-in replacement of {@link ConstructorInjectionComponentAdapter}
 * The same code (generified and cleaned up) but without constructor caching (hence taking up less memory).
 * This class also inlines instance caching (e.g. it doesn't need to be wrapped in a CachingComponentAdapter).
 */
public class CachingConstructorInjectionComponentAdapter extends InstantiatingComponentAdapter {
  @SuppressWarnings("SSBasedInspection")
  private static final ThreadLocal<Set<CachingConstructorInjectionComponentAdapter>> ourGuard =
    new ThreadLocal<>();
  private Object myInstance;

  public CachingConstructorInjectionComponentAdapter(@NotNull Object componentKey, @NotNull Class componentImplementation, Parameter[] parameters, boolean allowNonPublicClasses) throws AssignabilityRegistrationException, NotConcreteRegistrationException {
    super(componentKey, componentImplementation, parameters, allowNonPublicClasses, DefaultPicoContainer.DEFAULT_DELEGATING_COMPONENT_MONITOR, DefaultPicoContainer.DEFAULT_LIFECYCLE_STRATEGY);
  }

  public CachingConstructorInjectionComponentAdapter(@NotNull Object componentKey, @NotNull Class componentImplementation, Parameter[] parameters) {
    this(componentKey, componentImplementation, parameters, false);
  }

  public CachingConstructorInjectionComponentAdapter(@NotNull Object componentKey, @NotNull Class componentImplementation) throws AssignabilityRegistrationException, NotConcreteRegistrationException {
    this(componentKey, componentImplementation, null);
  }

  @Override
  public Object getComponentInstance(PicoContainer container) throws PicoInitializationException, PicoIntrospectionException,
                                                                     AssignabilityRegistrationException, NotConcreteRegistrationException {
    Object instance = myInstance;
    if (instance == null) {
      myInstance = instance = instantiateGuarded(container, getComponentImplementation());
    }
    return instance;
  }

  @NotNull
  private Object instantiateGuarded(PicoContainer container, Class stackFrame) {
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

  @NotNull
  private Object doGetComponentInstance(PicoContainer guardedContainer) {
    final Constructor constructor;
    try {
      constructor = getGreediestSatisfiableConstructor(guardedContainer);
    }
    catch (AmbiguousComponentResolutionException e) {
      e.setComponent(getComponentImplementation());
      throw e;
    }
    ComponentMonitor componentMonitor = currentMonitor();
    try {
      Object[] parameters = getConstructorArguments(guardedContainer, constructor);
      componentMonitor.instantiating(constructor);
      long startTime = System.currentTimeMillis();
      Object inst = newInstance(constructor, parameters);
      componentMonitor.instantiated(constructor, System.currentTimeMillis() - startTime);
      return inst;
    }
    catch (InvocationTargetException e) {
      componentMonitor.instantiationFailed(constructor, e);
      ExceptionUtil.rethrowUnchecked(e.getTargetException());
      throw new PicoInvocationTargetInitializationException(e.getTargetException());
    }
    catch (InstantiationException e) {
      componentMonitor.instantiationFailed(constructor, e);
      throw new PicoInitializationException("Should never get here");
    }
    catch (IllegalAccessException e) {
      componentMonitor.instantiationFailed(constructor, e);
      throw new PicoInitializationException(e);
    }
  }

  @NotNull
  private Object[] getConstructorArguments(PicoContainer container, @NotNull Constructor ctor) {
    Class[] parameterTypes = ctor.getParameterTypes();
    Object[] result = new Object[parameterTypes.length];
    Parameter[] currentParameters = parameters != null ? parameters : createDefaultParameters(parameterTypes);

    for (int i = 0; i < currentParameters.length; i++) {
      result[i] = currentParameters[i].resolveInstance(container, this, parameterTypes[i]);
    }
    return result;
  }

  @NotNull
  @Override
  protected Constructor getGreediestSatisfiableConstructor(PicoContainer container) throws
                                                                                    PicoIntrospectionException,
                                                                                    AssignabilityRegistrationException, NotConcreteRegistrationException {
    final Set<Constructor> conflicts = new HashSet<>();
    final Set<List<Class>> unsatisfiableDependencyTypes = new HashSet<>();
    List<Constructor> sortedMatchingConstructors = getSortedMatchingConstructors();
    Constructor greediestConstructor = null;
    int lastSatisfiableConstructorSize = -1;
    Class unsatisfiedDependencyType = null;
    for (Constructor constructor : sortedMatchingConstructors) {
      boolean failedDependency = false;
      Class[] parameterTypes = constructor.getParameterTypes();
      Parameter[] currentParameters = parameters != null ? parameters : createDefaultParameters(parameterTypes);

      // remember: all constructors with less arguments than the given parameters are filtered out already
      for (int j = 0; j < currentParameters.length; j++) {
        // check whether this constructor is satisfiable
        if (currentParameters[j].isResolvable(container, this, parameterTypes[j])) {
          continue;
        }
        unsatisfiableDependencyTypes.add(Arrays.asList(parameterTypes));
        unsatisfiedDependencyType = parameterTypes[j];
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
      throw new TooManySatisfiableConstructorsException(getComponentImplementation(), conflicts);
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

  private List<Constructor> getSortedMatchingConstructors() {
    List<Constructor> matchingConstructors = new ArrayList<>();
    // filter out all constructors that will definitely not match
    for (Constructor<?> constructor : getConstructors()) {
      if ((parameters == null || constructor.getParameterTypes().length == parameters.length) &&
          (allowNonPublicClasses || (constructor.getModifiers() & Modifier.PUBLIC) != 0)) {
        matchingConstructors.add(constructor);
      }
    }
    // optimize list of constructors moving the longest at the beginning
    if (parameters == null) {
      matchingConstructors.sort((arg0, arg1) -> arg1.getParameterTypes().length - arg0.getParameterTypes().length);
    }
    return matchingConstructors;
  }

  @NotNull
  private Constructor<?>[] getConstructors() {
    return AccessController.doPrivileged((PrivilegedAction<Constructor<?>[]>)() -> getComponentImplementation().getDeclaredConstructors());
  }
}