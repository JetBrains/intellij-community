/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

public interface PsiLiteralExpression extends PsiExpression {
  //TODO: consider getValue to throw exception when parsing error and remove method getParsingError()!
  Object getValue();
  String getParsingError();
}
