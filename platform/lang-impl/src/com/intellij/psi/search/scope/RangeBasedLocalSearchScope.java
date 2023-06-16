// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.scope;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.AbstractFileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A base class for scopes that restricts their content based on text ranges (from VCS, selection, etc)
 */
public abstract class RangeBasedLocalSearchScope extends LocalSearchScope {
  private static final Logger LOG = Logger.getInstance(RangeBasedLocalSearchScope.class);

  protected final boolean myIgnoreInjectedPsi;
  protected final @NotNull @Nls String  myDisplayName;

  private LocalSearchScope myLocalSearchScope;

  public RangeBasedLocalSearchScope(final @NotNull @Nls String displayName,
                                    final boolean ignoreInjectedPsi) {
    super(PsiElement.EMPTY_ARRAY);
    myDisplayName = displayName;
    myIgnoreInjectedPsi = ignoreInjectedPsi;
  }

  public abstract @NotNull TextRange @NotNull [] getRanges(@NotNull VirtualFile file);

  protected static void collectPsiElementsAtRange(@NotNull PsiFile psiFile, @NotNull List<? super @NotNull PsiElement> elements, int start, int end) {
    int modifiedEnd = end;
    int length = psiFile.getTextLength();
    if (end > length) {
      LOG.error("Range extends beyond the PSI file range. Maybe PSI file is not actual");
    }

    if (end == length) {
      modifiedEnd--;
    }

    PsiElement startElement = psiFile.findElementAt(start);
    PsiElement endElement = psiFile.findElementAt(modifiedEnd);

    if (startElement == null || endElement == null) {
      return;
    }

    // PsiFileImpl.findElementAt may return element from another language, for example, JS element when called for an HTML file.
    // Such an element would belong to a sister psi file.
    // If startElement and endElement belong to different files, then findCommonParent doesn't work correctly, so we have to use
    // AbstractFileViewProvider.findElementAt which always returns psi elements from an original file.
    if (startElement.getContainingFile() != endElement.getContainingFile()) {
      startElement = AbstractFileViewProvider.findElementAt(psiFile, start);
      endElement = AbstractFileViewProvider.findElementAt(psiFile, modifiedEnd);
      if (startElement == null || endElement == null) {
        return;
      }
    }

    final PsiElement parent = PsiTreeUtil.findCommonParent(startElement, endElement);
    if (parent == null) {
      return;
    }

    final PsiElement[] children = parent.getChildren();
    TextRange range = new TextRange(start, end);
    if (children.length == 0) {
      if (parent.getContainingFile() != null && range.intersects(parent.getTextRange())) {
        elements.add(parent);
      }
    }
    else {
      for (PsiElement child : children) {
        TextRange childRange = child.getTextRange();
        if (!(child instanceof PsiWhiteSpace) &&
            child.getContainingFile() != null &&
            childRange != null &&
            range.intersects(childRange)) {
          elements.add(child);
        }
      }
    }
  }

  @Override
  public boolean isIgnoreInjectedPsi() {
    return myIgnoreInjectedPsi;
  }

  @Override
  public @NotNull String getDisplayName() {
    return myDisplayName;
  }

  protected abstract @NotNull PsiElement @NotNull [] getPsiElements();

  private void createIfNeeded() {
    if (myLocalSearchScope == null) {
      myLocalSearchScope = ReadAction.compute(() -> new LocalSearchScope(getPsiElements(), myDisplayName, myIgnoreInjectedPsi));
    }
  }

  @Override
  public PsiElement @NotNull [] getScope() {
    createIfNeeded();
    return myLocalSearchScope.getScope();
  }

  @Override
  public @NotNull LocalSearchScope intersectWith(@NotNull LocalSearchScope scope2) {
    createIfNeeded();
    return myLocalSearchScope.intersectWith(scope2);
  }

  @Override
  public @NotNull SearchScope intersectWith(@NotNull SearchScope scope2) {
    createIfNeeded();
    return myLocalSearchScope.intersectWith(scope2);
  }

  @Override
  public @NotNull SearchScope union(@NotNull SearchScope scope) {
    createIfNeeded();
    return myLocalSearchScope.union(scope);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    createIfNeeded();
    return myLocalSearchScope.contains(file);
  }

  @Override
  public boolean isInScope(@NotNull VirtualFile file) {
    createIfNeeded();
    return myLocalSearchScope.isInScope(file);
  }
}
