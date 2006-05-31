/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xml.*;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Method;

/**
 * @author peter
 */
public class DomImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomImplUtil");

  public static boolean tryAccept(final DomElementVisitor visitor, final Class aClass, DomElement proxy) {
    try {
      tryInvoke(visitor, "visit" + aClass.getSimpleName(), aClass, proxy);
      return true;
    }
    catch (NoSuchMethodException e) {
      try {
        tryInvoke(visitor, "visit", aClass, proxy);
        return true;
      }
      catch (NoSuchMethodException e1) {
        for (Class aClass1 : aClass.getInterfaces()) {
          if (tryAccept(visitor, aClass1, proxy)) {
            return true;
          }
        }
        return false;
      }
    }
  }

  static void tryInvoke(final DomElementVisitor visitor, @NonNls final String name, final Class aClass, DomElement proxy) throws NoSuchMethodException {
    final Method method = visitor.getClass().getMethod(name, aClass);
    method.setAccessible(true);
    DomReflectionUtil.invokeMethod(method, visitor, proxy);
  }


  public static boolean isTagValueGetter(final Method method) {
    if (!isGetter(method)) {
      return false;
    }
    if (hasTagValueAnnotation(method)) {
      return true;
    }
    if ("getValue".equals(method.getName())) {
      final JavaMethodSignature signature = JavaMethodSignature.getSignature(method);
      final Class<?> declaringClass = method.getDeclaringClass();
      if (signature.findAnnotation(SubTag.class, declaringClass) != null) return false;
      if (signature.findAnnotation(SubTagList.class, declaringClass) != null) return false;
      if (DomElement.class.isAssignableFrom(method.getReturnType())) return false;
      return true;
    }
    return false;
  }

  static boolean hasTagValueAnnotation(final Method method) {
    return DomReflectionUtil.findAnnotationDFS(method, TagValue.class) != null;
  }

  public static boolean isGetter(final Method method) {
    final String name = method.getName();
    if (method.getParameterTypes().length != 0) {
      return false;
    }
    final Class<?> returnType = method.getReturnType();
    if (name.startsWith("get")) {
      return returnType != void.class;
    }
    if (name.startsWith("is")) {
      return DomReflectionUtil.canHaveIsPropertyGetterPrefix(method.getGenericReturnType());
    }
    return false;
  }


  public static boolean isTagValueSetter(final Method method) {
    boolean setter = method.getName().startsWith("set") && method.getParameterTypes().length == 1 && method.getReturnType() == void.class;
    return setter && (hasTagValueAnnotation(method) || "setValue".equals(method.getName()));
  }

  public static DomNameStrategy getDomNameStrategy(final Class<?> rawType, boolean isAttribute) {
    Class aClass = null;
    if (isAttribute) {
      NameStrategyForAttributes annotation = DomReflectionUtil.findAnnotationDFS(rawType, NameStrategyForAttributes.class);
      if (annotation != null) {
        aClass = annotation.value();
      }
    }
    if (aClass == null) {
      NameStrategy annotation = DomReflectionUtil.findAnnotationDFS(rawType, NameStrategy.class);
      if (annotation != null) {
        aClass = annotation.value();
      }
    }
    if (aClass != null) {
      if (HyphenNameStrategy.class.equals(aClass)) return DomNameStrategy.HYPHEN_STRATEGY;
      if (JavaNameStrategy.class.equals(aClass)) return DomNameStrategy.JAVA_STRATEGY;
      try {
        return (DomNameStrategy)aClass.newInstance();
      }
      catch (InstantiationException e) {
        LOG.error(e);
      }
      catch (IllegalAccessException e) {
        LOG.error(e);
      }
    }
    return null;
  }
}
