/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.xml;

import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlDocument;

/**
 * @author Mike
 */
public interface XmlNSDescriptor extends PsiMetaData{
  XmlElementDescriptor getElementDescriptor(XmlTag tag);
  XmlElementDescriptor[] getRootElementsDescriptors(final XmlDocument document);
  XmlFile getDescriptorFile();

  boolean isHierarhyEnabled();
}
