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

import com.intellij.core.JavaCoreBundle;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
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

  private volatile TreeElement myMirror = null;

  @Override
  @NotNull
  public Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public PsiManager getManager() {
    return getParent().getManager();
  }

  @Override
  public PsiFile getContainingFile() {
    PsiElement parent = getParent();
    if (parent == null) {
      if (!isValid()) throw new PsiInvalidElementAccessException(this);
      return null;
    }
    return parent.getContainingFile();
  }

  @Override
  public final boolean isWritable() {
    return false;
  }

  @Override
  public boolean isPhysical() {
    return true;
  }

  @Override
  public boolean isValid() {
    PsiElement parent = getParent();
    return parent != null && parent.isValid();
  }

  @Override
  public PsiElement copy() {
    return this;
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException(CAN_NOT_MODIFY_MESSAGE);
  }

  protected static final String CAN_NOT_MODIFY_MESSAGE = JavaCoreBundle.message("psi.error.attempt.to.edit.class.file");

  public abstract void appendMirrorText(final int indentLevel, final StringBuilder buffer);

  protected static void goNextLine(int indentLevel, StringBuilder buffer) {
    buffer.append('\n');
    for (int i = 0; i < indentLevel; i++) buffer.append(' ');
  }

  protected int getIndentSize() {
    return JavaCodeStyleSettingsFacade.getInstance(getProject()).getIndentSize();
  }

  public abstract void setMirror(@NotNull TreeElement element);

  @Override
  public PsiElement getMirror() {
    TreeElement mirror = myMirror;
    if (mirror == null) {
      ((ClsFileImpl)getContainingFile()).getMirror();
      mirror = myMirror;
    }
    return SourceTreeToPsiMap.treeElementToPsi(mirror);
  }

  @Override
  public final TextRange getTextRange() {
    PsiElement mirror = getMirror();
    return mirror != null ? mirror.getTextRange() : TextRange.EMPTY_RANGE;
  }

  @Override
  public final int getStartOffsetInParent() {
    PsiElement mirror = getMirror();
    return mirror != null ? mirror.getStartOffsetInParent() : -1;
  }

  @Override
  public int getTextLength() {
    String text = getText();
    if (text == null){
      LOG.error("getText() == null, element = " + this + ", parent = " + getParent());
      return 0;
    }
    return text.length();
  }

  @Override
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

  @Override
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

  @Override
  public final int getTextOffset() {
    PsiElement mirror = getMirror();
    return mirror != null ? mirror.getTextOffset() : -1;
  }

  @Override
  public String getText() {
    PsiElement mirror = getMirror();
    return mirror != null ? mirror.getText() : null;
  }

  @Override
  @NotNull
  public char[] textToCharArray() {
    return getMirror().textToCharArray();
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
