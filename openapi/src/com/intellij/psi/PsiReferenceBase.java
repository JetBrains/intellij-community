/*
 * Copyright 2000-2006 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public abstract class PsiReferenceBase<T extends PsiElement> implements PsiReference {

  protected final T myElement;
  private TextRange myRange;
  protected boolean mySoft;

  public PsiReferenceBase(T element, TextRange range, boolean soft) {
    this(element, range);
    mySoft = soft;
  }

  public PsiReferenceBase(T element, TextRange range) {
    this(element);
    myRange = range;
  }

  public PsiReferenceBase(T element, boolean soft) {
    this(element);
    mySoft = soft;
  }

  public PsiReferenceBase(T element) {
    myElement = element;
  }

  public void setRangeInElement(TextRange range) {
    myRange = range;
  }

  @NotNull
  public String getValue() {
    String text = myElement.getText();
    return getRangeInElement().substring(text);
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
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public static <T extends PsiElement> PsiReferenceBase<T> createSelfReference(T element, final PsiElement resolveTo) {

    return new PsiReferenceBase<T>(element,true) {

      @Nullable
      public PsiElement resolve() {
        return resolveTo;
      }

      public Object[] getVariants() {
        return EMPTY_ARRAY;
      }
    };
  }

  @Nullable
  public Module getModule() {
    return ModuleUtil.findModuleForPsiElement(myElement);
  }

  @Nullable
  ElementManipulator<T> getManipulator() {
    return PsiManager.getInstance(myElement.getProject()).getElementManipulatorsRegistry().getManipulator(myElement);
  }

  public boolean isSoft() {
    return mySoft;
  }

  public static abstract class Poly<T extends PsiElement> extends PsiReferenceBase<T> implements PsiPolyVariantReference {

  public Poly(final T psiElement) {
    super(psiElement);
  }

  @Nullable
  public PsiElement resolve() {
    ResolveResult[] resolveResults = multiResolve(false);
    return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
  }
}
}
