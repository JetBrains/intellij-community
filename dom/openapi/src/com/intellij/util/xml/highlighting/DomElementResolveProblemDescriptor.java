/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.highlighting;

import com.intellij.util.xml.GenericDomValue;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public interface DomElementResolveProblemDescriptor extends DomElementProblemDescriptor{
  @NotNull
  PsiReference getPsiReference();

  @NotNull
  GenericDomValue getDomElement();
}
