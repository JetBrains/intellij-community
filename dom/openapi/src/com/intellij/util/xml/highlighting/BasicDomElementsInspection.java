/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml.highlighting;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NotNull;

/**
 * User: Sergey.Vasiliev
 */
public abstract class BasicDomElementsInspection<T extends DomElement> extends DomElementsInspection<T> {

  public BasicDomElementsInspection(@NotNull Class<? extends T> domClass, Class<? extends T>... additionalClasses) {
    super(domClass, additionalClasses);
  }

  /**
   * One may want to create several inspections that check for unresolved DOM references of different types. Through this method one can control
   * these types. 
   * @param value GenericDomValue containing references in question
   * @return whether to check for resolve problems
   */
  protected boolean shouldCheckResolveProblems(GenericDomValue value) {
    return true;
  }

  /**
   * The default implementations checks for resolve problems (if {@link #shouldCheckResolveProblems(com.intellij.util.xml.GenericDomValue)} returns true),
   * then runs annotators (see {@link com.intellij.util.xml.DomFileDescription#createAnnotator()}),
   * checks for {@link @com.intellij.util.xml.Required} and {@link @com.intellij.util.xml.ExtendClass} annotation problems, checks
   * for name identity (see {@link @com.intellij.util.xml.NameValue} annotation).
   * @param element element to check
   * @param holder a place to add problems to
   * @param helper helper object
   */
  protected void checkDomElement(DomElement element, DomElementAnnotationHolder holder, DomHighlightingHelper helper) {
    final int oldSize = holder.getSize();
    if (element instanceof GenericDomValue) {
      final GenericDomValue genericDomValue = (GenericDomValue)element;
      if (shouldCheckResolveProblems(genericDomValue)) {
        helper.checkResolveProblems(genericDomValue, holder);
      }
    }
    for (final Class<? extends T> aClass : getDomClasses()) {
      helper.runAnnotators(element, holder, aClass);
    }
    if (oldSize != holder.getSize()) return;

    if (!helper.checkRequired(element, holder).isEmpty()) return;
    if (element instanceof GenericDomValue) {
      helper.checkExtendClass((GenericDomValue)element, holder);
    } else {
      helper.checkNameIdentity(element, holder);
    }
  }

}
