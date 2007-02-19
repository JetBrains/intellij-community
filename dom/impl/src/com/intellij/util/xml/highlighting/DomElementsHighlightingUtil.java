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
import com.intellij.psi.xml.*;
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

  private DomElementsHighlightingUtil() {
  }

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
    final List<T> descriptors = new SmartList<T>();

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
      descriptors.add(creator.fun(Pair.create(errorRange, element)));
      return descriptors;
    }

    final DomElement domElement = problemDescriptor.getDomElement();
    final PsiElement psiElement = getPsiElement(domElement);
    final DomElementProblemDescriptor.HighlightingType highlightingType = problemDescriptor.getHighlightingType();

    if (highlightingType == DomElementProblemDescriptor.HighlightingType.WHOLE_ELEMENT) {
      if (psiElement instanceof XmlAttributeValue) {
        final PsiElement attr = psiElement.getParent();
        descriptors.add(creator.fun(Pair.create(new TextRange(0, attr.getTextLength()), attr)));
      } else {
        final XmlTag tag = (XmlTag)(psiElement instanceof XmlTag ? psiElement : psiElement.getParent());
        descriptors.add(creator.fun(Pair.create(new TextRange(0, tag.getTextLength()), (PsiElement)tag)));
      }
      return descriptors;
    }
    
    if (psiElement != null && StringUtil.isNotEmpty(psiElement.getText())) {
      if (psiElement instanceof XmlTag) {
        final XmlTag tag = (XmlTag)psiElement;
        switch (highlightingType) {
          case WHOLE_ELEMENT:
            descriptors.add(creator.fun(Pair.create(new TextRange(0, tag.getTextLength()), (PsiElement)tag)));
            break;
          case START_TAG_NAME:
            addDescriptionsToTagEnds(tag, descriptors, creator);
            break;
        }

        return descriptors;
      }
      int start = 0;
      int length = psiElement.getTextRange().getLength();
      if (psiElement instanceof XmlAttributeValue) {
        String value = ((XmlAttributeValue)psiElement).getValue();
        if (StringUtil.isNotEmpty(value)) {
          start = psiElement.getText().indexOf(value);
          length = value.length();
        }
      }
      return Arrays.asList(creator.fun(Pair.create(TextRange.from(start, length), psiElement)));
    }

    final XmlTag tag = getParentXmlTag(domElement);
    if (tag != null) {
      addDescriptionsToTagEnds(tag, descriptors, creator);
    }
    return descriptors;
  }

  private static <T> void addDescriptionsToTagEnds(final XmlTag tag, final List<T> descriptors, Function<Pair<TextRange, PsiElement>, T> creator) {
    final ASTNode node = tag.getNode();
    assert node != null;
    final ASTNode startNode = XmlChildRole.START_TAG_NAME_FINDER.findChild(node);

    final int startOffset = tag.getTextRange().getStartOffset();
    assert startNode != null;
    descriptors.add(creator.fun(Pair.create(startNode.getTextRange().shiftRight(-startOffset), (PsiElement)tag)));
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
