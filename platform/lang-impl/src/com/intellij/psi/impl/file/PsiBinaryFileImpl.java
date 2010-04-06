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

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.CheckUtil;
import com.intellij.psi.impl.PsiElementBase;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

public class PsiBinaryFileImpl extends PsiElementBase implements PsiBinaryFile, Cloneable, Queryable {
  private final PsiManagerImpl myManager;
  private String myName; // for myFile == null only
  private byte[] myContents; // for myFile == null only
  private final long myModificationStamp;
  private final FileType myFileType;
  private final FileViewProvider myViewProvider;

  public PsiBinaryFileImpl(PsiManagerImpl manager, FileViewProvider viewProvider) {
    myViewProvider = viewProvider;
    myManager = manager;
    final VirtualFile virtualFile = myViewProvider.getVirtualFile();
    myModificationStamp = virtualFile.getModificationStamp();
    myFileType = viewProvider.getVirtualFile().getFileType();
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myViewProvider.getVirtualFile();
  }

  public boolean processChildren(final PsiElementProcessor<PsiFileSystemItem> processor) {
    return true;
  }

  public byte[] getStoredContents() {
    return myContents;
  }

  @NotNull
  public String getName() {
    return !isCopy() ? getVirtualFile().getName() : myName;
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);

    if (isCopy()){
      myName = name;
      return this; // not absolutely correct - might change type
    }

    return PsiFileImplUtil.setName(this, name);
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    if (isCopy()) return;
    PsiFileImplUtil.checkSetName(this, name);
  }

  public boolean isDirectory() {
    return false;
  }

  public PsiDirectory getContainingDirectory() {
    VirtualFile parentFile = getVirtualFile().getParent();
    if (parentFile == null) return null;
    return getManager().findDirectory(parentFile);
  }

  @Nullable
  public PsiDirectory getParentDirectory() {
    return getContainingDirectory();
  }

  public long getModificationStamp() {
    return myModificationStamp;
  }

  @NotNull
  public Language getLanguage() {
    return Language.ANY;
  }

  public PsiManager getManager() {
    return myManager;
  }

  @NotNull
  public PsiElement[] getChildren() {
    return PsiElement.EMPTY_ARRAY;
  }

  public PsiDirectory getParent() {
    return getContainingDirectory();
  }

  public PsiFile getContainingFile() {
    return this;
  }

  public TextRange getTextRange() {
    return null;
  }

  public int getStartOffsetInParent() {
    return -1;
  }

  public int getTextLength() {
    return -1;
  }

  public PsiElement findElementAt(int offset) {
    return null;
  }

  public int getTextOffset() {
    return -1;
  }

  public String getText() {
    return ""; // TODO throw new InsupportedOperationException()
  }

  @NotNull
  public char[] textToCharArray() {
    return ArrayUtil.EMPTY_CHAR_ARRAY; // TODO throw new InsupportedOperationException()
  }

  public boolean textMatches(@NotNull CharSequence text) {
    return false;
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return false;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitBinaryFile(this);
  }

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

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public void delete() throws IncorrectOperationException{
    checkDelete();
    PsiFileImplUtil.doDelete(this);
  }

  public void checkDelete() throws IncorrectOperationException{
    if (isCopy()){
      throw new IncorrectOperationException();
    }
    CheckUtil.checkWritable(this);
  }

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    return null;
  }

  public boolean isValid() {
    if (isCopy()) return true; // "dummy" file
    return getVirtualFile().isValid() && !myManager.getProject().isDisposed() && myManager.getFileManager().findFile(getVirtualFile()) == this;
  }

  public boolean isWritable() {
    return isCopy() || getVirtualFile().isWritable();
  }

  public boolean isPhysical() {
    return !isCopy();
  }

  @NotNull
  public PsiFile getOriginalFile() {
    return this;
  }

  @NonNls
  public String toString() {
    return "PsiBinaryFile:" + getName();
  }

  @NotNull
  public FileType getFileType() {
    return myFileType;
  }

  @NotNull
  public PsiFile[] getPsiRoots() {
    return new PsiFile[]{this};
  }

  @NotNull
  public FileViewProvider getViewProvider() {
    return myViewProvider;
  }

  public ASTNode getNode() {
    return null; // TODO throw new InsupportedOperationException()
  }

  public void subtreeChanged() {
  }

  @Override
  public PsiElement getContext() {
    return FileContextUtil.getFileContext(this);
  }

  public void putInfo(Map<String, String> info) {
    info.put("fileName", getName());
    info.put("fileType", getFileType().getName());
  }
}
