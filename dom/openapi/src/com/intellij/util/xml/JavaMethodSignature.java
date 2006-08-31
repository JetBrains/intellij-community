/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Pair;
import com.intellij.util.SmartList;
import com.intellij.util.ReflectionCache;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author peter
 */
public class JavaMethodSignature {
  private static final Map<Method, JavaMethodSignature> ourSignatures = new THashMap<Method, JavaMethodSignature>();
  private static final Map<Pair<String, Class[]>, JavaMethodSignature> ourSignatures2 = new THashMap<Pair<String, Class[]>, JavaMethodSignature>();
  private final String myMethodName;
  private final Class[] myMethodParameters;
  private final Set<Class> myKnownClasses = new THashSet<Class>();
  private final List<Method> myAllMethods = new SmartList<Method>();
  private final Map<Class,Method> myMethods = new THashMap<Class, Method>();

  private JavaMethodSignature(final String methodName, final Class[] methodParameters) {
    myMethodName = methodName;
    myMethodParameters = methodParameters;
  }

  public String getMethodName() {
    return myMethodName;
  }

  public Class[] getParameterTypes() {
    return myMethodParameters;
  }

  public final Object invoke(final Object instance, final Object... args) throws IllegalAccessException, InvocationTargetException {
    final Class<? extends Object> aClass = instance.getClass();
    final Method method = findMethod(aClass);
    assert method != null : "No method " + this + " in " + aClass;
    return method.invoke(instance, args);
  }

  @Nullable
  public final synchronized Method findMethod(final Class aClass) {
    if (myMethods.containsKey(aClass)) {
      return myMethods.get(aClass);
    }
    Method method = getDeclaredMethod(aClass);
    if (method == null && ReflectionCache.isInterface(aClass)) {
      method = getDeclaredMethod(Object.class);
    }
    myMethods.put(aClass, method);
    return method;
  }

  private void addMethodsIfNeeded(final Class aClass) {
    if (!myKnownClasses.contains(aClass)) {
      addMethodWithSupers(aClass, findMethod(aClass));
    }
  }

  @Nullable
  private Method getDeclaredMethod(final Class aClass) {
    try {
      return aClass.getMethod(myMethodName, myMethodParameters);
    }
    catch (NoSuchMethodException e) {
      try {
        return aClass.getDeclaredMethod(myMethodName, myMethodParameters);
      }
      catch (NoSuchMethodException e1) {
        return null;
      }
    }
  }

  private void addMethodWithSupers(final Class aClass, final Method method) {
    myKnownClasses.add(aClass);
    if (method != null) {
      myAllMethods.add(method);
    }
    final Class superClass = aClass.getSuperclass();
    if (superClass != null) {
      addMethodsIfNeeded(superClass);
    } else {
      if (aClass.isInterface()) {
        addMethodsIfNeeded(Object.class);
      }
    }
    for (final Class anInterface : aClass.getInterfaces()) {
      addMethodsIfNeeded(anInterface);
    }
  }

  @Nullable
  public final synchronized <T extends Annotation> T findAnnotation(final Class<T> annotationClass, final Class startFrom) {
    addMethodsIfNeeded(startFrom);
    //noinspection ForLoopReplaceableByForEach
    final int size = myAllMethods.size();
    for (int i = 0; i < size; i++) {
      Method method = myAllMethods.get(i);
      final T annotation = method.getAnnotation(annotationClass);
      if (annotation != null && ReflectionCache.isAssignable(method.getDeclaringClass(), startFrom)) {
        return annotation;
      }
    }
    return null;
  }

  @Nullable
  public final synchronized <T extends Annotation> Method findAnnotatedMethod(final Class<T> annotationClass, final Class startFrom) {
    addMethodsIfNeeded(startFrom);
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myAllMethods.size(); i++) {
      Method method = myAllMethods.get(i);
      final T annotation = method.getAnnotation(annotationClass);
      if (annotation != null && ReflectionCache.isAssignable(method.getDeclaringClass(), startFrom)) {
        return method;
      }
    }
    return null;
  }

  public String toString() {
    return myMethodName + Arrays.asList(myMethodParameters);
  }

  public static JavaMethodSignature getSignature(Method method) {
    JavaMethodSignature methodSignature;
    synchronized (ourSignatures) {
      methodSignature = ourSignatures.get(method);
      if (methodSignature == null) {
        ourSignatures.put(method, methodSignature = _getSignature(method.getName(), method.getParameterTypes()));
      }
      //methodSignature.addKnownMethod(method);
    }
    return methodSignature;
  }

  public static JavaMethodSignature getSignature(final String name, final Class<?>... parameterTypes) {
    synchronized (ourSignatures) {
      return _getSignature(name, parameterTypes);
    }
  }

  private static JavaMethodSignature _getSignature(final String name, final Class<?>... parameterTypes) {
    final JavaMethodSignature methodSignature;
    final Pair<String, Class[]> key = new Pair<String, Class[]>(name, parameterTypes);
    JavaMethodSignature oldSignature = ourSignatures2.get(key);
    if (oldSignature == null) {
      oldSignature = new JavaMethodSignature(name, parameterTypes);
      ourSignatures2.put(key, oldSignature);
    }
    methodSignature = oldSignature;
    return methodSignature;
  }

}
