/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.xml;

import com.intellij.openapi.util.Condition;
import com.intellij.util.ReflectionCache;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author peter
 */
public class JavaMethodSignature {
  private static final Map<Method, JavaMethodSignature> ourSignatures = new ConcurrentHashMap<Method, JavaMethodSignature>();
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
    final Class<?> aClass = instance.getClass();
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
    final Method method = ReflectionUtil.getMethod(aClass, myMethodName, myMethodParameters);
    return method == null ? ReflectionUtil.getDeclaredMethod(aClass, myMethodName, myMethodParameters) : method;
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
    T result = null;
    Class bestClass = null;

    final int size = myAllMethods.size();
    for (int i = 0; i < size; i++) {
      Method method = myAllMethods.get(i);
      final T annotation = method.getAnnotation(annotationClass);
      if (annotation != null) {
        final Class<?> declaringClass = method.getDeclaringClass();
        if (ReflectionCache.isAssignable(declaringClass, startFrom) &&
            (result == null || ReflectionCache.isAssignable(bestClass, declaringClass))) {
          result = annotation;
          bestClass = declaringClass;
        }
      }
    }
    return result;
  }

  public final synchronized List<Method> getAllMethods(final Class startFrom) {
    addMethodsIfNeeded(startFrom);
    final List<Method> list = ContainerUtil.findAll(myAllMethods, new Condition<Method>() {
      public boolean value(final Method method) {
        return method.getDeclaringClass().isAssignableFrom(startFrom);
      }
    });
    return list;
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

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final JavaMethodSignature that = (JavaMethodSignature)o;

    if (!myMethodName.equals(that.myMethodName)) return false;
    if (!Arrays.equals(myMethodParameters, that.myMethodParameters)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMethodName.hashCode();
    result = 31 * result + Arrays.hashCode(myMethodParameters);
    return result;
  }

  public static JavaMethodSignature getSignature(Method method) {
    JavaMethodSignature signature = ourSignatures.get(method);
    if (signature != null) return signature;

    ourSignatures.put(method, signature = new JavaMethodSignature(method.getName(), method.getParameterTypes()));
    return signature;
  }

  public static JavaMethodSignature getSignature(final String name, final Class<?>... parameterTypes) {
    return new JavaMethodSignature(name, parameterTypes);
  }

}
