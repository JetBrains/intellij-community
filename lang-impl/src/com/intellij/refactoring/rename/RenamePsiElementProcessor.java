package com.intellij.refactoring.rename;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.List;

/**
 * @author yole
 */
public abstract class RenamePsiElementProcessor {
  public static final ExtensionPointName<RenamePsiElementProcessor> EP_NAME = ExtensionPointName.create("com.intellij.renamePsiElementProcessor");

  public abstract boolean canProcessElement(PsiElement element);

  public void renameElement(final PsiElement element, String newName, UsageInfo[] usages,
                     RefactoringElementListener listener) throws IncorrectOperationException {
    RenameUtil.doRenameGenericNamedElement(element, newName, usages, listener);
  }

  @NotNull
  public Collection<PsiReference> findReferences(final PsiElement element) {
    return ReferencesSearch.search(element).findAll();
  }

  @Nullable
  public Pair<String, String> getTextOccurrenceSearchStrings(final PsiElement element, final String newName) {
    return null;
  }

  @Nullable
  public String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava) {
    return null;
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
  }

  public void findExistingNameConflicts(final PsiElement element, final String newName, final Collection<String> conflicts) {
  }

  @NotNull
  public static RenamePsiElementProcessor forElement(PsiElement element) {
    for(RenamePsiElementProcessor processor: Extensions.getExtensions(EP_NAME)) {
      if (processor.canProcessElement(element)) {
        return processor;
      }
    }
    return DEFAULT;
  }

  @Nullable
  public Runnable getPostRenameCallback(final PsiElement element, final String newName, final RefactoringElementListener elementListener) {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpID(final PsiElement element) {
    if (element instanceof PsiFile) {
      return "refactoring.renameFile";
    }
    return null;
  }

  public boolean isToSearchInComments(final PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return RefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FILE;
    }
    return false;
  }

  public void setToSearchInComments(final PsiElement element, boolean enabled) {
    if (element instanceof PsiFileSystemItem) {
      RefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FILE = enabled;
    }
  }

  public boolean isToSearchForTextOccurrences(final PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return RefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FILE;
    }
    return false;
  }

  public void setToSearchForTextOccurrences(final PsiElement element, boolean enabled) {
    if (element instanceof PsiFileSystemItem) {
      RefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FILE = enabled;
    }
  }

  @Nullable
  public PsiElement substituteElementToRename(final PsiElement element) {
    return element;
  }

  public void findCollisions(final PsiElement element, final String newName, final Map<? extends PsiElement, String> allRenames,
                             final List<UsageInfo> result) {
  }
  
  public static RenamePsiElementProcessor DEFAULT = new RenamePsiElementProcessor() {
    public boolean canProcessElement(final PsiElement element) {
      return true;
    }
  };
}
