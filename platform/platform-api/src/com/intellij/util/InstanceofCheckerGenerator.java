/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class InstanceofCheckerGenerator {
  private static InstanceofCheckerGenerator ourInstance;

  static {
    try {
      ourInstance = (InstanceofCheckerGenerator)Class.forName("com.intellij.util.InstanceofCheckerGeneratorImpl").newInstance();
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  public static InstanceofCheckerGenerator getInstance() {
    return ourInstance;
  }

  @NotNull 
  public abstract Condition<Object> getInstanceofChecker(Class<?> someClass);

}
