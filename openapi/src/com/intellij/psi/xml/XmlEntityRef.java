/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.xml;

import com.intellij.psi.PsiFile;

/**
 * @author mike
 */
public interface XmlEntityRef extends XmlElement, XmlTagChild {
  XmlEntityDecl resolve(PsiFile targetFile);
}
