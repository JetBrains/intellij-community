/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.PsiNamedElement;

/**
 * @author Mike
 */
public interface XmlAttributeDecl extends XmlElement, PsiMetaOwner, PsiNamedElement {
  XmlElement getNameElement();
  XmlAttributeValue getDefaultValue();

  boolean isAttributeRequired();
  boolean isAttributeFixed();
  boolean isAttributeImplied();

  boolean isEnumerated();
  XmlElement[] getEnumeratedValues();

  boolean isIdAttribute();
  boolean isIdRefAttribute();
}
