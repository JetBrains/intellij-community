// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiReferenceBase<T extends PsiElement> implements PsiReference {
  private static final Logger LOG = Logger.getInstance(PsiReferenceBase.class);

  protected final T myElement;
  private TextRange myRangeInElement;
  protected boolean mySoft;

  /**
   * @param element        Underlying element.
   * @param rangeInElement Reference range {@link PsiReference#getRangeInElement() relative to given element}.
   * @param soft           Whether reference {@link PsiReference#isSoft() may fail to resolve}.
   */
  public PsiReferenceBase(@NotNull T element, TextRange rangeInElement, boolean soft) {
    myElement = element;
    myRangeInElement = rangeInElement;
    mySoft = soft;
  }

  /**
   * @param element        Underlying element.
   * @param rangeInElement Reference range {@link PsiReference#getRangeInElement() relative to given element}.
   */
  public PsiReferenceBase(@NotNull T element, TextRange rangeInElement) {
    this(element);
    myRangeInElement = rangeInElement;
  }

  /**
   * Reference range is obtained from {@link ElementManipulator#getRangeInElement(PsiElement)}.
   *
   * @param element Underlying element.
   * @param soft    Whether reference {@link PsiReference#isSoft() may fail to resolve}.
   */
  public PsiReferenceBase(@NotNull T element, boolean soft) {
    myElement = element;
    mySoft = soft;
  }

  /**
   * Reference range is obtained from {@link ElementManipulator#getRangeInElement(PsiElement)}.
   *
   * @param element Underlying element.
   */
  public PsiReferenceBase(@NotNull T element) {
    myElement = element;
    mySoft = false;
  }

  public void setRangeInElement(TextRange rangeInElement) {
    myRangeInElement = rangeInElement;
  }

  public @NotNull @NlsSafe String getValue() {
    String text = myElement.getText();
    final TextRange range = getRangeInElement();
    try {
      return range.substring(text);
    }
    catch (StringIndexOutOfBoundsException e) {
      LOG.error("Wrong range in reference " + this + ": " + range + ". Reference text: '" + text + "'", e);
      return text;
    }
  }

  @NotNull
  @Override
  public T getElement() {
    return myElement;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    TextRange rangeInElement = myRangeInElement;
    if (rangeInElement == null) {
      myRangeInElement = rangeInElement = calculateDefaultRangeInElement();
    }
    return rangeInElement;
  }

  protected TextRange calculateDefaultRangeInElement() {
    return getManipulator().getRangeInElement(myElement);
  }

  @Override
  public @NotNull @NlsSafe String getCanonicalText() {
    return getValue();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    return getManipulator().handleContentChange(myElement, getRangeInElement(), newElementName);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    Class<?> aClass = getClass();
    throw new IncorrectOperationException("Rebind cannot be performed for " + aClass,
                                          (Throwable)PluginException.createByClass("", null, aClass));
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return getElement().getManager().areElementsEquivalent(resolve(), element);
  }

  public static <T extends PsiElement> @NotNull PsiReferenceBase<T> createSelfReference(T element, final PsiElement resolveTo) {
    return new Immediate<>(element, true, resolveTo);
  }

  public static <T extends PsiElement> @NotNull PsiReferenceBase<T> createSelfReference(T element,
                                                                                        TextRange rangeInElement,
                                                                                        final PsiElement resolveTo) {
    return new Immediate<>(element, rangeInElement, resolveTo);
  }

  @NotNull
  private ElementManipulator<T> getManipulator() {
    ElementManipulator<T> manipulator = ElementManipulators.getManipulator(myElement);
    if (manipulator == null) {
      throw PluginException.createByClass(
        "No ElementManipulator instance registered for " + myElement + " [" + myElement.getClass() + "]" +
        " in " + this + " [" + getClass() + "]", null, myElement.getClass());
    }
    return manipulator;
  }

  @Override
  public boolean isSoft() {
    return mySoft;
  }

  public abstract static class Poly<T extends PsiElement> extends PsiReferenceBase<T> implements PsiPolyVariantReference {
    public Poly(final T psiElement) {
      super(psiElement);
    }

    public Poly(final T element, final boolean soft) {
      super(element, soft);
    }

    public Poly(final T element, final TextRange rangeInElement, final boolean soft) {
      super(element, rangeInElement, soft);
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
      final ResolveResult[] results = multiResolve(false);
      for (ResolveResult result : results) {
        if (element.getManager().areElementsEquivalent(result.getElement(), element)) {
          return true;
        }
      }
      return false;
    }

    @Override
    @Nullable
    public PsiElement resolve() {
      ResolveResult[] resolveResults = multiResolve(false);
      return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }
  }

  public static class Immediate<T extends PsiElement> extends PsiReferenceBase<T> {
    private final PsiElement myResolveTo;

    public Immediate(T element, TextRange rangeInElement, boolean soft, PsiElement resolveTo) {
      super(element, rangeInElement, soft);
      myResolveTo = resolveTo;
    }

    public Immediate(T element, TextRange rangeInElement, PsiElement resolveTo) {
      super(element, rangeInElement);
      myResolveTo = resolveTo;
    }

    public Immediate(T element, boolean soft, PsiElement resolveTo) {
      super(element, soft);
      myResolveTo = resolveTo;
    }

    public Immediate(@NotNull T element, PsiElement resolveTo) {
      super(element);
      myResolveTo = resolveTo;
    }

    //do nothing. the element will be renamed via PsiMetaData (com.intellij.refactoring.rename.RenameUtil.doRenameGenericNamedElement())
    @Override
    public PsiElement handleElementRename(@NotNull final String newElementName) throws IncorrectOperationException {
      return getElement();
    }

    @Override
    @Nullable
    public PsiElement resolve() {
      return myResolveTo;
    }
  }

  @Override
  public String toString() {
    return getClass().getName() + "(" + myElement + ":" + myRangeInElement + ")";
  }
}