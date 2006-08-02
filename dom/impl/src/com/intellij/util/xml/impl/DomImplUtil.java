/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ReflectionCache;
import com.intellij.util.xml.*;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public class DomImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.impl.DomImplUtil");

  public static boolean isTagValueGetter(final JavaMethod method) {
    if (!isGetter(method)) {
      return false;
    }
    if (hasTagValueAnnotation(method)) {
      return true;
    }
    if ("getValue".equals(method.getName())) {
      final JavaMethodSignature signature = method.getSignature();
      final Class<?> declaringClass = method.getDeclaringClass();
      if (signature.findAnnotation(SubTag.class, declaringClass) != null) return false;
      if (signature.findAnnotation(SubTagList.class, declaringClass) != null) return false;
      if (signature.findAnnotation(Convert.class, declaringClass) != null ||
          signature.findAnnotation(Resolve.class, declaringClass) != null) {
        return !ReflectionCache.isAssignable(GenericDomValue.class, method.getReturnType());
      }
      if (ReflectionCache.isAssignable(DomElement.class, method.getReturnType())) return false;
      return true;
    }
    return false;
  }

  static boolean hasTagValueAnnotation(final JavaMethod method) {
    return method.getAnnotation(TagValue.class) != null;
  }

  public static boolean isGetter(final JavaMethod method) {
    final String name = method.getName();
    if (method.getGenericParameterTypes().length != 0) {
      return false;
    }
    final Type returnType = method.getGenericReturnType();
    if (name.startsWith("get")) {
      return returnType != void.class;
    }
    return name.startsWith("is") && DomReflectionUtil.canHaveIsPropertyGetterPrefix(returnType);
  }


  public static boolean isTagValueSetter(final JavaMethod method) {
    boolean setter = method.getName().startsWith("set") && method.getGenericParameterTypes().length == 1 && method.getReturnType() == void.class;
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
