// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.jsp.jspXml;

import com.intellij.psi.xml.XmlTag;

public interface JspXmlTagBase extends XmlTag, JspTag {
  XmlTag findParentTag();
}
