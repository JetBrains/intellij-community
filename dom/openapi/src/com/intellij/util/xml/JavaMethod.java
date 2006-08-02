/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.util.containers.FactoryMap;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.annotation.Annotation;

/**
 * @author peter
 */
public final class JavaMethod implements AnnotatedElement{
  private static final FactoryMap<JavaMethodSignature,FactoryMap<Class,JavaMethod>> ourMethods = new FactoryMap<JavaMethodSignature, FactoryMap<Class, JavaMethod>>() {
    protected FactoryMap<Class, JavaMethod> create(final JavaMethodSignature signature) {
      return new FactoryMap<Class, JavaMethod>() {
        protected JavaMethod create(final Class key) {
          return new JavaMethod(key, signature);
        }
      };
    }
  };

  private final JavaMethodSignature mySignature;
  private final Class myDeclaringClass;
  private final Method myMethod;

  private JavaMethod(final Class declaringClass, final JavaMethodSignature signature) {
    mySignature = signature;
    myMethod = signature.findMethod(declaringClass);
    assert myMethod != null : "No method " + signature + " in class " + declaringClass;
    myDeclaringClass = myMethod.getDeclaringClass();
  }

  public final Class getDeclaringClass() {
    return myDeclaringClass;
  }

  public final JavaMethodSignature getSignature() {
    return mySignature;
  }

  public final Method getMethod() {
    return myMethod;
  }

  public final Type[] getGenericParameterTypes() {
    return myMethod.getGenericParameterTypes();
  }

  public final Type getGenericReturnType() {
    return myMethod.getGenericReturnType();
  }

  public static JavaMethod getMethod(final Class declaringClass, final JavaMethodSignature signature) {
    synchronized (ourMethods) {
      return ourMethods.get(signature).get(declaringClass);
    }
  }

  public static JavaMethod getMethod(final Class declaringClass, final Method method) {
    return getMethod(declaringClass, JavaMethodSignature.getSignature(method));
  }

  public final Object invoke(final Object o, final Object... args) {
    return DomReflectionUtil.invokeMethod(myMethod, o, args);
  }

  public String toString() {
    return "JavaMethod: " + myMethod.toString();
  }

  public final String getName() {
    return myMethod.getName();
  }

  public final <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
    return mySignature.findAnnotation(annotationClass, myDeclaringClass);
  }

  public final Class getReturnType() {
    return myMethod.getReturnType();
  }

  public Class<?>[] getParameterTypes() {
    return myMethod.getParameterTypes();
  }
}
