/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.psi.PsiClass;

/**
 * @author dsl
 */
public interface MoveInnerRefactoring extends Refactoring {

  PsiClass getInnerClass();

  String getNewClassName();

  boolean shouldPassParameter();

  String getParameterName();

  void setSearchInComments(boolean value);

  void setSearchInNonJavaFiles(boolean value);

  boolean isSearchInComments();

  boolean isSearchInNonJavaFiles();
}
