/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericValue;

import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
public class DomElementsHighlightingUtil {
  static final AnnotationHolderImpl EMPTY_ANNOTATION_HOLDER = new AnnotationHolderImpl() {
    public boolean add(final Annotation annotation) {
      return false;
    }
  };

  public static List<ProblemDescriptor> createProblemDescriptors(final InspectionManager manager, final DomElementProblemDescriptor problemDescriptor) {
    return createProblemDescriptors(problemDescriptor, new Function<ASTNode, ProblemDescriptor>() {
      public ProblemDescriptor fun(final ASTNode s) {
        return manager.createProblemDescriptor(s.getPsi(), problemDescriptor.getDescriptionTemplate(),
                                                       problemDescriptor.getFixes(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
    });
  }
  
  public static List<Annotation> createAnnotations(final DomElementProblemDescriptor problemDescriptor) {

    return createProblemDescriptors(problemDescriptor, new Function<ASTNode, Annotation>() {
      public Annotation fun(final ASTNode s) {
        final String text = problemDescriptor.getDescriptionTemplate();
        final HighlightSeverity severity = problemDescriptor.getHighlightSeverity();
        final AnnotationHolderImpl holder = EMPTY_ANNOTATION_HOLDER;
        if (severity.compareTo(HighlightSeverity.ERROR) >= 0) return holder.createErrorAnnotation(s, text);
        if (severity.compareTo(HighlightSeverity.WARNING) >= 0) return holder.createWarningAnnotation(s, text);
        if (severity.compareTo(HighlightSeverity.INFO) >= 0) return holder.createInfoAnnotation(s, text);
        return holder.createInformationAnnotation(s, text);
      }
    });
  }

  private static <T> List<T> createProblemDescriptors(final DomElementProblemDescriptor problemDescriptor, final Function<ASTNode, T> creator) {
    List<T> descritors = new SmartList<T>();
    final DomElement domElement = problemDescriptor.getDomElement();

    final PsiElement psiElement = getPsiElement(domElement);
    if (psiElement != null) {
      final XmlTag tag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class, false);
      if (tag != null && tag.getSubTags().length > 0) {
        addDescriptionsToTagEnds(tag, descritors, creator);
      } else {
        descritors.add(creator.fun(psiElement.getNode()));
      }
    } else {
      final XmlTag tag = getParentXmlTag(domElement);
      if (tag != null) {
        addDescriptionsToTagEnds(tag, descritors, creator);
      }
    }
    return descritors;
  }

  private static <T> void addDescriptionsToTagEnds(final XmlTag tag, final List<T> descritors, Function<ASTNode, T> creator) {
    final ASTNode startNode = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.getNode());
    final ASTNode endNode = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(tag.getNode());

    descritors.add(creator.fun(startNode));

    if (endNode != null && !startNode.equals(endNode)) {
      descritors.add(creator.fun(endNode));
    }
  }

  private static PsiElement getPsiElement(final DomElement domElement) {
    if (domElement instanceof GenericAttributeValue) {
      return ((GenericAttributeValue)domElement).getXmlAttributeValue();
    }
    if (domElement instanceof GenericValue && ((GenericValue)domElement).getStringValue() != null &&
        domElement.getXmlTag().getValue().getTextElements().length > 0) {
      return domElement.getXmlTag().getValue().getTextElements()[0];
    }

    return domElement.getXmlTag();
  }

  private static XmlTag getParentXmlTag(final DomElement domElement) {
    DomElement parent = domElement.getParent();
    while (parent != null) {
      if (parent.getXmlTag() != null) return parent.getXmlTag();
    }
    return null;
  }

}
