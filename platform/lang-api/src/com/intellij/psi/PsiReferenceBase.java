/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
  private TextRange myRange;
  protected boolean mySoft;

  /**
   * @param element PSI element
   * @param range range relatively to the element's start offset
   * @param soft soft
   */
  public PsiReferenceBase(T element, TextRange range, boolean soft) {
    myElement = element;
    myRange = range;
    mySoft = soft;
  }

  /**
   * @param element PSI element
   * @param range range relatively to the element's start offset
   */
  public PsiReferenceBase(T element, TextRange range) {
    this(element);
    myRange = range;
  }

  /**
   * The range is obtained from {@link ElementManipulators}
   * @param element PSI element
   * @param soft soft
   */
  public PsiReferenceBase(T element, boolean soft) {
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

  public void setRangeInElement(TextRange range) {
    myRange = range;
  }

  @NotNull
  public String getValue() {
    String text = myElement.getText();
    final TextRange range = getRangeInElement();
    if (range.getEndOffset() > text.length() || range.getStartOffset() > text.length() || range.getStartOffset() < 0 || range.getEndOffset() < 0) {
      LOG.error("Wrong range in reference " + this + ": " + range + ". Reference text: '" + text + "'");
      return text;
    }
    return range.substring(text);
  }


  public T getElement() {
    return myElement;
  }

  public TextRange getRangeInElement() {
    if (myRange == null) {
      myRange = calculateDefaultRangeInElement();
    }
    return myRange;
  }

  protected TextRange calculateDefaultRangeInElement() {
    final ElementManipulator<T> manipulator = getManipulator();
    assert manipulator != null: "Cannot find manipulator for " + myElement;
    return manipulator.getRangeInElement(myElement);
  }

  @NotNull
  public String getCanonicalText() {
    return getValue();
  }

  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final ElementManipulator<T> manipulator = getManipulator();
    assert manipulator != null: "Cannot find manipulator for " + myElement;
    return manipulator.handleContentChange(myElement, getRangeInElement(), newElementName);
  }                                                              

  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("Rebind cannot be performed for " + getClass());
  }

  public boolean isReferenceTo(PsiElement element) {
    return getElement().getManager().areElementsEquivalent(resolve(), element);
  }

  public static <T extends PsiElement> PsiReferenceBase<T> createSelfReference(T element, final PsiElement resolveTo) {
    return new Immediate<T>(element, true, resolveTo);
  }

  public static <T extends PsiElement> PsiReferenceBase<T> createSelfReference(T element, TextRange range, final PsiElement resolveTo) {
    return new Immediate<T>(element, range, resolveTo);
  }

  @Nullable
  ElementManipulator<T> getManipulator() {
    return ElementManipulators.getManipulator(myElement);
  }

  public boolean isSoft() {
    return mySoft;
  }

  public static abstract class Poly<T extends PsiElement> extends PsiReferenceBase<T> implements PsiPolyVariantReference {

    public Poly(final T psiElement) {
      super(psiElement);
    }

    public Poly(final T element, final boolean soft) {
      super(element, soft);
    }

    public Poly(final T element, final TextRange range, final boolean soft) {
      super(element, range, soft);
    }

    public boolean isReferenceTo(PsiElement element) {
      final ResolveResult[] results = multiResolve(false);
      for (ResolveResult result : results) {
        if (element.getManager().areElementsEquivalent(result.getElement(), element)) {
          return true;
        }
      }
      return false;
    }

    @Nullable
    public PsiElement resolve() {
      ResolveResult[] resolveResults = multiResolve(false);
      return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }
  }

  public static class Immediate<T extends PsiElement> extends PsiReferenceBase<T> {
    private final PsiElement myResolveTo;

    public Immediate(T element, TextRange range, boolean soft, PsiElement resolveTo) {
      super(element, range, soft);
      myResolveTo = resolveTo;
    }

    public Immediate(T element, TextRange range, PsiElement resolveTo) {
      super(element, range);
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
    public PsiElement handleElementRename(final String newElementName) throws IncorrectOperationException {
      return getElement();
    }

    @Nullable
    public PsiElement resolve() {
      return myResolveTo;
    }

    @NotNull
    public Object[] getVariants() {
      return EMPTY_ARRAY;
    }
  }
}
