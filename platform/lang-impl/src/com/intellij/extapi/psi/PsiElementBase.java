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

package com.intellij.extapi.psi;

import com.intellij.ide.util.EditSourceUtil;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.ElementBase;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.SharedPsiElementImplUtil;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public abstract class PsiElementBase extends ElementBase implements NavigatablePsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.extapi.psi.PsiElementBase");

  public PsiElement copy() {
    return (PsiElement)clone();
  }

  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void delete() throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void checkDelete() throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public PsiReference getReference() {
    return null;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState state, PsiElement lastParent, @NotNull PsiElement place) {
    return true;
  }

  @NotNull
  public Project getProject() {
    final PsiManager manager = getManager();
    if (manager == null) throw new PsiInvalidElementAccessException(this);

    return manager.getProject();
  }

  public PsiManager getManager() {
    final PsiElement parent = getParent();
    return parent != null ? parent.getManager() : null;
  }

  public PsiFile getContainingFile() {
    final PsiElement parent = getParent();
    if (parent == null) throw new PsiInvalidElementAccessException(this);
    return parent.getContainingFile();
  }

  public PsiReference findReferenceAt(int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(this, offset);
  }

  @NotNull
  public PsiElement getNavigationElement() {
    return this;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  //Q: get rid of these methods?
  public boolean textMatches(@NotNull CharSequence text) {
    return Comparing.equal(getText(), text, true);
  }

  public boolean textMatches(@NotNull PsiElement element) {
    return getText().equals(element.getText());
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      child.accept(visitor);
      child = child.getNextSibling();
    }
  }

  public boolean isValid() {
    final PsiElement parent = getParent();
    return parent != null && parent.isValid();
  }

  public boolean isWritable() {
    final PsiElement parent = getParent();
    return parent != null && parent.isWritable();
  }

  @NotNull
  public PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  public PsiElement getContext() {
    return getParent();
  }

  public boolean isPhysical() {
    final PsiElement parent = getParent();
    return parent != null && parent.isPhysical();
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return ((PsiManagerEx)getManager()).getFileManager().getResolveScope(this);
  }

  @NotNull
  public SearchScope getUseScope() {
    return ((PsiManagerEx) getManager()).getFileManager().getUseScope(this);
  }

  /**
   * Returns the UI presentation data for the PSI element.
   *
   * @return null, unless overridden in a subclass. 
   */
  public ItemPresentation getPresentation() {
    return null;
  }

  public String getName() {
    return null;
  }

  public void navigate(boolean requestFocus) {
    final Navigatable descriptor = EditSourceUtil.getDescriptor(this);
    if (descriptor != null) descriptor.navigate(requestFocus);
  }

  public boolean canNavigate() {
    return true;
  }

  public boolean canNavigateToSource() {
    final Navigatable descriptor = EditSourceUtil.getDescriptor(this);
    return descriptor != null && descriptor.canNavigateToSource();
  }

  public FileStatus getFileStatus() {
    if (!isPhysical()) return FileStatus.NOT_CHANGED;
    PsiFile contFile = getContainingFile();
    if (contFile == null) return FileStatus.NOT_CHANGED;
    VirtualFile vFile = contFile.getVirtualFile();
    return vFile != null ? FileStatusManager.getInstance(getProject()).getStatus(vFile) : FileStatus.NOT_CHANGED;
  }

  @NotNull
  protected <T> T[] findChildrenByClass(Class<T> aClass) {
    List<T> result = new ArrayList<T>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (ReflectionCache.isInstance(cur, aClass)) result.add((T)cur);
    }
    return result.toArray((T[]) Array.newInstance(aClass, result.size()));
  }

  @Nullable
  protected <T> T findChildByClass(Class<T> aClass) {
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (ReflectionCache.isInstance(cur, aClass)) return (T)cur;
    }
    return null;
  }

  @NotNull
  protected <T> T findNotNullChildByClass(Class<T> aClass) {
    return notNullChild(findChildByClass(aClass));
  }

  @NotNull
  protected <T> T notNullChild(T child) {
    if (child == null) {
      LOG.error(getText() + "\n parent=" + getParent().getText());
    }
    return child;
  }

  public boolean isEquivalentTo(final PsiElement another) {
    return this == another;
  }  
}
