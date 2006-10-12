/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.ProblemDescriptorImpl;
import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericValue;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User: Sergey.Vasiliev
 */
public class DomElementsHighlightingUtil {
  private static final AnnotationHolderImpl EMPTY_ANNOTATION_HOLDER = new AnnotationHolderImpl() {
    public boolean add(final Annotation annotation) {
      return false;
    }
  };

  public static List<ProblemDescriptor> createProblemDescriptors(final DomElementProblemDescriptor problemDescriptor) {
    final ProblemHighlightType type = problemDescriptor instanceof DomElementResolveProblemDescriptor ? ProblemHighlightType.LIKE_UNKNOWN_SYMBOL : ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    return createProblemDescriptors(problemDescriptor, new Function<Pair<TextRange, PsiElement>, ProblemDescriptor>() {
      public ProblemDescriptor fun(final Pair<TextRange, PsiElement> s) {
        final PsiElement element = s.second;
        return new ProblemDescriptorImpl(element, element, problemDescriptor.getDescriptionTemplate(), problemDescriptor.getFixes(), type, false) {

          public final PsiElement getPsiElement() {
            return getStartElement();
          }

          public TextRange getTextRange() {
            return s.first;
          }
        };
      }
    });
  }
  
  public static List<Annotation> createAnnotations(final DomElementProblemDescriptor problemDescriptor) {

    return createProblemDescriptors(problemDescriptor, new Function<Pair<TextRange, PsiElement>, Annotation>() {
      public Annotation fun(final Pair<TextRange, PsiElement> s) {
        final String text = problemDescriptor.getDescriptionTemplate();
        final HighlightSeverity severity = problemDescriptor.getHighlightSeverity();
        final AnnotationHolderImpl holder = EMPTY_ANNOTATION_HOLDER;
        final TextRange range = s.first;
        if (severity.compareTo(HighlightSeverity.ERROR) >= 0) return holder.createErrorAnnotation(range, text);
        if (severity.compareTo(HighlightSeverity.WARNING) >= 0) return holder.createWarningAnnotation(range, text);
        if (severity.compareTo(HighlightSeverity.INFO) >= 0) return holder.createInfoAnnotation(range, text);
        return holder.createInformationAnnotation(range, text);
      }
    });
  }

  private static <T> List<T> createProblemDescriptors(final DomElementProblemDescriptor problemDescriptor, final Function<Pair<TextRange, PsiElement>, T> creator) {
    final List<T> descritors = new SmartList<T>();

    if (problemDescriptor instanceof DomElementResolveProblemDescriptor) {
      final PsiReference reference = ((DomElementResolveProblemDescriptor)problemDescriptor).getPsiReference();
      final PsiElement element = reference.getElement();
      final int startOffset = element.getTextRange().getStartOffset();
      descritors.add(creator.fun(Pair.create(reference.getRangeInElement().shiftRight(startOffset), element)));
      return descritors;
    }

    final DomElement domElement = problemDescriptor.getDomElement();
    final PsiElement psiElement = getPsiElement(domElement);
    if (psiElement != null && StringUtil.isNotEmpty(psiElement.getText())) {
      final XmlTag tag = PsiTreeUtil.getParentOfType(psiElement, XmlTag.class, false);
      if (tag != null && tag.getSubTags().length > 0) {
        addDescriptionsToTagEnds(tag, descritors, creator);
      } else {
        descritors.add(creator.fun(Pair.create(psiElement.getTextRange(), psiElement)));
      }
    } else {
      final XmlTag tag = getParentXmlTag(domElement);
      if (tag != null) {
        addDescriptionsToTagEnds(tag, descritors, creator);
      }
    }
    return descritors;
  }

  @Nullable
  private static TextRange getTextRange(final DomElementProblemDescriptor problemDescriptor, final PsiElement psiElement) {
    if (psiElement == null) return null;
    return StringUtil.isEmpty(psiElement.getText()) ? null : psiElement.getTextRange();
  }

  private static <T> void addDescriptionsToTagEnds(final XmlTag tag, final List<T> descritors, Function<Pair<TextRange, PsiElement>, T> creator) {
    final ASTNode node = tag.getNode();
    assert node != null;
    final ASTNode startNode = XmlChildRole.START_TAG_NAME_FINDER.findChild(node);
    final ASTNode endNode = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(node);

    descritors.add(creator.fun(Pair.create(startNode.getTextRange(), (PsiElement)tag)));

    if (endNode != null && !startNode.equals(endNode)) {
      descritors.add(creator.fun(Pair.create(endNode.getTextRange(), (PsiElement)tag)));
    }
  }

  @Nullable
  private static PsiElement getPsiElement(final DomElement domElement) {
    if (domElement instanceof GenericAttributeValue) {
      return ((GenericAttributeValue)domElement).getXmlAttributeValue();
    }
    final XmlTag tag = domElement.getXmlTag();
    if (domElement instanceof GenericValue && tag != null) {
      final XmlText[] textElements = tag.getValue().getTextElements();
      if (textElements.length > 0) {
        return textElements[0];
      }
    }

    return tag;
  }

  @Nullable
  private static XmlTag getParentXmlTag(final DomElement domElement) {
    DomElement parent = domElement.getParent();
    while (parent != null) {
      if (parent.getXmlTag() != null) return parent.getXmlTag();
      parent = parent.getParent();
    }
    return null;
  }

}
