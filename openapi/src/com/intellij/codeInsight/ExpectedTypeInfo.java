package com.intellij.codeInsight;

import com.intellij.psi.PsiType;

/**
 * Created by IntelliJ IDEA.
 * User: ven
 * Date: Jul 14, 2004
 * Time: 4:45:46 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ExpectedTypeInfo {
  int TYPE_STRICTLY = 0;
  int TYPE_OR_SUBTYPE = 1;
  int TYPE_OR_SUPERTYPE = 2;
  ExpectedTypeInfo[] EMPTY = new ExpectedTypeInfo[0];

  PsiType getType ();

  PsiType getDefaultType ();

  public int getKind();

  boolean equals (ExpectedTypeInfo info);

  String toString();

  ExpectedTypeInfo[] intersect(ExpectedTypeInfo info);

  boolean isArrayTypeInfo ();

  int getTailType();
}
