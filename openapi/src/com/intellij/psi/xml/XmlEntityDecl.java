/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * @author mike
 */
public interface XmlEntityDecl extends XmlElement {
  int CONTEXT_ELEMENT_CONTENT_SPEC = 1;
  int CONTEXT_ATTRIBUTE_SPEC = 2;
  int CONTEXT_ATTLIST_SPEC = 3;
  int CONTEXT_ENTITY_DECL_CONTENT = 4;
  int CONTEXT_GENERIC_XML = 5;
  int CONTEXT_ENUMERATED_TYPE = 6;

  String getName();
  PsiElement parse(PsiFile baseFile, int context, XmlEntityRef originalElement);
}
