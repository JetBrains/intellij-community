/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
*/
public class CanonicalPsiTypeConverterImpl extends CanonicalPsiTypeConverter {
  public PsiType fromString(final String s, final ConvertContext context) {
    if (s == null) return null;
    try {
      return context.getFile().getManager().getElementFactory().createTypeFromText(s, null);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  public String toString(final PsiType t, final ConvertContext context) {
    return t == null? null:t.getCanonicalText();
  }

}
