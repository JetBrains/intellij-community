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
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomFileDescription;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolderImpl;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManagerImpl;
import com.intellij.util.xml.highlighting.DomElementProblemDescriptor;
import com.intellij.util.xml.highlighting.DomElementsInspection;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public class DefaultDomAnnotator implements Annotator {
  private final DomElementAnnotationsManagerImpl myAnnotationsManager;
  private DomManagerImpl myDomManager;

  public DefaultDomAnnotator(final DomManagerImpl domManager, final DomElementAnnotationsManagerImpl annotationsManager) {
    myAnnotationsManager = annotationsManager;
    myDomManager = domManager;
  }

  @Nullable
  private DomElement getDomElement(PsiElement psiElement) {
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
    if (DomElementAnnotationsManagerImpl.isHolderUpToDate(fileElement) && myAnnotationsManager.getProblemHolder(fileElement).isInspectionCompleted(inspection)) return;

    final DomElementAnnotationHolderImpl annotationHolder = new DomElementAnnotationHolderImpl();
    inspection.checkFileElement(fileElement, annotationHolder);
    myAnnotationsManager.appendProblems(fileElement, annotationHolder, inspection.getClass());
    for (final DomElementProblemDescriptor descriptor : annotationHolder) {
      toFill.addAll(descriptor.getAnnotations());
    }
    toFill.addAll(annotationHolder.getAnnotations());
  }


  public void annotate(final PsiElement psiElement, AnnotationHolder holder) {
    final List<Annotation> list = (List<Annotation>)holder;

    final DomFileDescription description = myDomManager.getDomFileDescription(psiElement);
    if (description != null) {
      final DomElement domElement = getDomElement(psiElement);
      if (domElement != null) {
        runInspection(domElement, list);
      }
    }
  }

  public final void runInspection(final DomElement domElement, final List<Annotation> list) {
    final DomFileElement root = domElement.getRoot();
    runInspection(myAnnotationsManager.getMockInspection(root), root, list);
  }

}
