/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class LocalSearchScope extends SearchScope {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.LocalSearchScope");

  @NotNull
  private final PsiElement[] myScope;
  private final VirtualFile[] myVirtualFiles;
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
    Set<PsiElement> localScope = new LinkedHashSet<>(scope.length);
    Set<VirtualFile> virtualFiles = new THashSet<>(scope.length);
    for (final PsiElement element : scope) {
      LOG.assertTrue(element != null, "null element");
      PsiFile containingFile = element.getContainingFile();
      LOG.assertTrue(containingFile != null, element.getClass().getName());
      if (element instanceof PsiFile) {
        for (PsiFile file : ((PsiFile)element).getViewProvider().getAllFiles()) {
          if (file == null) throw new IllegalArgumentException("file "+element+" returned null in its getAllFiles()");
          localScope.add(file);
        }
      }
      else if (element instanceof StubBasedPsiElement || element.getTextRange() != null){
        localScope.add(element);
      }
      VirtualFile virtualFile = PsiUtilCore.getVirtualFile(containingFile);
      if (virtualFile != null) {
        virtualFiles.add(virtualFile);
      }
    }
    myScope = PsiUtilCore.toPsiElementArray(localScope);
    myVirtualFiles = VfsUtilCore.toVirtualFileArray(virtualFiles);
  }

  public boolean isIgnoreInjectedPsi() {
    return myIgnoreInjectedPsi;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return myDisplayName == null ? super.getDisplayName() : myDisplayName;
  }

  @NotNull
  public PsiElement[] getScope() {
    return myScope;
  }

  @NotNull
  public VirtualFile[] getVirtualFiles() {
    return myVirtualFiles;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LocalSearchScope)) return false;

    LocalSearchScope other = (LocalSearchScope)o;

    if (other.myIgnoreInjectedPsi != myIgnoreInjectedPsi) return false;
    if (other.myScope.length != myScope.length) return false;
    if (!Comparing.strEqual(myDisplayName, other.myDisplayName)) return false; // scopes like "Current file" and "Changed files" should be different event if empty

    for (final PsiElement scopeElement : myScope) {
      for (final PsiElement thatScopeElement : other.myScope) {
        if (!Comparing.equal(scopeElement, thatScopeElement)) return false;
      }
    }


    return true;
  }

  @Override
  public int calcHashCode() {
    int result = 0;
    result += myIgnoreInjectedPsi? 1 : 0;
    for (final PsiElement element : myScope) {
      result += element.hashCode();
    }
    return result;
  }

  @NotNull
  public LocalSearchScope intersectWith(@NotNull LocalSearchScope scope2){
    if (equals(scope2)) return this;
    return intersection(this, scope2);
  }

  @NotNull
  private static LocalSearchScope intersection(@NotNull LocalSearchScope scope1, @NotNull LocalSearchScope scope2) {
    List<PsiElement> result = new ArrayList<>();
    for (final PsiElement element1 : scope1.myScope) {
      for (final PsiElement element2 : scope2.myScope) {
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
  private static PsiElement intersectScopeElements(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    if (PsiTreeUtil.isContextAncestor(element1, element2, false)) return element2;
    if (PsiTreeUtil.isContextAncestor(element2, element1, false)) return element1;
    if (PsiTreeUtil.isAncestor(element1, element2, false)) return element2;
    if (PsiTreeUtil.isAncestor(element2, element1, false)) return element1;
    return null;
  }

  @Override
  public String toString() {
    //noinspection HardCodedStringLiteral
    return Arrays.stream(myScope).map(String::valueOf).collect(Collectors.joining(",", "LocalSearchScope:", ""));
  }

  @Override
  @NotNull
  public SearchScope union(@NotNull SearchScope scope) {
    if (scope instanceof LocalSearchScope) return union((LocalSearchScope)scope);
    return ((GlobalSearchScope)scope).union(this);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    return isInScope(file);
  }

  @NotNull
  public SearchScope union(@NotNull LocalSearchScope scope2) {
    if (equals(scope2)) return this;
    PsiElement[] elements1 = getScope();
    PsiElement[] elements2 = scope2.getScope();
    boolean[] united = new boolean[elements2.length];
    List<PsiElement> result = new ArrayList<>();
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

  private static PsiElement scopeElementsUnion(@NotNull PsiElement element1, @NotNull PsiElement element2) {
    if (PsiTreeUtil.isAncestor(element1, element2, false)) return element1;
    if (PsiTreeUtil.isAncestor(element2, element1, false)) return element2;
    PsiElement commonParent = PsiTreeUtil.findCommonParent(element1, element2);
    if (commonParent == null) return null;
    return commonParent;
  }

  public boolean isInScope(@NotNull VirtualFile file) {
    return ArrayUtil.indexOf(myVirtualFiles, file) != -1;
  }

  public boolean containsRange(@NotNull PsiFile file, @NotNull TextRange range) {
    for (PsiElement element : getScope()) {
      if (file == element.getContainingFile() && element.getTextRange().contains(range)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Contract(pure=true)
  public static LocalSearchScope getScopeRestrictedByFileTypes(@NotNull final LocalSearchScope scope, @NotNull final FileType... fileTypes) {
    if (fileTypes.length == 0) throw new IllegalArgumentException("empty fileTypes");
    if (scope == EMPTY) {
      return EMPTY;
    }
    return ReadAction.compute(() -> {
      PsiElement[] elements = scope.getScope();
      List<PsiElement> result = new ArrayList<>(elements.length);
      for (PsiElement element : elements) {
        PsiFile containingFile = element.getContainingFile();
        FileType fileType = containingFile.getFileType();
        if (ArrayUtil.contains(fileType, fileTypes)) {
          result.add(element);
        }
      }
      return result.isEmpty()
             ? EMPTY
             : new LocalSearchScope(PsiUtilCore.toPsiElementArray(result), scope.getDisplayName(), scope.isIgnoreInjectedPsi());
    });
  }
}
