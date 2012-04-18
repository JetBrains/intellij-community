/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

package com.intellij.psi.impl.light;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public abstract class LightElement extends PsiElementBase {
  protected final PsiManager myManager;
  private final Language myLanguage;
  private volatile PsiElement myNavigationElement = this;

  protected LightElement(PsiManager manager, final Language language) {
    myManager = manager;
    myLanguage = language;
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return myLanguage;
  }

  @Override
  public PsiManager getManager() {
    return myManager;
  }

  @Override
  public PsiElement getParent() {
    return null;
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiFile getContainingFile() {
    return null;
  }

  @Override
  public TextRange getTextRange() {
    return null;
  }

  @Override
  public int getStartOffsetInParent() {
    return -1;
  }

  @Override
  public final int getTextLength() {
    String text = getText();
    return text != null ? text.length() : 0;
  }

  @Override
  @NotNull
  public char[] textToCharArray() {
    return getText().toCharArray();
  }

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    return getText().equals(text.toString());
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return getText().equals(element.getText());
  }

  @Override
  public PsiElement findElementAt(int offset) {
    return null;
  }

  @Override
  public int getTextOffset() {
    return -1;
  }

  @Override
  public boolean isValid() {
    final PsiElement navElement = getNavigationElement();
    if (navElement != this) {
      return navElement.isValid();
    }

    return true;
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isPhysical() {
    return false;
  }

  public abstract String toString();

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public void delete() throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException(getClass().getName());
  }

  @Override
  public ASTNode getNode() {
    return null;
  }

  @Override
  public String getText() {
    return null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
  }

  @Override
  public PsiElement copy() {
    return null;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myNavigationElement;
  }

  public void setNavigationElement(@NotNull PsiElement navigationElement) {
    PsiElement nnElement = navigationElement.getNavigationElement();
    if (nnElement != navigationElement && nnElement != null) {
      navigationElement = nnElement;
    }
    myNavigationElement = navigationElement;
  }

  @Override
  public PsiElement getPrevSibling() {
    return null;
  }

  @Override
  public PsiElement getNextSibling() {
    return null;
  }

}
