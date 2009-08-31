/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.jsp.impl;

import com.intellij.psi.xml.XmlFile;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public interface TldDescriptor extends JspNsDescriptor {
  XmlElementDescriptor getElementDescriptor(String name);

  @Nullable
  String getUri();

  /**
   *
   * @return short name, or null if not present
   */
  @Nullable
  String getDefaultPrefix();

  int getFunctionsCount();

  String[] getFunctionNames();

  XmlFile getDeclarationFile();

  FunctionDescriptor getFunctionDescriptor(String name);

  void resetClassloaderState();
}
