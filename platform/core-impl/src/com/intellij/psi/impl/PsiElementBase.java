
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
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public abstract class PsiElementBase extends ElementBase implements NavigatablePsiElement {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.PsiElementBase");

  public PsiElement getFirstChild() {
    PsiElement[] children = getChildren();
    if (children.length == 0) return null;
    return children[0];
  }

  public PsiElement getLastChild() {
    PsiElement[] children = getChildren();
    if (children.length == 0) return null;
    return children[children.length - 1];
  }

  public PsiElement getNextSibling() {
    return SharedPsiElementImplUtil.getNextSibling(this);
  }

  public PsiElement getPrevSibling() {
    return SharedPsiElementImplUtil.getPrevSibling(this);
  }

  public void acceptChildren(@NotNull PsiElementVisitor visitor) {
    PsiElement child = getFirstChild();
    while (child != null) {
      child.accept(visitor);
      child = child.getNextSibling();
    }
  }

  public PsiReference getReference() {
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    return SharedPsiElementImplUtil.getReferences(this);
  }

  public PsiReference findReferenceAt(int offset) {
    return SharedPsiElementImplUtil.findReferenceAt(this, offset);
  }

  public PsiElement addRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException("Operation not supported in: " + getClass());
  }

  public PsiElement addRangeBefore(@NotNull PsiElement first, @NotNull PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException("Operation not supported in: " + getClass());
  }

  public PsiElement addRangeAfter(PsiElement first, PsiElement last, PsiElement anchor)
    throws IncorrectOperationException {
    throw new IncorrectOperationException("Operation not supported in: " + getClass());
  }

  public void deleteChildRange(PsiElement first, PsiElement last) throws IncorrectOperationException {
    throw new IncorrectOperationException("Operation not supported in: " + getClass());
  }

  public boolean textContains(char c) {
    return getText().indexOf(c) >= 0;
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return true;
  }

  public PsiElement getContext() {
    return getParent();
  }

  @NotNull
  public PsiElement getNavigationElement() {
    return this;
  }

  public PsiElement getOriginalElement() {
    return this;
  }

  @NotNull
  public GlobalSearchScope getResolveScope() {
    return ResolveScopeManager.getElementResolveScope(this);
  }

  @NotNull
  public SearchScope getUseScope() {
    return ResolveScopeManager.getElementUseScope(this);
  }

  public void navigate(boolean requestFocus) {
    final Navigatable descriptor = PsiNavigationSupport.getInstance().getDescriptor(this);
    if (descriptor != null) {
      descriptor.navigate(requestFocus);
    }
  }

  public boolean canNavigate() {
    return PsiNavigationSupport.getInstance().canNavigate(this);
  }

  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @NotNull
  public Project getProject() {
    final PsiManager manager = getManager();
    if (manager == null) {
      throw new PsiInvalidElementAccessException(this);
    }

    return manager.getProject();
  }

  //default implementations of methods from NavigationItem
  public ItemPresentation getPresentation() {
    return null;
  }

  public boolean isEquivalentTo(final PsiElement another) {
    return this == another;
  }  

  public PsiFile getContainingFile() {
    final PsiElement parent = getParent();
    if (parent == null) throw new PsiInvalidElementAccessException(this);
    return parent.getContainingFile();
  }

  public boolean isPhysical() {
    final PsiElement parent = getParent();
    return parent != null && parent.isPhysical();
  }

  public boolean isWritable() {
    final PsiElement parent = getParent();
    return parent != null && parent.isWritable();
  }

  public boolean isValid() {
    final PsiElement parent = getParent();
    return parent != null && parent.isValid();
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
}
