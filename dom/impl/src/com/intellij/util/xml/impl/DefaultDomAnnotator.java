/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.impl;

import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.xml.*;
import com.intellij.util.xml.highlighting.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class DefaultDomAnnotator implements Annotator {

  @Nullable
  private static DomElement getDomElement(PsiElement psiElement, DomManager myDomManager) {
    if (psiElement instanceof XmlTag) {
      return myDomManager.getDomElement((XmlTag)psiElement);
    }
    if (psiElement instanceof XmlAttribute) {
      return myDomManager.getDomElement((XmlAttribute)psiElement);
    }
    return null;
  }

  public <T extends DomElement> void runInspection(@Nullable final DomElementsInspection<T> inspection, final DomFileElement<T> fileElement, List<Annotation> toFill) {
    if (inspection == null) return;
    DomElementAnnotationsManagerImpl annotationsManager = getAnnotationsManager(fileElement);
    if (DomElementAnnotationsManagerImpl.isHolderUpToDate(fileElement) && annotationsManager.getProblemHolder(fileElement).isInspectionCompleted(inspection)) return;

    final DomElementAnnotationHolderImpl annotationHolder = new DomElementAnnotationHolderImpl();
    inspection.checkFileElement(fileElement, annotationHolder);
    annotationsManager.appendProblems(fileElement, annotationHolder, inspection.getClass());
    for (final DomElementProblemDescriptor descriptor : annotationHolder) {
      toFill.addAll(descriptor.getAnnotations());
    }
    toFill.addAll(annotationHolder.getAnnotations());
  }

  protected DomElementAnnotationsManagerImpl getAnnotationsManager(final DomElement element) {
    return (DomElementAnnotationsManagerImpl)DomElementAnnotationsManager.getInstance(element.getManager().getProject());
  }


  public void annotate(final PsiElement psiElement, AnnotationHolder holder) {
    final List<Annotation> list = (List<Annotation>)holder;

    final DomManagerImpl domManager = DomManagerImpl.getDomManager(psiElement.getProject());
    final DomFileDescription description = domManager.getDomFileDescription(psiElement);
    if (description != null) {
      final DomElement domElement = getDomElement(psiElement, domManager);
      if (domElement != null) {
        runInspection(domElement, list);
      }
    }
  }

  public final void runInspection(final DomElement domElement, final List<Annotation> list) {
    final DomFileElement root = DomUtil.getFileElement(domElement);
    runInspection(getAnnotationsManager(domElement).getMockInspection(root), root, list);
  }

}
