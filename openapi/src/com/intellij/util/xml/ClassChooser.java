/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public interface ClassChooser<T extends DomElement> {
  Class<? extends T> chooseClass(XmlTag tag);
  void distinguishTag(XmlTag tag, Class<? extends T> aClass) throws IncorrectOperationException;
  Class[] getChooserClasses();
}
