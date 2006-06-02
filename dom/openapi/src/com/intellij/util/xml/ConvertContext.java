/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.util.xml;

import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFile;
import com.intellij.openapi.module.Module;

/**
 * @author peter
 */
public abstract class ConvertContext {
  public abstract DomElement getInvocationElement();

  public abstract PsiClass findClass(String name);

  public abstract XmlTag getTag();

  public abstract XmlFile getFile();

  public abstract Module getModule();
}
