/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.impl.source.jsp.jspJava;

import com.intellij.psi.impl.source.jsp.jspXml.JspTag;
import com.intellij.psi.xml.XmlTag;

/**
 * @author peter
 */
public interface JspXmlTagBase extends XmlTag, JspTag {
  XmlTag findParentTag();
}
