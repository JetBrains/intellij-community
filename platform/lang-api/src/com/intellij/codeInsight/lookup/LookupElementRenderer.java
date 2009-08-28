/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

/**
 * @author peter
 */
public abstract class LookupElementRenderer<T extends LookupElement> {
  public abstract void renderElement(final T element, LookupElementPresentation presentation);
}
