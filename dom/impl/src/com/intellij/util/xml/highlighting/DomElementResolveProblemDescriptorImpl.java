/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.highlighting;

import com.intellij.util.xml.GenericDomValue;
import com.intellij.psi.PsiReference;
import com.intellij.codeInsight.daemon.impl.analysis.XmlHighlightVisitor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.codeInspection.LocalQuickFix;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
class DomElementResolveProblemDescriptorImpl extends DomElementProblemDescriptorImpl implements DomElementResolveProblemDescriptor {
  @NotNull private final PsiReference myReference;

  public DomElementResolveProblemDescriptorImpl(@NotNull final GenericDomValue domElement, @NotNull final PsiReference reference, LocalQuickFix... quickFixes) {
     super(domElement, XmlHighlightVisitor.getErrorDescription(reference), HighlightSeverity.ERROR, quickFixes);
     myReference = reference;
  }

  @NotNull
  public PsiReference getPsiReference() {
    return myReference;
  }

  @NotNull
  public GenericDomValue getDomElement() {
    return (GenericDomValue)super.getDomElement();
  }
}
