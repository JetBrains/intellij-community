/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiReferenceBase<T extends PsiElement> implements PsiReference {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.PsiReferenceBase");

  protected final T myElement;
  private TextRange myRangeInElement;
  protected boolean mySoft;

  /**
   * @param element PSI element
   * @param rangeInElement range relatively to the element's start offset
   * @param soft soft
   */
  public PsiReferenceBase(@NotNull T element, TextRange rangeInElement, boolean soft) {
    myElement = element;
    myRangeInElement = rangeInElement;
    mySoft = soft;
  }

  /**
   * @param element PSI element
   * @param rangeInElement range relatively to the element's start offset
   */
  public PsiReferenceBase(@NotNull T element, TextRange rangeInElement) {
    this(element);
    myRangeInElement = rangeInElement;
  }

  /**
   * The range is obtained from {@link ElementManipulators}
   * @param element PSI element
   * @param soft soft
   */
  public PsiReferenceBase(@NotNull T element, boolean soft) {
    myElement = element;
    mySoft = soft;
  }

  /**
   * The range is obtained from {@link ElementManipulators}
   * @param element PSI element
   */
  public PsiReferenceBase(@NotNull T element) {
    myElement = element;
    mySoft = false;
  }

  public void setRangeInElement(TextRange rangeInElement) {
    myRangeInElement = rangeInElement;
  }

  @NotNull
  public String getValue() {
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
    if (myRangeInElement == null) {
      myRangeInElement = calculateDefaultRangeInElement();
    }
    return myRangeInElement;
  }

  protected TextRange calculateDefaultRangeInElement() {
    return getManipulator().getRangeInElement(myElement);
  }

  @Override
  @NotNull
  public String getCanonicalText() {
    return getValue();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return getManipulator().handleContentChange(myElement, getRangeInElement(), newElementName);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("Rebind cannot be performed for " + getClass());
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return getElement().getManager().areElementsEquivalent(resolve(), element);
  }

  public static <T extends PsiElement> PsiReferenceBase<T> createSelfReference(T element, final PsiElement resolveTo) {
    return new Immediate<>(element, true, resolveTo);
  }

  public static <T extends PsiElement> PsiReferenceBase<T> createSelfReference(T element, TextRange rangeInElement, final PsiElement resolveTo) {
    return new Immediate<>(element, rangeInElement, resolveTo);
  }

  private ElementManipulator<T> getManipulator() {
    ElementManipulator<T> manipulator = ElementManipulators.getManipulator(myElement);
    if (manipulator == null) {
      LOG.error("Cannot find manipulator for " + myElement + " in " + this + " class " + getClass());
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
    public boolean isReferenceTo(PsiElement element) {
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
    public PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException {
      return getElement();
    }

    @Override
    @Nullable
    public PsiElement resolve() {
      return myResolveTo;
    }

    @Override
    @NotNull
    public Object[] getVariants() {
      return EMPTY_ARRAY;
    }
  }

  @Override
  public String toString() {
    return myElement + ":" + myRangeInElement;
  }
}