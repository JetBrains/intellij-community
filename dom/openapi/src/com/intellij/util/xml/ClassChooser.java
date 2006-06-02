/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

/**
 * @author peter
 */
public abstract class ClassChooser<T extends DomElement> {
  public abstract Class<? extends T> chooseClass(XmlTag tag);
  public abstract void distinguishTag(XmlTag tag, Class<? extends T> aClass) throws IncorrectOperationException;
  public abstract Class[] getChooserClasses();
}
