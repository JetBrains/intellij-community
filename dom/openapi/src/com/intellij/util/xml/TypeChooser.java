/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;

import java.lang.reflect.Type;

/**
 * @author peter
 */
public abstract class TypeChooser {
  public abstract Type chooseType(XmlTag tag);
  public abstract void distinguishTag(XmlTag tag, Type aClass) throws IncorrectOperationException;
  public abstract Type[] getChooserTypes();
}
