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

package com.intellij.psi.impl.source;

import com.intellij.ide.caches.FileContent;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.*;
import com.intellij.psi.impl.file.PsiFileImplUtil;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.impl.source.tree.SharedImplUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class LightPsiFileImpl extends PsiElementBase implements PsiFileEx {

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.LightPsiFileImpl");
  private PsiFile myOriginalFile = null;
  private boolean myExplicitlySetAsValid = false;
  private final FileViewProvider myViewProvider;
  private final PsiManagerImpl myManager;
  private final Language myLanguage;

  public LightPsiFileImpl(final FileViewProvider provider, final Language language) {
    myViewProvider = provider;
    myManager = (PsiManagerImpl)provider.getManager();
    myLanguage = language;
  }

  public VirtualFile getVirtualFile() {
    return getViewProvider().isEventSystemEnabled() ? getViewProvider().getVirtualFile() : null;
  }

  public boolean processChildren(final PsiElementProcessor<PsiFileSystemItem> processor) {
    return true;
  }

  public boolean isValid() {
    if (!getViewProvider().isPhysical() || myExplicitlySetAsValid) return true; // "dummy" file
    return getViewProvider().getVirtualFile().isValid();
  }

  public void setIsValidExplicitly(boolean b) {
    LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode());
    myExplicitlySetAsValid = b;
  }

  public String getText() {
    return getViewProvider().getContents().toString();
  }

  public PsiElement getNextSibling() {
    return SharedPsiElementImplUtil.getNextSibling(this);
  }

  public PsiElement getPrevSibling() {
    return SharedPsiElementImplUtil.getPrevSibling(this);
  }

  public long getModificationStamp() {
    return getViewProvider().getModificationStamp();
  }

  public void subtreeChanged() {
    clearCaches();
    getViewProvider().rootChanged(this);
  }

  public abstract void clearCaches();

  @SuppressWarnings({"CloneDoesntDeclareCloneNotSupportedException"})
  protected LightPsiFileImpl clone() {
    final FileViewProvider provider = getViewProvider().clone();
    final LightPsiFileImpl clone = (LightPsiFileImpl)provider.getPsi(getLanguage());

    copyCopyableDataTo(clone);

    if (getViewProvider().isEventSystemEnabled()) {
      clone.myOriginalFile = this;
    }
    else if (myOriginalFile != null) {
      clone.myOriginalFile = myOriginalFile;
    }
    return clone;
  }

  @NotNull
  public String getName() {
    return getViewProvider().getVirtualFile().getName();
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    checkSetName(name);
    subtreeChanged();
    return PsiFileImplUtil.setName(this, name);
  }

  public void checkSetName(String name) throws IncorrectOperationException {
    if (!getViewProvider().isEventSystemEnabled()) return;
    PsiFileImplUtil.checkSetName(this, name);
  }

  public PsiDirectory getParent() {
    return getContainingDirectory();
  }

  public PsiDirectory getContainingDirectory() {
    final VirtualFile parentFile = getViewProvider().getVirtualFile().getParent();
    if (parentFile == null) return null;
    return getManager().findDirectory(parentFile);
  }

  @Nullable
  public PsiDirectory getParentDirectory() {
    return getContainingDirectory();
  }

  public PsiFile getContainingFile() {
    return this;
  }

  public void delete() throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public void checkDelete() throws IncorrectOperationException {
    if (!getViewProvider().isEventSystemEnabled()) {
      throw new IncorrectOperationException();
    }
    CheckUtil.checkWritable(this);
  }

  @NotNull
  public PsiFile getOriginalFile() {
    return myOriginalFile == null ? this : myOriginalFile;
  }

  public void setOriginalFile(final PsiFile originalFile) {
    myOriginalFile = originalFile.getOriginalFile();
  }

  @NotNull
  public PsiFile[] getPsiRoots() {
    return new PsiFile[]{this};
  }

  public boolean isPhysical() {
    return getViewProvider().isEventSystemEnabled();
  }

  @NotNull
  public Language getLanguage() {
    return myLanguage;
  }

  @NotNull
  public FileViewProvider getViewProvider() {
    return myViewProvider;
  }

  public PsiManager getManager() {
    return myManager;
  }

  @NotNull
  public Project getProject() {
    final PsiManager manager = getManager();
    if (manager == null) throw new PsiInvalidElementAccessException(this);

    return manager.getProject();
  }

  public PsiElement getNavigationElement() {
    return this;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      final PsiElement nextSibling = child.getNextSibling();
      child.accept(visitor);
      child = nextSibling;
    }
  }

  public synchronized final PsiElement copy() {
    return clone();
  }

  public final void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    CheckUtil.checkWritable(this);
  }

  @NotNull
  public synchronized PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return true;
  }

  @NotNull
  public SearchScope getUseScope() {
    return ((PsiManagerEx) getManager()).getFileManager().getUseScope(this);
  }

  // Default implementation just to make sure it compiles.
  public ItemPresentation getPresentation() {
    return null;
  }

  public FileStatus getFileStatus() {
    return SharedImplUtil.getFileStatus(this);
  }

  public void navigate(boolean requestFocus) {
    EditSourceUtil.getDescriptor(this).navigate(requestFocus);
  }

  public boolean canNavigate() {
    return EditSourceUtil.canNavigate(this);
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  public synchronized PsiElement findElementAt(int offset) {
    return getViewProvider().findElementAt(offset);
  }

  public synchronized PsiReference findReferenceAt(int offset) {
    return getViewProvider().findReferenceAt(offset);
  }

  @NotNull
  public char[] textToCharArray() {
    return CharArrayUtil.fromSequenceStrict(getViewProvider().getContents());
  }

  public boolean isContentsLoaded() {
    return true;
  }

  public void onContentReload() {    
  }

  public PsiFile cacheCopy(final FileContent content) {
    return this;
  }

  public boolean isWritable() {
    return getViewProvider().getVirtualFile().isWritable();
  }

  @NotNull
  public abstract PsiElement[] getChildren();

  public PsiElement getFirstChild() {
    final PsiElement[] children = getChildren();
    return children.length == 0 ? null : children[0];
  }

  public PsiElement getLastChild() {
    final PsiElement[] children = getChildren();
    return children.length == 0 ? null : children[children.length - 1];
  }

  public TextRange getTextRange() {
    return new TextRange(0, getTextLength());
  }

  public int getStartOffsetInParent() {
    return 0;
  }

  public int getTextLength() {
    return getViewProvider().getContents().length();
  }

  public int getTextOffset() {
    return 0;
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return textMatches(element.getText());
  }

  public boolean textMatches(@NotNull CharSequence text) {
    return text.equals(getViewProvider().getContents());
  }

  public boolean textContains(char c) {
    return getText().indexOf(c) >= 0;
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public final PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public final PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    throw new IncorrectOperationException("Not implemented");
  }

  public PsiReference getReference() {
    return null;
  }

  public ASTNode getNode() {
    return null;
  }

  public abstract LightPsiFileImpl copyLight(final FileViewProvider viewProvider);

  @Override
  public PsiElement getContext() {
    return FileContextUtil.getFileContext(this);
  }
}
