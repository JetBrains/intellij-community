/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

import com.intellij.psi.tree.IElementType;


/**
 * @author Mike
 */
public interface XmlToken extends XmlElement, XmlTokenType, DTDTokenType{
  IElementType getTokenType();
}
