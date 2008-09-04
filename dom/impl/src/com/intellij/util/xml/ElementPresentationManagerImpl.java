/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.util.xml;

import com.intellij.codeInsight.lookup.LookupValueWithPsiElement;
import com.intellij.codeInsight.lookup.LookupValueWithUIHint;
import com.intellij.codeInsight.lookup.PresentableLookupValue;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class ElementPresentationManagerImpl extends ElementPresentationManager {

  @NotNull
  public <T> Object[] createVariants(Collection<T> elements, Function<T, String> namer, int iconFlags) {
    ArrayList<Object> result = new ArrayList<Object>(elements.size());
    for (T element: elements) {
      String name = namer.fun(element);
      if (name != null) {
        Object value = createVariant(element, name, null);
        result.add(value);
      }
    }
    return result.toArray();
  }

  public Object createVariant(final Object variant, final String name, final PsiElement psiElement) {
    return new DomVariant(variant, name, psiElement);
  }

  private static class DomVariant implements PresentableLookupValue, Iconable, LookupValueWithUIHint, LookupValueWithPsiElement {

    private final Object myVariant;
    private final String myName;
    private PsiElement myElement;

    public DomVariant(final Object variant, String name, final PsiElement element) {
      myVariant = variant;
      myName = name;
      myElement = element;
    }

    public String getPresentation() {
      return myName;
    }

    @Nullable
    public Icon getIcon(final int flags) {
      return ElementPresentationManager.getIcon(myVariant);
    }

    @Nullable
    public String getTypeHint() {
      return ElementPresentationManager.getHintForElement(myVariant);
    }

    @Nullable
    public Color getColorHint() {
      return null;
    }

    public boolean isBold() {
      return false;
    }

    @Nullable
    public PsiElement getElement() {
      return myElement;
    }
  }
}
