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

package com.intellij.psi.impl.file;

import com.intellij.lang.FileASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.ArrayUtil;
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
  private final long myModificationStamp;
  private final FileViewProvider myViewProvider;
  private boolean myInvalidated;

  public PsiBinaryFileImpl(PsiManagerImpl manager, FileViewProvider viewProvider) {
    myViewProvider = viewProvider;
    myManager = manager;
    final VirtualFile virtualFile = myViewProvider.getVirtualFile();
    myModificationStamp = virtualFile.getModificationStamp();
  }

  @Override
  @NotNull
  public VirtualFile getVirtualFile() {
    return myViewProvider.getVirtualFile();
  }

  @Override
  public boolean processChildren(final PsiElementProcessor<PsiFileSystemItem> processor) {
    return true;
  }

  public byte[] getStoredContents() {
    return myContents;
  }

  @Override
  @NotNull
  public String getName() {
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

  @Nullable
  public PsiDirectory getParentDirectory() {
    return getContainingDirectory();
  }

  @Override
  public long getModificationStamp() {
    return myModificationStamp;
  }

  @Override
  @NotNull
  public Language getLanguage() {
    return Language.ANY;
  }

  @Override
  public PsiManager getManager() {
    return myManager;
  }

  @Override
  @NotNull
  public PsiElement[] getChildren() {
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
  @NotNull
  public char[] textToCharArray() {
    return ArrayUtil.EMPTY_CHAR_ARRAY; // TODO[max] throw new UnsupportedOperationException()
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
    try{
      clone.myContents = !isCopy() ? getVirtualFile().contentsToByteArray() : myContents;
    }
    catch(IOException e){
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
    return getVirtualFile().isValid() && !myManager.getProject().isDisposed() && !myInvalidated;
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
  @NotNull
  public PsiFile getOriginalFile() {
    return this;
  }

  @NonNls
  public String toString() {
    return "PsiBinaryFile:" + getName();
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return myViewProvider.getFileType();
  }

  @Override
  @NotNull
  public PsiFile[] getPsiRoots() {
    return new PsiFile[]{this};
  }

  @Override
  @NotNull
  public FileViewProvider getViewProvider() {
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
  public void putInfo(@NotNull Map<String, String> info) {
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
    myInvalidated = true;
    DebugUtil.onInvalidated(this);
  }
}
