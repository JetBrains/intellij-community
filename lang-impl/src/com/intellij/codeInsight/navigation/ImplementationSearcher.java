package com.intellij.codeInsight.navigation;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.search.searches.DefinitionsSearch;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;

public class ImplementationSearcher {
  public static final int FLAGS = TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED
                                  | TargetElementUtil.ELEMENT_NAME_ACCEPTED
                                  | TargetElementUtil.LOOKUP_ITEM_ACCEPTED
                                  | TargetElementUtil.THIS_ACCEPTED
                                  | TargetElementUtil.SUPER_ACCEPTED;

  public PsiElement[] searchImplementations(final Editor editor, final PsiElement element, final int offset) {
    boolean onRef = TargetElementUtil.findTargetElement(editor, FLAGS & ~TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED, offset) == null;
    final boolean isAbstract =
      element instanceof PsiModifierListOwner && ((PsiModifierListOwner)element).hasModifierProperty(PsiModifier.ABSTRACT);
    return searchImplementations(editor, element, offset, onRef && !isAbstract, onRef);
  }

  @NotNull
  public PsiElement[] searchImplementations(@Nullable Editor editor,
                                            final PsiElement element,
                                            int offset,
                                            final boolean includeSelfAlways,
                                            final boolean includeSelfIfNoOthers) {
    if (element == null) return PsiElement.EMPTY_ARRAY;
    final PsiElement[] elements = searchDefinitions(element);
    if (elements == null) return PsiElement.EMPTY_ARRAY; //the search has been cancelled
    if (elements.length > 0) {
      if (!includeSelfAlways) return filterElements(editor, element, elements, offset);
      PsiElement[] all = new PsiElement[elements.length + 1];
      all[0] = element;
      System.arraycopy(elements, 0, all, 1, elements.length);
      return filterElements(editor, element, all, offset);
    }
    return includeSelfAlways || includeSelfIfNoOthers ?
           new PsiElement[] {element} :
           PsiElement.EMPTY_ARRAY;
  }

  @Nullable("For the case the search has been cancelled")
  protected PsiElement[] searchDefinitions(final PsiElement element) {
    final PsiElement[][] result = new PsiElement[1][];
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        result[0] = DefinitionsSearch.search(element).toArray(PsiElement.EMPTY_ARRAY);
      }
    }, CodeInsightBundle.message("searching.for.implementations"), true, element.getProject())) {
      return null;
    }
    return result[0];
  }

  protected PsiElement[] filterElements(@Nullable Editor editor, PsiElement element, PsiElement[] targetElements, final int offset) {
    return targetElements;
  }
}
