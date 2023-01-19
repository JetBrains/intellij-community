package com.intellij.psi.search.scope;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
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
  @NotNull
  protected final @Nls String  myDisplayName;

  private LocalSearchScope myLocalSearchScope;

  public RangeBasedLocalSearchScope(@NotNull final @Nls String displayName,
                                    final boolean ignoreInjectedPsi) {
    super(PsiElement.EMPTY_ARRAY);
    myDisplayName = displayName;
    myIgnoreInjectedPsi = ignoreInjectedPsi;
  }

  public abstract @NotNull TextRange @NotNull [] getRanges(@NotNull VirtualFile file);

  protected static void collectPsiElementsAtRange(@NotNull PsiFile psiFile, @NotNull List<? super @NotNull PsiElement> elements, int start, int end) {
    final PsiElement startElement = psiFile.findElementAt(start);
    if (startElement == null) {
      return;
    }
    int modifiedEnd = end;
    int length = psiFile.getTextLength();
    if (end > length) {
      LOG.error("Range extends beyond the PSI file range. Maybe PSI file is not actual");
    }

    if (end == length) {
      modifiedEnd--;
    }

    final PsiElement endElement = psiFile.findElementAt(modifiedEnd);
    if (endElement == null) {
      return;
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

  @NotNull
  @Override
  public String getDisplayName() {
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

  @NotNull
  @Override
  public LocalSearchScope intersectWith(@NotNull LocalSearchScope scope2) {
    createIfNeeded();
    return myLocalSearchScope.intersectWith(scope2);
  }

  @NotNull
  @Override
  public SearchScope intersectWith(@NotNull SearchScope scope2) {
    createIfNeeded();
    return myLocalSearchScope.intersectWith(scope2);
  }

  @NotNull
  @Override
  public SearchScope union(@NotNull SearchScope scope) {
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
