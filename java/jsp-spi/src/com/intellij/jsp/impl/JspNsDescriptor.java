/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.jsp.impl;

import com.intellij.openapi.project.DumbAware;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public interface JspNsDescriptor extends XmlNSDescriptor {
  @NonNls String ROOT_ELEMENT_DESCRIPTOR = "root";
}
