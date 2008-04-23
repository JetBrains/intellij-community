/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.xml.highlighting;

import com.intellij.util.xml.DomElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * With my help you can plug into default dom inspection (see {@link com.intellij.util.xml.highlighting.BasicDomElementsInspection})
 * with custom DOM elements checking based on custom user-defined annotations on those DOM elements
 *
 * @author peter
 */
public abstract class DomCustomAnnotationChecker<T extends Annotation> {
  public static final ExtensionPointName<DomCustomAnnotationChecker> EP_NAME = ExtensionPointName.create("com.intellij.dom.customAnnotationChecker");
  
  @NotNull
  public abstract Class<T> getAnnotationClass();

  public abstract List<DomElementProblemDescriptor> checkForProblems(@NotNull T t, @NotNull DomElement element, @NotNull DomElementAnnotationHolder holder, @NotNull DomHighlightingHelper helper);
}
