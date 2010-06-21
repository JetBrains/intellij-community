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

/**
 *
 */
public abstract class LightElement extends PsiElementBase {
  protected final PsiManager myManager;
  private final Language myLanguage;
  protected volatile PsiElement myNavigationElement = this;

  protected LightElement(PsiManager manager, final Language language) {
    myManager = manager;
    myLanguage = language;
  }

  @NotNull
  public Language getLanguage() {
    return myLanguage;
  }

  public PsiManager getManager(){
    return myManager;
  }

  public PsiElement getParent(){
    return null;
  }

  @NotNull
  public PsiElement[] getChildren(){
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiFile getContainingFile(){
    return null;
  }

  public TextRange getTextRange(){
    return null;
  }

  public int getStartOffsetInParent(){
    return -1;
  }

  public final int getTextLength(){
    String text = getText();
    return text != null ? text.length() : 0;
  }

  @NotNull
  public char[] textToCharArray(){
    return getText().toCharArray();
  }

  public boolean textMatches(@NotNull CharSequence text) {
    return getText().equals(text.toString());
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return getText().equals(element.getText());
  }

  public PsiElement findElementAt(int offset) {
    return null;
  }

  public int getTextOffset(){
    return -1;
  }

  public boolean isValid() {
    if (myNavigationElement != this) {
      //return myNavigationElement.isValid();
    }

    return true;
  }

  public boolean isWritable(){
    return false;
  }

  public boolean isPhysical() {
    return false;
  }

  public abstract String toString();

  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public void delete() throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public void checkDelete() throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException{
    throw new IncorrectOperationException();
  }

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

  /*
  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myNavigationElement;
  }
                                   */
  public LightElement setNavigationElement(PsiElement navigationElement) {
    myNavigationElement = navigationElement;
    return this;
  }
  
}
