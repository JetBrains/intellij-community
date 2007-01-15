/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.jsp.impl;

import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.psi.xml.XmlFile;

/**
 * @author peter
 */
public interface TldDescriptor extends XmlNSDescriptor {
  XmlElementDescriptor getElementDescriptor(String name);

  String getUri();

  int getFunctionsCount();

  String[] getFunctionNames();

  XmlFile getDeclarationFile();

  FunctionDescriptor getFunctionDescriptor(String name);
}
