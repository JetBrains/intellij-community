/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericValue;

import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
public class DomElementsHighlightingUtil {

  public static List<ProblemDescriptor> createProblemDescriptors(final InspectionManager manager, DomElementProblemDescriptor problemDescriptor) {
    List<ProblemDescriptor>  descritors = new SmartList<ProblemDescriptor>();
    final DomElement domElement = problemDescriptor.getDomElement();

    final PsiElement psiElement = getPsiElement(domElement);
    if (psiElement != null) {
      final XmlTag tag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class, false);
      if (tag != null && tag.getSubTags().length > 0) {
        addDescriptionsToTagEnds(tag, descritors, manager, problemDescriptor);
      } else {
        descritors.add(manager.createProblemDescriptor(psiElement, problemDescriptor.getDescriptionTemplate(),
                                                       problemDescriptor.getFixes(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
      }
    } else {
      final XmlTag tag = getParentXmlTag(domElement);
      if (tag != null) {
        addDescriptionsToTagEnds(tag, descritors, manager, problemDescriptor);
      }
    }
    return descritors;
  }

  private static void addDescriptionsToTagEnds(final XmlTag tag, final List<ProblemDescriptor> descritors, final InspectionManager manager,
                                               final DomElementProblemDescriptor problemDescriptor) {
    final ASTNode startNode = XmlChildRole.START_TAG_NAME_FINDER.findChild(tag.getNode());
    final ASTNode endNode = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(tag.getNode());

    descritors.add(manager.createProblemDescriptor(startNode.getPsi(), problemDescriptor.getDescriptionTemplate(), problemDescriptor.getFixes(),
                                                   ProblemHighlightType.GENERIC_ERROR_OR_WARNING));

    if (endNode != null && !startNode.equals(endNode)) {
      descritors.add(manager.createProblemDescriptor(endNode.getPsi(), problemDescriptor.getDescriptionTemplate(), problemDescriptor.getFixes(),
                                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING));

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
