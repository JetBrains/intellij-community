/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 * @author dsl
 */
public interface PsiTypeCodeFragment extends PsiCodeFragment {
  PsiType getType()
    throws TypeSyntaxException, NoTypeException;

  boolean isVoidValid();

  class IncorrectTypeException extends Exception {}

  class TypeSyntaxException extends IncorrectTypeException {}

  class NoTypeException extends IncorrectTypeException {}
}
