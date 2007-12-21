/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.find.findUsages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class FindUsagesHandler {
  private final PsiElement myPsiElement;

  protected FindUsagesHandler(@NotNull PsiElement psiElement) {
    myPsiElement = psiElement;
  }

  @NotNull
  public AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab) {
    return new CommonFindUsagesDialog(myPsiElement, getProject(), getFindUsagesOptions(), toShowInNewTab, mustOpenInNewTab, isSingleFile);
  }

  public final PsiElement getPsiElement() {
    return myPsiElement;
  }

  protected final Project getProject() {
    return myPsiElement.getProject();
  }

  @NotNull
  public PsiElement[] getPrimaryElements() {
    return new PsiElement[]{myPsiElement};
  }

  @NotNull
  public PsiElement[] getSecondaryElements() {
    return PsiElement.EMPTY_ARRAY;
  }

  public static FindUsagesOptions createFindUsagesOptions(final Project project) {
    FindUsagesOptions findUsagesOptions = new FindUsagesOptions(project);
    findUsagesOptions.isUsages = true;
    findUsagesOptions.isIncludeOverloadUsages = false;
    findUsagesOptions.isIncludeSubpackages = true;
    findUsagesOptions.isReadAccess = true;
    findUsagesOptions.isWriteAccess = true;
    findUsagesOptions.isCheckDeepInheritance = true;
    findUsagesOptions.isSearchForTextOccurences = true;
    return findUsagesOptions;
  }

  @NotNull
  public FindUsagesOptions getFindUsagesOptions() {
    FindUsagesOptions options = createFindUsagesOptions(getProject());
    options.isSearchForTextOccurences &= isSearchForTextOccurencesAvailable(getPsiElement(), false);
    return options;
  }

  public void processElementUsages(final PsiElement element, final Processor<UsageInfo> processor, final FindUsagesOptions options) {
    if (options.isUsages) {
      ReferencesSearch.search(element, options.searchScope, false).forEach(new ReadActionProcessor<PsiReference>() {
        public boolean processInReadAction(final PsiReference ref) {
          TextRange rangeInElement = ref.getRangeInElement();
          return processor.process(new UsageInfo(ref.getElement(), rangeInElement.getStartOffset(), rangeInElement.getEndOffset(), false));
        }
      });
    }

    if (options.isSearchForTextOccurences && options.searchScope instanceof GlobalSearchScope) {
      String stringToSearch = getStringToSearch(element);
      if (stringToSearch != null) {
        final TextRange elementTextRange = ApplicationManager.getApplication().runReadAction(new Computable<TextRange>() {
          public TextRange compute() {
            return element.getTextRange();
          }
        });
        RefactoringUtil.UsageInfoFactory factory = new RefactoringUtil.UsageInfoFactory() {
          public UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset) {
            if (elementTextRange != null
                && usage.getContainingFile() == element.getContainingFile()
                && elementTextRange.contains(startOffset)
                && elementTextRange.contains(endOffset)) {
              return null;
            }
            return new UsageInfo(usage, startOffset, endOffset, true);
          }
        };
        RefactoringUtil.processTextOccurences(element, stringToSearch, (GlobalSearchScope)options.searchScope, processor, factory);
      }
    }
  }

  protected String getStringToSearch(final PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return ((PsiNamedElement)element).getName();
    }

    return element.getText();
  }

  protected boolean isSearchForTextOccurencesAvailable(PsiElement psiElement, boolean isSingleFile) {
    return false;
  }
}
