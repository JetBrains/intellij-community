/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;

/**
 * @author Mike
 */
public interface XmlElement extends PsiElement {
  Key<XmlElement> ORIGINAL_ELEMENT = Key.create("ORIGINAL_ELEMENT");
  Key DEPENDING_ELEMENT = Key.create("DEPENDING_ELEMENT");

  XmlElement[] EMPTY_ARRAY = new XmlElement[0];

  boolean processElements(PsiElementProcessor processor, PsiElement place);
}
