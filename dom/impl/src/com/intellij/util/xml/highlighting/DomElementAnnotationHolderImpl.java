/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixProvider;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
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

import java.util.List;
import java.util.Arrays;

public class DomElementAnnotationHolderImpl extends SmartList<DomElementProblemDescriptor> implements DomElementAnnotationHolder {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.highlighting.DomElementAnnotationHolderImpl");
  private final SmartList<Annotation> myAnnotations = new SmartList<Annotation>();

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
    return createProblem(domElement, highlightType, message, LocalQuickFix.EMPTY_ARRAY);
  }

  public DomElementProblemDescriptor createProblem(final DomElement domElement, final HighlightSeverity highlightType, final String message,
                                                   final LocalQuickFix[] fixes) {
    return addProblem(new DomElementProblemDescriptorImpl(domElement, message, highlightType, fixes));
  }

  @NotNull
  public DomElementResolveProblemDescriptor createResolveProblem(@NotNull GenericDomValue element, @NotNull PsiReference reference) {
    return addProblem(new DomElementResolveProblemDescriptorImpl(element, reference, getQuickFixes(element, reference)));
  }

  @NotNull
  public Annotation createAnnotation(DomElement element, HighlightSeverity severity, @Nullable String message) {
    final XmlElement xmlElement = element.getXmlElement();
    LOG.assertTrue(xmlElement != null, "No XML element for " + element);
    final TextRange range = xmlElement.getTextRange();
    final int startOffset = range.getStartOffset();
    final int endOffset = message == null ? startOffset : range.getEndOffset();
    final Annotation annotation = new Annotation(startOffset, endOffset, severity, message, null);
    myAnnotations.add(annotation);
    return annotation;
  }

  public final SmartList<Annotation> getAnnotations() {
    return myAnnotations;
  }

  public int getSize() {
    return size();
  }

  private static LocalQuickFix[] getQuickFixes(final GenericDomValue element, PsiReference reference) {
    final List<LocalQuickFix> result = new SmartList<LocalQuickFix>();
    final Converter converter = element.getConverter();
    if (converter instanceof ResolvingConverter) {
      final ResolvingConverter resolvingConverter = (ResolvingConverter)converter;
      result.addAll(Arrays.asList(resolvingConverter.getQuickFixes(new ConvertContextImpl(DomManagerImpl.getDomInvocationHandler(element)))));
    }
    if (reference instanceof LocalQuickFixProvider) {
      result.addAll(Arrays.asList(((LocalQuickFixProvider)reference).getQuickFixes()));
    }
    return result.toArray(new LocalQuickFix[result.size()]);
  }

  public <T extends DomElementProblemDescriptor> T addProblem(final T problemDescriptor) {
    add(problemDescriptor);
    return problemDescriptor;
  }

}
