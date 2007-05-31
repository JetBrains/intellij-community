/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericValue;
import com.intellij.xml.util.XmlTagUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DomElementProblemDescriptorImpl implements DomElementProblemDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.highlighting.DomElementProblemDescriptorImpl");
  private final DomElement myDomElement;
  private final HighlightSeverity mySeverity;
  private final String myMessage;
  private final LocalQuickFix[] myFixes;
  private List<Annotation> myAnnotations;
  private Pair<TextRange, PsiElement> myPair;
  public static final Pair<TextRange,PsiElement> NO_PROBLEM = new Pair<TextRange, PsiElement>(null, null);

  public DomElementProblemDescriptorImpl(@NotNull final DomElement domElement, final String message, final HighlightSeverity type) {
    this(domElement, message, type, LocalQuickFix.EMPTY_ARRAY);
  }

  public DomElementProblemDescriptorImpl(@NotNull final DomElement domElement,
                                         final String message,
                                         final HighlightSeverity type,
                                         @Nullable final TextRange textRange) {
    this(domElement, message, type, textRange, LocalQuickFix.EMPTY_ARRAY);
  }

  public DomElementProblemDescriptorImpl(@NotNull final DomElement domElement,
                                         final String message,
                                         final HighlightSeverity type,
                                         final LocalQuickFix... fixes) {
    this(domElement, message, type, null, fixes);
  }

  public DomElementProblemDescriptorImpl(@NotNull final DomElement domElement,
                                         final String message,
                                         final HighlightSeverity type,
                                         @Nullable final TextRange textRange,
                                         final LocalQuickFix... fixes) {
    myDomElement = domElement;
    final XmlElement element = domElement.getXmlElement();
    if (element != null) {
      LOG.assertTrue(element.isPhysical(), "Problems may not be created for non-physical DOM elements");
    }
    mySeverity = type;
    myMessage = message;
    myFixes = fixes;

    if (textRange != null) {
      final PsiElement psiElement = getPsiElement();
      LOG.assertTrue(psiElement != null, "Problems with explicit text range can't be created for DOM elements without underlying XML element");
      myPair = new Pair<TextRange, PsiElement>(textRange, psiElement);
    }
  }

  @NotNull
  public DomElement getDomElement() {
    return myDomElement;
  }

  @NotNull
  public HighlightSeverity getHighlightSeverity() {
    return mySeverity;
  }

  @NotNull
  public String getDescriptionTemplate() {
    return myMessage == null ? "" : myMessage;
  }

  @NotNull
  public LocalQuickFix[] getFixes() {
    return myFixes;
  }

  @NotNull
  public final List<Annotation> getAnnotations() {
    if (myAnnotations == null) {
      myAnnotations = ContainerUtil.createMaybeSingletonList(DomElementsHighlightingUtil.createAnnotation(this));
    }
    return myAnnotations;
  }

  public void highlightWholeElement() {
    final PsiElement psiElement = getPsiElement();
    if (psiElement instanceof XmlAttributeValue) {
      final PsiElement attr = psiElement.getParent();
      myPair = Pair.create(new TextRange(0, attr.getTextLength()), attr);
    }
    else if (psiElement != null) {
      final XmlTag tag = (XmlTag)(psiElement instanceof XmlTag ? psiElement : psiElement.getParent());
      myPair = new Pair<TextRange, PsiElement>(new TextRange(0, tag.getTextLength()), tag);
    }
  }

  public Pair<TextRange,PsiElement> getProblemRange() {
    if (myPair == null) {
      myPair = computeProblemRange();
    }
    return myPair;
  }

  @NotNull
  protected Pair<TextRange,PsiElement> computeProblemRange() {
    final PsiElement element = getPsiElement();

    if (element != null) {
      if (element instanceof XmlTag) {
        return createTagNameRange((XmlTag)element);
      }

      int length = element.getTextRange().getLength();
      TextRange range = TextRange.from(0, length);
      if (element instanceof XmlAttributeValue) {
        final String value = ((XmlAttributeValue)element).getValue();
        if (StringUtil.isNotEmpty(value)) {
          range = TextRange.from(element.getText().indexOf(value), value.length());
        }
      }
      return Pair.create(range, element);
    }

    final XmlTag tag = getParentXmlTag();
    if (tag != null) {
      return createTagNameRange(tag);
    }
    return NO_PROBLEM;
  }

  private static Pair<TextRange, PsiElement> createTagNameRange(final XmlTag tag) {
    final PsiElement startToken = XmlTagUtil.getStartTagNameElement(tag);
    assert startToken != null;
    return Pair.create(startToken.getTextRange().shiftRight(-tag.getTextRange().getStartOffset()), (PsiElement)tag);
  }

  public String toString() {
    return myDomElement + "; " + myMessage;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DomElementProblemDescriptorImpl that = (DomElementProblemDescriptorImpl)o;

    if (myDomElement != null ? !myDomElement.equals(that.myDomElement) : that.myDomElement != null) return false;
    if (!myMessage.equals(that.myMessage)) return false;
    if (!mySeverity.equals(that.mySeverity)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myDomElement != null ? myDomElement.hashCode() : 0);
    result = 31 * result + mySeverity.hashCode();
    result = 31 * result + myMessage.hashCode();
    return result;
  }

  @Nullable
  private PsiElement getPsiElement() {
    if (myDomElement instanceof GenericAttributeValue) {
      final GenericAttributeValue attributeValue = (GenericAttributeValue)myDomElement;
      final XmlAttributeValue value = attributeValue.getXmlAttributeValue();
      return value != null && StringUtil.isNotEmpty(value.getText()) ? value : attributeValue.getXmlElement();
    }
    final XmlTag tag = myDomElement.getXmlTag();
    if (myDomElement instanceof GenericValue && tag != null) {
      final XmlText[] textElements = tag.getValue().getTextElements();
      if (textElements.length > 0) {
        return textElements[0];
      }
    }

    return tag;
  }

  @Nullable
  private XmlTag getParentXmlTag() {
    DomElement parent = myDomElement.getParent();
    while (parent != null) {
      if (parent.getXmlTag() != null) return parent.getXmlTag();
      parent = parent.getParent();
    }
    return null;
  }
}
