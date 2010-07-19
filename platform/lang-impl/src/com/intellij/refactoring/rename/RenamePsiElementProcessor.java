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

package com.intellij.refactoring.rename;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public abstract class RenamePsiElementProcessor {
  private static final ExtensionPointName<RenamePsiElementProcessor> EP_NAME = ExtensionPointName.create("com.intellij.renamePsiElementProcessor");

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
  public Pair<String, String> getTextOccurrenceSearchStrings(@NotNull PsiElement element, @NotNull String newName) {
    return null;
  }

  @Nullable
  public String getQualifiedNameAfterRename(final PsiElement element, final String newName, final boolean nonJava) {
    return null;
  }

  /**
   * Builds the complete set of elements to be renamed during the refactoring.
   *
   * @param element the base element for the refactoring.
   * @param newName the name into which the element is being renamed.
   * @param allRenames the map (from element to its new name) into which all additional elements to be renamed should be stored.
   */
  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
  }

  public void findExistingNameConflicts(final PsiElement element, final String newName, final MultiMap<PsiElement,String> conflicts) {
  }

  public static List<RenamePsiElementProcessor> allForElement(PsiElement element) {
    final List<RenamePsiElementProcessor> result = new ArrayList<RenamePsiElementProcessor>();
    for (RenamePsiElementProcessor processor : EP_NAME.getExtensions()) {
      if (processor.canProcessElement(element)) {
        result.add(processor);
      }
    }
    return result;
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
    return "refactoring.renameDialogs";
  }

  public boolean isToSearchInComments(final PsiElement element) {
    return element instanceof PsiFileSystemItem && RefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FILE;
  }

  public void setToSearchInComments(final PsiElement element, boolean enabled) {
    if (element instanceof PsiFileSystemItem) {
      RefactoringSettings.getInstance().RENAME_SEARCH_IN_COMMENTS_FOR_FILE = enabled;
    }
  }

  public boolean isToSearchForTextOccurrences(final PsiElement element) {
    return element instanceof PsiFileSystemItem && RefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FILE;
  }

  public void setToSearchForTextOccurrences(final PsiElement element, boolean enabled) {
    if (element instanceof PsiFileSystemItem) {
      RefactoringSettings.getInstance().RENAME_SEARCH_FOR_TEXT_FOR_FILE = enabled;
    }
  }

  /**
   * Returns the element to be renamed instead of the element on which the rename refactoring was invoked (for example, a super method
   * of an inherited method).
   *
   * @param element the element on which the refactoring was invoked.
   * @param editor the editor in which the refactoring was invoked.
   * @return the element to rename, or null if the rename refactoring should be canceled.
   */
  @Nullable
  public PsiElement substituteElementToRename(final PsiElement element, @Nullable Editor editor) {
    return element;
  }

  public void findCollisions(final PsiElement element, final String newName, final Map<? extends PsiElement, String> allRenames,
                             final List<UsageInfo> result) {
  }
  
  public static final RenamePsiElementProcessor DEFAULT = new RenamePsiElementProcessor() {
    public boolean canProcessElement(final PsiElement element) {
      return true;
    }
  };

  /**
   * Use this method to force showing preview for custom processors.
   * This method is always called after prepareRenaming()
   * @return force show preview
   */
  public boolean forcesShowPreview() {
    return false;
  }

  @Nullable
  public PsiElement getElementToSearchInStringsAndComments(PsiElement element) {
    return element;
  }
}
