/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.xml.XmlNSDescriptor;

import java.util.Map;

/**
 * @author Mike
 */
public interface XmlDocument extends XmlElement, PsiMetaOwner {
  XmlProlog getProlog();
  XmlTag getRootTag();

  XmlNSDescriptor getRootTagNSDescriptor();
}
