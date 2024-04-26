// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.memberPushDown;

import com.intellij.psi.PsiElement;

/**
 * Bean to store new class data if no inheritors were found: {@link PushDownDelegate#preprocessNoInheritorsFound(PsiElement, String)}
 */
public final class NewSubClassData {
  public static final NewSubClassData ABORT_REFACTORING = new NewSubClassData(null, null);

  private final Object myContext;
  private final String myNewClassName;

  public NewSubClassData(Object context, String newClassName) {
    myContext = context;
    myNewClassName = newClassName;
  }

  /**
   * Directory to create new class
   */
  public Object getContext() {
    return myContext;
  }

  public String getNewClassName() {
    return myNewClassName;
  }
}
