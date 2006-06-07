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
  private static final Map<Method, JavaMethodSignature> ourSignatures = new HashMap<Method, JavaMethodSignature>();
  private static final Map<Pair<String, Class[]>, JavaMethodSignature> ourSignatures2 = new HashMap<Pair<String, Class[]>, JavaMethodSignature>();
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
    return findMethod(instance.getClass()).invoke(instance, args);
  }

  public final Method findMethod(final Class aClass) {
    Method method = myMethods.get(aClass);
    if (method == null) {
      try {
        method = aClass.getMethod(myMethodName, myMethodParameters);
      }
      catch (NoSuchMethodException e) {
        throw new AssertionError(e);
      }
      myMethods.put(aClass, method);
    }
    return method;
  }

  private void addMethodsIfNeeded(final Class aClass) {
    if (!myKnownClasses.contains(aClass)) {
      try {
        myKnownClasses.add(aClass);
        myAllMethods.add(aClass.getDeclaredMethod(myMethodName, myMethodParameters));
      }
      catch (NoSuchMethodException e) {
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
  }

  private List<Method> findAllMethods(final Class aClass) {
    addMethodsIfNeeded(aClass);
    return myAllMethods;
  }

  @Nullable
  public final <T extends Annotation> T findAnnotation(final Class<T> annotationClass, final Class startFrom) {
    for (final Method method : findAllMethods(startFrom)) {
      final T annotation = method.getAnnotation(annotationClass);
      if (annotation != null && ReflectionCache.isAssignable(method.getDeclaringClass(), startFrom)) {
        return annotation;
      }
    }
    return null;
  }

  @Nullable
  public final <T extends Annotation> Method findAnnotatedMethod(final Class<T> annotationClass, final Class startFrom) {
    for (final Method method : findAllMethods(startFrom)) {
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
    JavaMethodSignature methodSignature = ourSignatures.get(method);
    if (methodSignature == null) {
      final Pair<String, Class[]> key = new Pair<String, Class[]>(method.getName(), method.getParameterTypes());
      JavaMethodSignature oldSignature = ourSignatures2.get(key);
      if (oldSignature == null) {
        oldSignature = new JavaMethodSignature(method.getName(), method.getParameterTypes());
        ourSignatures2.put(key, oldSignature);
      }
      methodSignature = oldSignature;
      ourSignatures.put(method, methodSignature);
    }
    return methodSignature;
  }

}
