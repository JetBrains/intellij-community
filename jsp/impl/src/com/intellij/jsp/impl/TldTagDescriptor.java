/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.jsp.impl;

/**
 * @author mike
 */
public interface TldTagDescriptor extends JspElementDescriptor {
  String getTagClass();

  String getTeiClass();
}
