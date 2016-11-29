/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
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
  protected static final ExtensionPointName<RenamePsiElementProcessor> EP_NAME = ExtensionPointName.create("com.intellij.renamePsiElementProcessor");

  public abstract boolean canProcessElement(@NotNull PsiElement element);

  public RenameDialog createRenameDialog(Project project, PsiElement element, PsiElement nameSuggestionContext, Editor editor) {
    return new RenameDialog(project, element, nameSuggestionContext, editor);
  }

  public void renameElement(final PsiElement element, String newName, UsageInfo[] usages,
                            @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    RenameUtil.doRenameGenericNamedElement(element, newName, usages, listener);
  }

  @NotNull
  public Collection<PsiReference> findReferences(final PsiElement element, boolean searchInCommentsAndStrings) {
    return findReferences(element);
  }

  @NotNull
  public Collection<PsiReference> findReferences(final PsiElement element) {
    return ReferencesSearch.search(element, GlobalSearchScope.projectScope(element.getProject())).findAll();
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
    prepareRenaming(element, newName, allRenames, element.getUseScope());
  }

  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames, SearchScope scope) {
  }

  public void findExistingNameConflicts(final PsiElement element, final String newName, final MultiMap<PsiElement,String> conflicts) {
  }
  
  public void findExistingNameConflicts(final PsiElement element, final String newName, final MultiMap<PsiElement,String> conflicts, Map<PsiElement, String> allRenames) {
    findExistingNameConflicts(element, newName, conflicts);
  }

  public boolean isInplaceRenameSupported() {
    return true;
  }

  public static List<RenamePsiElementProcessor> allForElement(@NotNull PsiElement element) {
    final List<RenamePsiElementProcessor> result = new ArrayList<>();
    for (RenamePsiElementProcessor processor : EP_NAME.getExtensions()) {
      if (processor.canProcessElement(element)) {
        result.add(processor);
      }
    }
    return result;
  }

  @NotNull
  public static RenamePsiElementProcessor forElement(@NotNull PsiElement element) {
    RenamePsiElementProcessor[] extensions = Extensions.getExtensions(EP_NAME);
    for(RenamePsiElementProcessor processor: extensions) {
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

  public boolean showRenamePreviewButton(final PsiElement psiElement){
    return true;
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

  /**
   * Substitutes element to be renamed and initiate rename procedure. Should be used in order to prevent modal dialogs to appear during inplace rename
   * @param element the element on which refactoring was invoked
   * @param editor the editor in which inplace refactoring was invoked
   * @param renameCallback rename procedure which should be called on the chosen substitution
   */
  public void substituteElementToRename(@NotNull final PsiElement element, @NotNull Editor editor, @NotNull Pass<PsiElement> renameCallback) {
    final PsiElement psiElement = substituteElementToRename(element, editor);
    if (psiElement == null) return;
    if (!PsiElementRenameHandler.canRename(psiElement.getProject(), editor, psiElement)) return;
    renameCallback.pass(psiElement);
  }

  public void findCollisions(final PsiElement element, final String newName, final Map<? extends PsiElement, String> allRenames,
                             final List<UsageInfo> result) {
  }

  public static final RenamePsiElementProcessor DEFAULT = new RenamePsiElementProcessor() {
    @Override
    public boolean canProcessElement(@NotNull final PsiElement element) {
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
