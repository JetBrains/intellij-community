/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Pair;
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
  private final Map<Class,List<Method>> myAllMethods = new HashMap<Class, List<Method>>();
  private final Map<Class,Method> myMethods = new HashMap<Class, Method>();

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
        method = findBestMethod(aClass, aClass.getMethod(myMethodName, myMethodParameters).getReturnType());
      }
      catch (NoSuchMethodException e) {
        if (aClass.isInterface()) {
          try {
            method = Object.class.getMethod(myMethodName, myMethodParameters);
          }
          catch (NoSuchMethodException e1) {
          }
        }
        throw new AssertionError(e);
      }
      myMethods.put(aClass, method);
    }
    return method;
  }

  public final List<Method> findAllMethods(final Class aClass) {
    List<Method> methods = myAllMethods.get(aClass);
    if (methods == null) {
      methods = new ArrayList<Method>();
      addMethods(aClass, methods);
    }
    return methods;
  }

  @Nullable
  public final <T extends Annotation> T findAnnotation(final Class<T> annotationClass, final Class startFrom) {
    for (final Method method : findAllMethods(startFrom)) {
      final T annotation = method.getAnnotation(annotationClass);
      if (annotation != null) {
        return annotation;
      }
    }
    return null;
  }

  @Nullable
  public final <T extends Annotation> Method findAnnotatedMethod(final Class<T> annotationClass, final Class startFrom) {
    for (final Method method : findAllMethods(startFrom)) {
      final T annotation = method.getAnnotation(annotationClass);
      if (annotation != null) {
        return method;
      }
    }
    return null;
  }

  private void addMethods(final Class aClass, final List<Method> methods) {
    List<Method> cachedMethods = myAllMethods.get(aClass);
    if (cachedMethods == null) {
      cachedMethods = new ArrayList<Method>();
      for (final Method method : aClass.getDeclaredMethods()) {
        if (method.getName().equals(myMethodName) && Arrays.equals(myMethodParameters, method.getParameterTypes())) {
          cachedMethods.add(method);
        }
      }
      final Class superclass = aClass.getSuperclass();
      if (superclass != null) {
        addMethods(superclass, cachedMethods);
      }
      for (final Class aClass1 : aClass.getInterfaces()) {
        addMethods(aClass1, cachedMethods);
      }
      cachedMethods = Collections.unmodifiableList(cachedMethods);
      myAllMethods.put(aClass, cachedMethods);
    }
    methods.addAll(cachedMethods);
  }

  public String toString() {
    return myMethodName + Arrays.asList(myMethodParameters);
  }

  private Method findBestMethod(final Class aClass, Class bestReturnType) {
    Method method = null;
    try {
      method = aClass.getDeclaredMethod(myMethodName, myMethodParameters);
      final Class<?> newReturnType = method.getReturnType();
      if (bestReturnType.isAssignableFrom(newReturnType) && !newReturnType.equals(bestReturnType)) {
        return method;
      }
    }
    catch (NoSuchMethodException e) {
      final Class[] interfaces = aClass.getInterfaces();
      for (final Class aClass1 : interfaces) {
        final Method bestMethod = findBestMethod(aClass1, bestReturnType);
        if (bestMethod != null) {
          method = bestMethod;
          bestReturnType = bestMethod.getReturnType();
        }
      }
      final Class superclass = aClass.getSuperclass();
      if (superclass != null) {
        final Method bestMethod = findBestMethod(superclass, bestReturnType);
        if (bestMethod != null) {
          return bestMethod;
        }
      } else if (aClass.isInterface() && interfaces.length == 0) {
        final Method bestMethod = findBestMethod(Object.class, bestReturnType);
        if (bestMethod != null) {
          return bestMethod;
        }
      }
    }
    return method;
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
