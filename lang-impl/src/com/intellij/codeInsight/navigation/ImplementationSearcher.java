package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.searches.DefinitionsSearch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplementationSearcher {
  public PsiElement[] searchImplementations(final Editor editor, final PsiElement element, final int offset) {
    final TargetElementUtilBase targetElementUtil = TargetElementUtilBase.getInstance();
    boolean onRef = targetElementUtil.findTargetElement(editor, getFlags() & ~TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED, offset) == null;
    return searchImplementations(element, offset, onRef && targetElementUtil.includeSelfInGotoImplementation(element), onRef);
  }

  @NotNull
  public PsiElement[] searchImplementations(final PsiElement element,
                                            int offset,
                                            final boolean includeSelfAlways,
                                            final boolean includeSelfIfNoOthers) {
    if (element == null) return PsiElement.EMPTY_ARRAY;
    final PsiElement[] elements = searchDefinitions(element);
    if (elements == null) return PsiElement.EMPTY_ARRAY; //the search has been cancelled
    if (elements.length > 0) {
      if (!includeSelfAlways) return filterElements(element, elements, offset);
      final PsiElement[] all;
      if (element.getTextRange() != null) {
        all = new PsiElement[elements.length + 1];
        all[0] = element;
        System.arraycopy(elements, 0, all, 1, elements.length);
      }
      else {
        all = elements;
      }
      return filterElements(element, all, offset);
    }
    return (includeSelfAlways || includeSelfIfNoOthers) && element.getTextRange() != null ?
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

  protected PsiElement[] filterElements(PsiElement element, PsiElement[] targetElements, final int offset) {
    return targetElements;
  }

  public static int getFlags() {
    return TargetElementUtilBase.getInstance().getDefinitionSearchFlags();
  }
}
