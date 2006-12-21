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
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericValue;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
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

  public static List<ProblemDescriptor> createProblemDescriptors(final InspectionManager manager, final DomElementProblemDescriptor problemDescriptor) {
    final ProblemHighlightType type = getProblemHighlightType(problemDescriptor);
    return createProblemDescriptors(problemDescriptor, new Function<Pair<TextRange, PsiElement>, ProblemDescriptor>() {
      public ProblemDescriptor fun(final Pair<TextRange, PsiElement> s) {
        return manager.createProblemDescriptor(s.second, s.first, problemDescriptor.getDescriptionTemplate(), type, problemDescriptor.getFixes());
      }
    });
  }

  private static ProblemHighlightType getProblemHighlightType(final DomElementProblemDescriptor problemDescriptor) {
    if (problemDescriptor instanceof DomElementResolveProblemDescriptor) {
      final TextRange range = ((DomElementResolveProblemDescriptor)problemDescriptor).getPsiReference().getRangeInElement();
      if (range.getStartOffset() != range.getEndOffset()) {
        return ProblemHighlightType.LIKE_UNKNOWN_SYMBOL;
      }
    }
    return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
  }

  public static List<Annotation> createAnnotations(final DomElementProblemDescriptor problemDescriptor) {

    return createProblemDescriptors(problemDescriptor, new Function<Pair<TextRange, PsiElement>, Annotation>() {
      public Annotation fun(final Pair<TextRange, PsiElement> s) {
        String text = problemDescriptor.getDescriptionTemplate();
        if (StringUtil.isEmpty(text)) text = null;
        final HighlightSeverity severity = problemDescriptor.getHighlightSeverity();
        final AnnotationHolderImpl holder = EMPTY_ANNOTATION_HOLDER;
        TextRange range = s.first;
        if (text == null) range = TextRange.from(range.getStartOffset(), 0);
        range = range.shiftRight(s.second.getTextRange().getStartOffset());
        final Annotation annotation = createAnnotation(severity, holder, range, text);
        if (problemDescriptor instanceof DomElementResolveProblemDescriptor) {
          annotation.setTextAttributes(CodeInsightColors.WRONG_REFERENCES_ATTRIBUTES);
        }
        return annotation;
      }
    });
  }

  private static Annotation createAnnotation(final HighlightSeverity severity, final AnnotationHolderImpl holder, final TextRange range,
                                             final String text) {
    if (severity.compareTo(HighlightSeverity.ERROR) >= 0) return holder.createErrorAnnotation(range, text);
    if (severity.compareTo(HighlightSeverity.WARNING) >= 0) return holder.createWarningAnnotation(range, text);
    if (severity.compareTo(HighlightSeverity.INFO) >= 0) return holder.createInformationAnnotation(range, text);
    return holder.createInfoAnnotation(range, text);
  }

  private static <T> List<T> createProblemDescriptors(final DomElementProblemDescriptor problemDescriptor, final Function<Pair<TextRange, PsiElement>, T> creator) {
    final List<T> descritors = new SmartList<T>();

    if (problemDescriptor instanceof DomElementResolveProblemDescriptor) {
      final PsiReference reference = ((DomElementResolveProblemDescriptor)problemDescriptor).getPsiReference();
      final PsiElement element = reference.getElement();
      final TextRange referenceRange = reference.getRangeInElement();
      final TextRange errorRange;
      if (referenceRange.getStartOffset() == referenceRange.getEndOffset()) {
        if (element instanceof XmlAttributeValue) {
          errorRange = TextRange.from(referenceRange.getStartOffset() - 1, 2);
        }
        else {
          errorRange = TextRange.from(referenceRange.getStartOffset(), 1);
        }
      } else {
        errorRange = referenceRange;
      }
      descritors.add(creator.fun(Pair.create(errorRange, element)));
      return descritors;
    }

    final DomElement domElement = problemDescriptor.getDomElement();
    final PsiElement psiElement = getPsiElement(domElement);
    if (psiElement != null && StringUtil.isNotEmpty(psiElement.getText())) {
      if (psiElement instanceof XmlTag) {
        final XmlTag tag = (XmlTag)psiElement;
        if (tag.getSubTags().length > 0) {
          addDescriptionsToTagEnds(tag, descritors, creator);
          return descritors;
        }
      }
      return Arrays.asList(creator.fun(Pair.create(TextRange.from(0, psiElement.getTextRange().getLength()), psiElement)));
    }

    final XmlTag tag = getParentXmlTag(domElement);
    if (tag != null) {
      addDescriptionsToTagEnds(tag, descritors, creator);
    }
    return descritors;
  }

  private static <T> void addDescriptionsToTagEnds(final XmlTag tag, final List<T> descritors, Function<Pair<TextRange, PsiElement>, T> creator) {
    final ASTNode node = tag.getNode();
    assert node != null;
    final ASTNode startNode = XmlChildRole.START_TAG_NAME_FINDER.findChild(node);
    final ASTNode endNode = XmlChildRole.CLOSING_TAG_NAME_FINDER.findChild(node);

    final int startOffset = tag.getTextRange().getStartOffset();
    descritors.add(creator.fun(Pair.create(startNode.getTextRange().shiftRight(-startOffset), (PsiElement)tag)));

    if (endNode != null && !startNode.equals(endNode)) {
      descritors.add(creator.fun(Pair.create(endNode.getTextRange().shiftRight(-startOffset), (PsiElement)tag)));
    }
  }

  @Nullable
  private static PsiElement getPsiElement(final DomElement domElement) {
    if (domElement instanceof GenericAttributeValue) {
      final GenericAttributeValue attributeValue = (GenericAttributeValue)domElement;
      final XmlAttributeValue value = attributeValue.getXmlAttributeValue();
      return value != null && StringUtil.isNotEmpty(value.getText()) ? value : attributeValue.getXmlElement();
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
