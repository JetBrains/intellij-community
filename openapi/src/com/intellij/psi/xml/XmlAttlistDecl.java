/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

/**
 * @author Mike
 */
public interface XmlAttlistDecl extends XmlElement {
  XmlElement getNameElement();
  XmlAttributeDecl[] getAttributeDecls();
}
