/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.util.xml.highlighting;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.reflect.DomCollectionChildDescription;
import org.jetbrains.annotations.Nullable;

public class DomElementAnnotationHolderImpl extends SmartList<DomElementProblemDescriptor> implements DomElementAnnotationHolder {

  public DomElementProblemDescriptor createProblem(DomElement domElement, @Nullable String message) {
    return createProblem(domElement, HighlightSeverity.ERROR, message);
  }

  public DomElementProblemDescriptor createProblem(DomElement domElement, DomCollectionChildDescription childDescription, @Nullable String message) {
    return addProblem(new DomCollectionProblemDescriptorImpl(domElement, message, HighlightSeverity.ERROR, childDescription));
  }

  public final DomElementProblemDescriptor createProblem(DomElement domElement, HighlightSeverity highlightType, String message) {
    return addProblem(new DomElementProblemDescriptorImpl(domElement, message, highlightType));
  }

  public DomElementProblemDescriptor addProblem(final DomElementProblemDescriptor problemDescriptor) {
    add(problemDescriptor);
    return problemDescriptor;
  }

}
