/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiReference;
import com.intellij.util.SmartList;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.impl.ConvertContextImpl;
import com.intellij.util.xml.impl.DomManagerImpl;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DomElementAnnotationHolderImpl extends SmartList<DomElementProblemDescriptor> implements DomElementAnnotationHolder {

  @NotNull
  public DomElementProblemDescriptor createProblem(DomElement domElement, @Nullable String message) {
    return createProblem(domElement, HighlightSeverity.ERROR, message);
  }

  @NotNull
  public DomElementProblemDescriptor createProblem(DomElement domElement, DomCollectionChildDescription childDescription, @Nullable String message) {
    return addProblem(new DomCollectionProblemDescriptorImpl(domElement, message, HighlightSeverity.ERROR, childDescription));
  }

  @NotNull
  public final DomElementProblemDescriptor createProblem(DomElement domElement, HighlightSeverity highlightType, String message) {
    return addProblem(new DomElementProblemDescriptorImpl(domElement, message, highlightType));
  }

  @NotNull
  public DomElementResolveProblemDescriptor createResolveProblem(@NotNull GenericDomValue element, @NotNull PsiReference reference) {
    return addProblem(new DomElementResolveProblemDescriptorImpl(element, reference, getQuickFixes(element)));
  }

  public int getSize() {
    return size();
  }

  private static LocalQuickFix[] getQuickFixes(final GenericDomValue element) {
    final Converter converter = element.getConverter();
    if (converter instanceof ResolvingConverter) {
      final ResolvingConverter resolvingConverter = (ResolvingConverter)converter;
      return resolvingConverter.getQuickFixes(new ConvertContextImpl(DomManagerImpl.getDomInvocationHandler(element)));
    }
    return new LocalQuickFix[0];
  }

  public <T extends DomElementProblemDescriptor> T addProblem(final T problemDescriptor) {
    add(problemDescriptor);
    return problemDescriptor;
  }

}
