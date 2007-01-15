/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.jsp.impl;

import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NonNls;

/**
 * @author peter
 */
public interface TldTagFileDescriptor extends CustomTagDescriptorBase {
  @NonNls String TAG_SUFFIX = "tag";
  @NonNls String TAGX_SUFFIX = "tagx";

  XmlTag getRealDeclaration();
}
