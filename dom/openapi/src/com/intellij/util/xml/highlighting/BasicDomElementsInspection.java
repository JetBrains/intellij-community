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

  protected boolean shouldCheckResolveProblems(GenericDomValue value) {
    return true;
  }

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
