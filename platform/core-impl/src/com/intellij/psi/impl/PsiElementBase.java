
/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.psi.impl;

import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public abstract class PsiElementBase extends ElementBase implements NavigatablePsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiElementBase");

  @Override
  public PsiElement getFirstChild() {
    PsiElement[] children = getChildren();
    if (children.length == 0) return null;
    return children[0];
  }

  @Override
  public PsiElement getLastChild() {
    PsiElement[] children = getChildren();
    if (children.length == 0) return null;
    return children[children.length - 1];
  }

  @Override
  public PsiElement getNextSibling() {
    return SharedPsiElementImplUtil.getNextSibling(this);
  }

  @Override
  public PsiElement getPrevSibling() {
    return SharedPsiElementImplUtil.getPrevSibling(this);
  }

  @Override
  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      child.accept(visitor);
      child = child.getNextSibling();
    }
  }

  @Override
  public PsiReference getReference() {
    return null;
  }

  @Override
  @NotNull
  public PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  @Override
  public PsiReference findReferenceAt(int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(this, offset);
  }

  @Override
  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException("Operation not supported in: " + getClass());
  }

  @Override
  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException("Operation not supported in: " + getClass());
  }

  @Override
  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException("Operation not supported in: " + getClass());
  }

  @Override
  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException("Operation not supported in: " + getClass());
  }

  @Override
  public PsiElement copy() {
    return (PsiElement)clone();
  }

  @Override
  public PsiElement add(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public PsiElement addBefore(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public PsiElement addAfter(@NotNull PsiElement element, PsiElement anchor) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void checkAdd(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void delete() throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public void checkDelete() throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public PsiElement replace(@NotNull PsiElement newElement) throws IncorrectOperationException {
    throw new UnsupportedOperationException(getClass().getName());
  }

  @Override
  public boolean textContains(char c) {
    return getText().indexOf(c) >= 0;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return true;
  }

  @Override
  public PsiElement getContext() {
    return getParent();
  }

  @Override
  @NotNull
  public PsiElement getNavigationElement() {
    return this;
  }

  @Override
  public PsiElement getOriginalElement() {
    return this;
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope() {
    return ResolveScopeManager.getElementResolveScope(this);
  }

  @Override
  @NotNull
  public SearchScope getUseScope() {
    return ResolveScopeManager.getElementUseScope(this);
  }

  @Override
  public void navigate(boolean requestFocus) {
    final Navigatable descriptor = PsiNavigationSupport.getInstance().getDescriptor(this);
    if (descriptor != null) {
      descriptor.navigate(requestFocus);
    }
  }

  @Override
  public boolean canNavigate() {
    return PsiNavigationSupport.getInstance().canNavigate(this);
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Override
  @NotNull
  public Project getProject() {
    final PsiManager manager = getManager();
    if (manager == null) {
      throw new PsiInvalidElementAccessException(this);
    }

    return manager.getProject();
  }

  //default implementations of methods from NavigationItem
  @Override
  public ItemPresentation getPresentation() {
    return null;
  }

  @Override
  public boolean isEquivalentTo(final PsiElement another) {
    return this == another;
  }  

  @Override
  public PsiFile getContainingFile() {
    final PsiElement parent = getParent();
    if (parent == null) throw new PsiInvalidElementAccessException(this);
    return parent.getContainingFile();
  }

  @Override
  public boolean isPhysical() {
    final PsiElement parent = getParent();
    return parent != null && parent.isPhysical();
  }

  @Override
  public boolean isWritable() {
    final PsiElement parent = getParent();
    return parent != null && parent.isWritable();
  }

  @Override
  public boolean isValid() {
    PsiElement parent = getParent();
    while (parent != null && parent.getClass() == this.getClass()) {
      parent = parent.getParent();
    }
    return parent != null && parent.isValid();
  }

  //Q: get rid of these methods?
  @Override
  public boolean textMatches(@NotNull CharSequence text) {
    return Comparing.equal(getText(), text, true);
  }

  @Override
  public boolean textMatches(@NotNull PsiElement element) {
    return getText().equals(element.getText());
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    visitor.visitElement(this);
  }

  @Override
  public String getName() {
    return null;
  }

  @NotNull
  protected <T> T notNullChild(T child) {
    if (child == null) {
      LOG.error(getText() + "\n parent=" + getParent().getText());
    }
    return child;
  }

  @NotNull
  protected <T> T[] findChildrenByClass(Class<T> aClass) {
    List<T> result = new ArrayList<T>();
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (aClass.isInstance(cur)) result.add((T)cur);
    }
    return result.toArray((T[]) Array.newInstance(aClass, result.size()));
  }

  @Nullable
  protected <T> T findChildByClass(Class<T> aClass) {
    for (PsiElement cur = getFirstChild(); cur != null; cur = cur.getNextSibling()) {
      if (aClass.isInstance(cur)) return (T)cur;
    }
    return null;
  }

  @NotNull
  protected <T> T findNotNullChildByClass(Class<T> aClass) {
    return notNullChild(findChildByClass(aClass));
  }

  @Override
  public PsiManager getManager() {
    return PsiManager.getInstance(getProject());
  }
}
