/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.converters;

import com.intellij.psi.PsiType;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.DomElement;

import java.util.List;

/**
 * @author peter
 */
public interface AbstractMethodParams extends DomElement {
  List<GenericDomValue<PsiType>> getMethodParams();

  GenericDomValue<PsiType> addMethodParam();
}
