/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public interface Converter<T> {
  T fromString(String s, final ConvertContext context);
  String toString(T t, final ConvertContext context);

  Converter<Integer> INTEGER_CONVERTER = new Converter<Integer>() {
    public Integer fromString(final String s, final ConvertContext context) {
      try {
        return Integer.decode(s);
      }
      catch (Exception e) {
        return null;
      }
    }

    public String toString(final Integer t, final ConvertContext context) {
      return t.toString();
    }

  };

  Converter<String> EMPTY_CONVERTER = new Converter<String>() {
    public String fromString(final String s, final ConvertContext context) {
      return s;
    }

    public String toString(final String t, final ConvertContext context) {
      return t;
    }

  };

  Converter<PsiClass> PSI_CLASS_CONVERTER = new Converter<PsiClass>() {
    public PsiClass fromString(final String s, final ConvertContext context) {
      return context.findClass(s);
    }

    public String toString(final PsiClass t, final ConvertContext context) {
      return t.getQualifiedName();
    }

  };

  Converter<PsiType> PSI_TYPE_CONVERTER = new Converter<PsiType>() {
    public PsiType fromString(final String s, final ConvertContext context) {
      try {
        return context.getFile().getManager().getElementFactory().createTypeFromText(s, null);
      }
      catch (IncorrectOperationException e) {
        return null;
      }
    }

    public String toString(final PsiType t, final ConvertContext context) {
      return t.getCanonicalText();
    }

  };

}
