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

  static class IncorrectTypeException extends Exception {}

  static class TypeSyntaxException extends IncorrectTypeException {}

  static class NoTypeException extends IncorrectTypeException {}

}
