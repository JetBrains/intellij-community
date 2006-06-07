/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ClassMap;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomElementVisitor;
import com.intellij.util.xml.DomReflectionUtil;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Method;

/**
 * @author peter
 */
public class VisitorDescription {
  private final Class<? extends DomElementVisitor> myVisitorClass;
  private final ClassMap<Method> myMethods = new ClassMap<Method>();
  @NonNls private static final String VISIT = "visit";

  public VisitorDescription(final Class<? extends DomElementVisitor> visitorClass) {
    myVisitorClass = visitorClass;
    for (final Method method : ReflectionCache.getMethods(visitorClass)) {
      final Class<?>[] parameterTypes = method.getParameterTypes();
      if (parameterTypes.length != 1) {
        continue;
      }
      final Class<?> domClass = parameterTypes[0];
      if (!ReflectionCache.isAssignable(DomElement.class, domClass)) {
        continue;
      }
      final String methodName = method.getName();
      if (VISIT.equals(methodName) ||
          methodName.startsWith(VISIT) && domClass.getSimpleName().equals(methodName.substring(VISIT.length()))) {
        method.setAccessible(true);
        myMethods.put(domClass, method);
      }
    }
  }

  public void acceptElement(DomElementVisitor visitor, DomElement element) {
    final Method method = myMethods.get(element.getClass());
    assert method != null : myVisitorClass + " can't accept element of type " + element.getClass();
    DomReflectionUtil.invokeMethod(method, visitor, element);
  }

}
