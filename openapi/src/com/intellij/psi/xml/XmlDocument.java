/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.Nullable;

/**
 * @author Mike
 */
public interface XmlDocument extends XmlElement, PsiMetaOwner {
  XmlProlog getProlog();
  @Nullable XmlTag getRootTag();

  XmlNSDescriptor getRootTagNSDescriptor();
  XmlNSDescriptor getDefaultNSDescriptor(final String namespace, final boolean strict);
}
