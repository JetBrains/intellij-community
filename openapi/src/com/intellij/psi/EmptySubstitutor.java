/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;

/**
 * @author dsl
 */
public abstract class EmptySubstitutor implements PsiSubstitutor {
  public static EmptySubstitutor getInstance()  {
    return ApplicationManager.getApplication().getComponent(EmptySubstitutor.class);
  }
}
