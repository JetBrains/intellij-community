// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.file;

import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.impl.FileManagerEx;
import com.intellij.psi.impl.file.impl.FileManagerImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public class PsiBinaryFileImpl extends PsiElementBase implements PsiBinaryFile, PsiFileEx, Cloneable, Queryable {
  private final PsiManagerImpl myManager;
  private String myName; // for myFile == null only
  private byte[] myContents; // for myFile == null only
  private final AbstractFileViewProvider myViewProvider;
  private volatile boolean myPossiblyInvalidated;

  public PsiBinaryFileImpl(@NotNull PsiManagerImpl manager, @NotNull FileViewProvider viewProvider) {
    myViewProvider = (AbstractFileViewProvider)viewProvider;
    myManager = manager;
  }

  @Override
  public @NotNull VirtualFile getVirtualFile() {
    return myViewProvider.getVirtualFile();
  }

  @Override
  public boolean processChildren(@NotNull PsiElementProcessor<? super PsiFileSystemItem> processor) {
    return true;
  }

  byte[] getStoredContents() {
    return myContents;
  }

  @Override
  public @NotNull String getName() {
    return !isCopy() ? getVirtualFile().getName() : myName;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);

    if (isCopy()){
      myName = name;
      return this; // not absolutely correct - might change type
    }

    return PsiFileImplUtil.setName(this, name);
  }

  @Override
  public void checkSetName(String name) throws IncorrectOperationException {
    if (isCopy()) return;
    PsiFileImplUtil.checkSetName(this, name);
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public PsiDirectory getContainingDirectory() {
    VirtualFile parentFile = getVirtualFile().getParent();
    if (parentFile == null) return null;
    return getManager().findDirectory(parentFile);
  }

  public @Nullable PsiDirectory getParentDirectory() {
    return getContainingDirectory();
  }

  @Override
  public long getModificationStamp() {
    return getVirtualFile().getModificationStamp();
  }

  @Override
  public @NotNull Language getLanguage() {
    return Language.ANY;
  }

  @Override
  public PsiManager getManager() {
    return myManager;
  }

  @Override
  public PsiElement @NotNull [] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  @Override
  public PsiDirectory getParent() {
    return getContainingDirectory();
  }

  @Override
  public PsiFile getContainingFile() {
    return this;
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
  public int getTextLength() {
    return -1;
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
  public String getText() {
    return ""; // TODO[max] throw new UnsupportedOperationException()
  }

  @Override
  public char @NotNull [] textToCharArray() {
    return ArrayUtilRt.EMPTY_CHAR_ARRAY; // TODO[max] throw new UnsupportedOperationException()
  }

  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    return false;
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitBinaryFile(this);
  }

  @Override
  public PsiElement copy() {
    PsiBinaryFileImpl clone = (PsiBinaryFileImpl)clone();
    clone.myName = getName();
    try {
      clone.myContents = !isCopy() ? getVirtualFile().contentsToByteArray() : myContents;
    }
    catch (IOException ignored) {
    }
    return clone;
  }

  private boolean isCopy() {
    return myName != null;
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void delete() throws IncorrectOperationException{
    checkDelete();
    PsiFileImplUtil.doDelete(this);
  }

  @Override
  public void checkDelete() throws IncorrectOperationException{
    if (isCopy()){
      throw new IncorrectOperationException();
    }
    CheckUtil.checkWritable(this);
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isValid() {
    if (isCopy()) return true; // "dummy" file
    if (!getVirtualFile().isValid() || myManager.getProject().isDisposed()) return false;


    if (!myPossiblyInvalidated) return true;

    // synchronized by read-write action
    if (((FileManagerEx)myManager.getFileManager()).evaluateValidity(this)) {
      myPossiblyInvalidated = false;
      PsiInvalidElementAccessException.setInvalidationTrace(this, null);
      return true;
    }
    return false;
  }

  @Override
  public boolean isWritable() {
    return isCopy() || getVirtualFile().isWritable();
  }

  @Override
  public boolean isPhysical() {
    return !isCopy();
  }

  @Override
  public @NotNull PsiFile getOriginalFile() {
    return this;
  }

  @Override
  public @NonNls String toString() {
    return "PsiBinaryFile:" + getName();
  }

  @Override
  public @NotNull FileType getFileType() {
    return myViewProvider.getFileType();
  }

  @Override
  public PsiFile @NotNull [] getPsiRoots() {
    return new PsiFile[]{this};
  }

  @Override
  public @NotNull FileViewProvider getViewProvider() {
    return myViewProvider;
  }

  @Override
  public FileASTNode getNode() {
    return null; // TODO[max] throw new UnsupportedOperationException()
  }

  @Override
  public void subtreeChanged() {
  }

  @Override
  public PsiElement getContext() {
    return FileContextUtil.getFileContext(this);
  }

  @Override
  public void putInfo(@NotNull Map<? super String, ? super String> info) {
    info.put("fileName", getName());
    info.put("fileType", getFileType().getName());
  }

  @Override
  public boolean isContentsLoaded() {
    return false;
  }

  @Override
  public void onContentReload() {
  }

  @Override
  public void markInvalidated() {
    myPossiblyInvalidated = true;
    DebugUtil.onInvalidated(this);
  }
}
