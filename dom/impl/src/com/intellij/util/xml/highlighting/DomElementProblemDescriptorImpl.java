/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.DomElement;
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
  private HighlightingType myHighlightingType = HighlightingType.START_TAG_NAME;
  private TextRange myTextRange;

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
    myTextRange = textRange;
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
      myAnnotations = DomElementsHighlightingUtil.createAnnotations(this);
    }
    return myAnnotations;
  }

  public void setHighlightingType(HighlightingType highlightingType) {
    myHighlightingType = highlightingType;
  }

  public HighlightingType getHighlightingType() {
    return myHighlightingType;
  }

  @Nullable
  public TextRange getTextRange() {
      return myTextRange;
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
}
