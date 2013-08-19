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
package com.intellij.psi.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LocalSearchScope extends SearchScope {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.LocalSearchScope");

  @NotNull
  private final PsiElement[] myScope;
  private final boolean myIgnoreInjectedPsi;

  public static final LocalSearchScope EMPTY = new LocalSearchScope(PsiElement.EMPTY_ARRAY);
  private String myDisplayName;

  public LocalSearchScope(@NotNull PsiElement scope) {
    this(scope, null);
  }

  public LocalSearchScope(@NotNull PsiElement scope, @Nullable String displayName) {
    this(new PsiElement[]{scope});
    myDisplayName = displayName;
  }

  public LocalSearchScope(@NotNull PsiElement[] scope) {
    this(scope, null);
  }

  public LocalSearchScope(@NotNull PsiElement[] scope, @Nullable String displayName) {
    this(scope, displayName, false);
  }

  public LocalSearchScope(@NotNull PsiElement[] scope, @Nullable final String displayName, final boolean ignoreInjectedPsi) {
    myIgnoreInjectedPsi = ignoreInjectedPsi;
    myDisplayName = displayName;
    Set<PsiElement> localScope = new LinkedHashSet<PsiElement>(scope.length);

    for (final PsiElement element : scope) {
      LOG.assertTrue(element != null, "null element");
      LOG.assertTrue(element.getContainingFile() != null, element.getClass().getName());
      if (element instanceof PsiFile) {
        for (PsiFile file : ((PsiFile)element).getViewProvider().getAllFiles()) {
          if (file == null) throw new IllegalArgumentException("file "+element+" returned null in its getAllFiles()");
          localScope.add(file);
        }
      }
      else if (element instanceof StubBasedPsiElement || element.getTextRange() != null){
        localScope.add(element);
      }
    }
    myScope = PsiUtilCore.toPsiElementArray(localScope);
  }

  public boolean isIgnoreInjectedPsi() {
    return myIgnoreInjectedPsi;
  }

  @Override
  public String getDisplayName() {
    return myDisplayName == null ? super.getDisplayName() : myDisplayName;
  }

  @NotNull
  public PsiElement[] getScope() {
    return myScope;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LocalSearchScope)) return false;

    final LocalSearchScope localSearchScope = (LocalSearchScope)o;

    if (localSearchScope.myIgnoreInjectedPsi != myIgnoreInjectedPsi) return false;
    if (localSearchScope.myScope.length != myScope.length) return false;
    for (final PsiElement scopeElement : myScope) {
      final PsiElement[] thatScope = localSearchScope.myScope;
      for (final PsiElement thatScopeElement : thatScope) {
        if (!Comparing.equal(scopeElement, thatScopeElement)) return false;
      }
    }


    return true;
  }

  public int hashCode() {
    int result = 0;
    result += myIgnoreInjectedPsi? 1 : 0;
    for (final PsiElement element : myScope) {
      result += element.hashCode();
    }
    return result;
  }

  @NotNull public LocalSearchScope intersectWith(@NotNull LocalSearchScope scope2){
    if (equals(scope2)) return this;
    return intersection(this, scope2);
  }

  private static LocalSearchScope intersection(LocalSearchScope scope1, LocalSearchScope scope2) {
    List<PsiElement> result = new ArrayList<PsiElement>();
    final PsiElement[] elements1 = scope1.myScope;
    final PsiElement[] elements2 = scope2.myScope;
    for (final PsiElement element1 : elements1) {
      for (final PsiElement element2 : elements2) {
        final PsiElement element = intersectScopeElements(element1, element2);
        if (element != null) {
          result.add(element);
        }
      }
    }
    return new LocalSearchScope(PsiUtilCore.toPsiElementArray(result), null, scope1.myIgnoreInjectedPsi || scope2.myIgnoreInjectedPsi);
  }

  @NotNull
  @Override
  public SearchScope intersectWith(@NotNull SearchScope scope2) {
    if (scope2 instanceof LocalSearchScope) {
      return intersectWith((LocalSearchScope)scope2);
    }
    LocalSearchScope nonPhysicalScope = tryIntersectNonPhysicalWith((GlobalSearchScope)scope2);
    if (nonPhysicalScope != null) return nonPhysicalScope;
    return ((GlobalSearchScope)scope2).intersectWith(this);
  }

  @Nullable
  private LocalSearchScope tryIntersectNonPhysicalWith(@NotNull GlobalSearchScope scope) {
    Project project = scope.getProject();
    for (PsiElement element : myScope) {
      PsiFile containingFile = element.getContainingFile();
      if (containingFile == null) continue;
      if (containingFile.getViewProvider().isPhysical()) return null;
      if (project != null && project != containingFile.getProject()) {
        return EMPTY;
      }
    }
    return this;
  }

  @Nullable
  private static PsiElement intersectScopeElements(PsiElement element1, PsiElement element2) {
    if (PsiTreeUtil.isContextAncestor(element1, element2, false)) return element2;
    if (PsiTreeUtil.isContextAncestor(element2, element1, false)) return element1;
    if (PsiTreeUtil.isAncestor(element1, element2, false)) return element2;
    if (PsiTreeUtil.isAncestor(element2, element1, false)) return element1;
    return null;
  }

  public String toString() {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < myScope.length; i++) {
      final PsiElement element = myScope[i];
      if (i > 0) {
        result.append(",");
      }
      result.append(element.toString());
    }
    //noinspection HardCodedStringLiteral
    return "LocalSearchScope:" + result;
  }

  @Override
  @NotNull
  public SearchScope union(@NotNull SearchScope scope) {
    if (scope instanceof LocalSearchScope) return union((LocalSearchScope)scope);
    return ((GlobalSearchScope)scope).union(this);
  }

  public SearchScope union(LocalSearchScope scope2) {
    if (equals(scope2)) return this;
    PsiElement[] elements1 = getScope();
    PsiElement[] elements2 = scope2.getScope();
    boolean[] united = new boolean[elements2.length];
    List<PsiElement> result = new ArrayList<PsiElement>();
    loop1:
    for (final PsiElement element1 : elements1) {
      for (int j = 0; j < elements2.length; j++) {
        final PsiElement element2 = elements2[j];
        final PsiElement unionElement = scopeElementsUnion(element1, element2);
        if (unionElement != null && unionElement.getContainingFile() != null) {
          result.add(unionElement);
          united[j] = true;
          break loop1;
        }
      }
      result.add(element1);
    }
    for (int i = 0; i < united.length; i++) {
      final boolean b = united[i];
      if (!b) {
        result.add(elements2[i]);
      }
    }
    return new LocalSearchScope(PsiUtilCore.toPsiElementArray(result));
  }

  private static PsiElement scopeElementsUnion(PsiElement element1, PsiElement element2) {
    if (PsiTreeUtil.isAncestor(element1, element2, false)) return element1;
    if (PsiTreeUtil.isAncestor(element2, element1, false)) return element2;
    PsiElement commonParent = PsiTreeUtil.findCommonParent(element1, element2);
    if (commonParent == null) return null;
    return commonParent;
  }

  public boolean isInScope(VirtualFile file) {
    for (PsiElement element : myScope) {
      PsiFile containingFile = element.getContainingFile();
      if (containingFile == null) continue;
      if (Comparing.equal(containingFile.getVirtualFile(), file)) return true;
    }
    return false;
  }

  public boolean containsRange(PsiFile file, TextRange range) {
    for (PsiElement element : getScope()) {
      if (file == element.getContainingFile() && element.getTextRange().contains(range)) {
        return true;
      }
    }
    return false;
  }
}
