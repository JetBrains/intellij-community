/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;



/**
 * @author mike
 */
public interface XmlEnumeratedType extends XmlElement {
  XmlElement[] getEnumeratedValues();
}
