/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 13.11.2003
 * Time: 17:48:06
 * To change this template use Options | File Templates.
 */
public interface XmlNotationDecl extends XmlElement{
  XmlElement getNameElement();
  XmlElementContentSpec getContentSpecElement();
}
