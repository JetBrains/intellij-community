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
package com.intellij.psi.impl.compiled;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ClsElementImpl extends PsiElementBase implements PsiCompiledElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.compiled.ClsElementImpl");
  private static final boolean CHECK_MIRROR_ENABLED = false;
  protected static final Object LAZY_BUILT_LOCK = new String("lazy cls tree initialization lock");
  public static final Key<PsiCompiledElement> COMPILED_ELEMENT = Key.create("COMPILED_ELEMENT");

  private TreeElement myMirror = null;

  @NotNull
  public Language getLanguage() {
    return StdFileTypes.JAVA.getLanguage();
  }

  public PsiManager getManager() {
    return getParent().getManager();
  }

  public PsiFile getContainingFile() {
    PsiElement parent = getParent();
    if (parent == null) {
      if (!isValid()) throw new PsiInvalidElementAccessException(this);
      return null;
    }
    return parent.getContainingFile();
  }

  public final boolean isWritable() {
    return false;
  }

  public boolean isPhysical() {
    return true;
  }

  public boolean isValid() {
    PsiElement parent = getParent();
    return parent != null && parent.isValid();
  }

  public PsiElement copy() {
    return this;
  }

  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public void delete() throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public void checkDelete() throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  protected static final String CAN_NOT_MODIFY_MESSAGE = PsiBundle.message("psi.error.attempt.to.edit.class.file");

  public abstract void appendMirrorText(final int indentLevel, final StringBuilder buffer);

  protected static void goNextLine(int indentLevel, StringBuilder buffer) {
    buffer.append('\n');
    for (int i = 0; i < indentLevel; i++) buffer.append(' ');
  }

  protected int getIndentSize() {
    return CodeStyleSettingsManager.getSettings(getProject()).getIndentSize(StdFileTypes.JAVA);
  }

  public abstract void setMirror(@NotNull TreeElement element);

  public PsiElement getMirror() {
    synchronized (ClsFileImpl.MIRROR_LOCK) {
      if (myMirror == null) {
        final ClsFileImpl file = (ClsFileImpl)getContainingFile();
        file.getMirror();
      }
      return SourceTreeToPsiMap.treeElementToPsi(myMirror);
    }
  }

  public final TextRange getTextRange() {
    PsiElement mirror = getMirror();
    return mirror != null ? mirror.getTextRange() : TextRange.EMPTY_RANGE;
  }

  public final int getStartOffsetInParent() {
    PsiElement mirror = getMirror();
    return mirror != null ? mirror.getStartOffsetInParent() : -1;
  }

  public int getTextLength() {
    String text = getText();
    if (text == null){
      LOG.error("getText() == null, element = " + this + ", parent = " + getParent());
      return 0;
    }
    return text.length();
  }

  public PsiElement findElementAt(int offset) {
    PsiElement mirrorAt = getMirror().findElementAt(offset);
    while(true){
      if (mirrorAt == null) return null;
      PsiElement elementAt = mirrorToElement(mirrorAt);
      if (elementAt != null) return elementAt;
      mirrorAt = mirrorAt.getParent();
    }

    /*
    PsiElement[] children = getChildren();
    if (children.length == 0) return this;
    for(int i = 0; i < children.length; i++){
      int start = children[i].getStartOffsetInParent();
      if (offset < start) return null;
      int end = start + children[i].getTextLength();
      if (offset < end){
        return children[i].findElementAt(offset - start);
      }
    }
    return null;
    */
  }

  public PsiReference findReferenceAt(int offset) {
    PsiReference mirrorRef = getMirror().findReferenceAt(offset);
    if (mirrorRef == null) return null;
    PsiElement mirrorElement = mirrorRef.getElement();
    PsiElement element = mirrorToElement(mirrorElement);
    if (element == null) return null;
    return element.getReference();
  }

  private PsiElement mirrorToElement(PsiElement mirror) {
    final PsiElement m = getMirror();
    if (m == mirror) return this;

    PsiElement[] children = getChildren();
    if (children.length == 0) return null;

    for (PsiElement child : children) {
      ClsElementImpl clsChild = (ClsElementImpl)child;
      if (PsiTreeUtil.isAncestor(clsChild.getMirror(), mirror, false)) {
        PsiElement element = clsChild.mirrorToElement(mirror);
        if (element != null) return element;
      }
    }

    return null;
  }

  public final int getTextOffset() {
    PsiElement mirror = getMirror();
    return mirror != null ? mirror.getTextOffset() : -1;
  }

  public String getText() {
    PsiElement mirror = getMirror();
    return mirror != null ? mirror.getText() : null;
  }

  @NotNull
  public char[] textToCharArray() {
    return getMirror().textToCharArray();
  }

  public boolean textMatches(@NotNull CharSequence text) {
    return getText().equals(text.toString());
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return getText().equals(element.getText());
  }

  @NotNull
  public PsiElement getNavigationElement() {
    return this;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  public ASTNode getNode() {
    return null;
  }

  protected void setMirrorCheckingType(@NotNull TreeElement element, @Nullable IElementType type) {
    if (CHECK_MIRROR_ENABLED) {
      LOG.assertTrue(myMirror == null);
    }

    if (type != null) {
      LOG.assertTrue(element.getElementType() == type, element.getElementType() + " != " + type);
    }

    element.putUserData(COMPILED_ELEMENT, this);
    myMirror = element;
  }
}
