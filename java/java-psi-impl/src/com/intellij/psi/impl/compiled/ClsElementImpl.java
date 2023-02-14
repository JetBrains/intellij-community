// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.compiled;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaFileCodeStyleFacade;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.smartPointers.Identikit;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class ClsElementImpl extends PsiElementBase implements PsiCompiledElement {
  public static final Key<PsiCompiledElement> COMPILED_ELEMENT = Key.create("COMPILED_ELEMENT");

  private volatile Pair<TextRange, Identikit.ByType> myMirror;

  @Override
  public @NotNull Language getLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  public PsiManager getManager() {
    return getParent().getManager();
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

  protected PsiElement @NotNull [] getChildren(PsiElement @Nullable ... children) {
    if (children == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    List<PsiElement> list = new ArrayList<>(children.length);
    for (PsiElement child : children) {
      if (child != null) {
        list.add(child);
      }
    }
    return PsiUtilCore.toPsiElementArray(list);
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  static @NotNull IncorrectOperationException cannotModifyException(@NotNull PsiCompiledElement element) {
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(element);
    String path = virtualFile == null ? "?" : virtualFile.getPresentableUrl();
    return new IncorrectOperationException(JavaPsiBundle.message("psi.error.attempt.to.edit.class.file", path));
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public void delete() throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    throw cannotModifyException(this);
  }

  public abstract void appendMirrorText(int indentLevel, @NotNull StringBuilder buffer);

  protected int getIndentSize() {
    return JavaFileCodeStyleFacade.forContext(getContainingFile()).getIndentSize();
  }

  public abstract void setMirror(@NotNull TreeElement element) throws InvalidMirrorException;

  @Override
  public PsiElement getMirror() {
    PsiFile mirrorFile = ((ClsFileImpl)getContainingFile()).getMirror().getContainingFile();
    Pair<TextRange, Identikit.ByType> mirror = myMirror;
    return mirror == null ? null : mirror.second.findPsiElement(mirrorFile, mirror.first.getStartOffset(), mirror.first.getEndOffset());
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
    return text == null ? 0 : text.length();
  }

  @Override
  public PsiElement findElementAt(int offset) {
    PsiElement mirror = getMirror();
    return mirror != null ? mirror.findElementAt(offset) : null;
  }

  @Override
  public PsiReference findReferenceAt(int offset) {
    PsiElement mirror = getMirror();
    return mirror != null ? mirror.findReferenceAt(offset) : null;
  }

  @Override
  public final int getTextOffset() {
    PsiElement mirror = getMirror();
    return mirror != null ? mirror.getTextOffset() : -1;
  }

  @Override
  public String getText() {
    PsiElement mirror = getMirror();
    if (mirror != null) return mirror.getText();

    StringBuilder buffer = new StringBuilder();
    appendMirrorText(0, buffer);
    Logger.getInstance(ClsElementImpl.class).warn("Mirror wasn't set for " + this + " in " + getContainingFile() + ", expected text '" + buffer + "'");
    return buffer.toString();
  }

  @Override
  public char @NotNull [] textToCharArray() {
    PsiElement mirror = getMirror();
    return mirror != null ? mirror.textToCharArray() : ArrayUtilRt.EMPTY_CHAR_ARRAY;
  }

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    return getText().equals(text.toString());
  }

  @Override
  public ASTNode getNode() {
    return null;
  }

  static void goNextLine(int indentLevel, @NotNull StringBuilder buffer) {
    buffer.append('\n');
    for (int i = 0; i < indentLevel; i++) buffer.append(' ');
  }

  protected static void appendText(@Nullable PsiElement stub, int indentLevel, @NotNull StringBuilder buffer) {
    if (stub == null) return;
    ((ClsElementImpl)stub).appendMirrorText(indentLevel, buffer);
  }

  protected static final String NEXT_LINE = "go_to_next_line_and_indent";

  protected static void appendText(@Nullable PsiElement stub, int indentLevel, @NotNull StringBuilder buffer, @NotNull String separator) {
    if (stub == null) return;
    int pos = buffer.length();
    ((ClsElementImpl)stub).appendMirrorText(indentLevel, buffer);
    if (buffer.length() != pos) {
      if (Strings.areSameInstance(separator, NEXT_LINE)) {
        goNextLine(indentLevel, buffer);
      }
      else {
        buffer.append(separator);
      }
    }
  }

  protected void setMirrorCheckingType(@NotNull TreeElement element, @Nullable IElementType type) throws InvalidMirrorException {
    // uncomment for extended consistency check
    //if (myMirror != null) throw new InvalidMirrorException("Mirror should be null: " + myMirror);

    if (type != null && element.getElementType() != type) {
      throw new InvalidMirrorException(element.getElementType() + " != " + type);
    }

    PsiElement psi = element.getPsi();
    psi.putUserData(COMPILED_ELEMENT, this);
    myMirror = Pair.create(element.getTextRange(), Identikit.fromPsi(psi, JavaLanguage.INSTANCE));
  }

  protected static <T extends  PsiElement> void setMirror(@Nullable T stub, @Nullable T mirror) throws InvalidMirrorException {
    if (stub == null || mirror == null) {
      throw new InvalidMirrorException(stub, mirror);
    }
    ((ClsElementImpl)stub).setMirror(SourceTreeToPsiMap.psiToTreeNotNull(mirror));
  }

  protected static <T extends  PsiElement> void setMirrorIfPresent(@Nullable T stub, @Nullable T mirror) throws InvalidMirrorException {
    if ((stub == null) != (mirror == null)) {
      throw new InvalidMirrorException(stub, mirror);
    }
    else if (stub != null) {
      ((ClsElementImpl)stub).setMirror(SourceTreeToPsiMap.psiToTreeNotNull(mirror));
    }
  }

  protected static <T extends  PsiElement> void setMirrors(T @NotNull [] stubs, T @NotNull [] mirrors) throws InvalidMirrorException {
    setMirrors(Arrays.asList(stubs), Arrays.asList(mirrors));
  }

  protected static <T extends  PsiElement> void setMirrors(@NotNull List<? extends T> stubs, @NotNull List<? extends T> mirrors) throws InvalidMirrorException {
    if (stubs.size() != mirrors.size()) {
      throw new InvalidMirrorException(stubs, mirrors);
    }
    for (int i = 0; i < stubs.size(); i++) {
      setMirror(stubs.get(i), mirrors.get(i));
    }
  }

  protected static class InvalidMirrorException extends RuntimeException {
    public InvalidMirrorException(@NotNull String message) {
      super(message);
    }

    public InvalidMirrorException(@Nullable PsiElement stubElement, @Nullable PsiElement mirrorElement) {
      this("stub:" + stubElement + "; mirror:" + mirrorElement);
    }

    public InvalidMirrorException(@NotNull List<? extends PsiElement> stubElements, @NotNull List<? extends PsiElement> mirrorElements) {
      this("stub:" + stubElements + "; mirror:" + mirrorElements);
    }
  }
}
