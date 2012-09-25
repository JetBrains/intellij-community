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
package com.intellij.find.findUsages;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

/**
 * @author peter
 * @see com.intellij.find.findUsages.FindUsagesHandlerFactory
 */
public abstract class FindUsagesHandler {
  // return this handler if you want to cancel the search
  public static final FindUsagesHandler NULL_HANDLER = new FindUsagesHandler(PsiUtilCore.NULL_PSI_ELEMENT){};

  private final PsiElement myPsiElement;

  protected FindUsagesHandler(@NotNull PsiElement psiElement) {
    myPsiElement = psiElement;
  }

  @NotNull
  public AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab) {
    return new CommonFindUsagesDialog(myPsiElement, getProject(), getFindUsagesOptions(DataManager.getInstance().getDataContext()),
                                      toShowInNewTab, mustOpenInNewTab, isSingleFile, this);
  }

  @NotNull
  public final PsiElement getPsiElement() {
    return myPsiElement;
  }

  @NotNull
  public final Project getProject() {
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

  public static FindUsagesOptions createFindUsagesOptions(final Project project, @Nullable final DataContext dataContext) {
    FindUsagesOptions findUsagesOptions = new FindUsagesOptions(project, dataContext);
    findUsagesOptions.isUsages = true;
    findUsagesOptions.isSearchForTextOccurrences = true;
    return findUsagesOptions;
  }

  @NotNull
  public FindUsagesOptions getFindUsagesOptions() {
    return getFindUsagesOptions(null);
  }
  @NotNull
  public FindUsagesOptions getFindUsagesOptions(@Nullable final DataContext dataContext) {
    FindUsagesOptions options = createFindUsagesOptions(getProject(), dataContext);
    options.isSearchForTextOccurrences &= isSearchForTextOccurencesAvailable(getPsiElement(), false);
    return options;
  }

  public boolean processElementUsages(@NotNull final PsiElement element,
                                      @NotNull final Processor<UsageInfo> processor,
                                      @NotNull final FindUsagesOptions options) {
    final ReadActionProcessor<PsiReference> refProcessor = new ReadActionProcessor<PsiReference>() {
      @Override
      public boolean processInReadAction(final PsiReference ref) {
        TextRange rangeInElement = ref.getRangeInElement();
        return processor.process(new UsageInfo(ref.getElement(), rangeInElement.getStartOffset(), rangeInElement.getEndOffset(), false));
      }
    };

    final SearchScope scope = options.searchScope;

    final boolean searchText = options.isSearchForTextOccurrences && scope instanceof GlobalSearchScope;

    if (options.isUsages) {
      boolean success =
        ReferencesSearch.search(new ReferencesSearch.SearchParameters(element, scope, false, options.fastTrack)).forEach(refProcessor);
      if (!success) return false;
    }

    if (searchText) {
      if (options.fastTrack != null) {
        options.fastTrack.searchCustom(new Processor<Processor<PsiReference>>() {
          @Override
          public boolean process(Processor<PsiReference> consumer) {
            return processUsagesInText(element, processor, (GlobalSearchScope)scope);
          }
        });
      }
      else {
        return processUsagesInText(element, processor, (GlobalSearchScope)scope);
      }
    }
    return true;
  }

  public boolean processUsagesInText(@NotNull final PsiElement element,
                                     @NotNull Processor<UsageInfo> processor,
                                     @NotNull GlobalSearchScope searchScope) {
    Collection<String> stringToSearch = ApplicationManager.getApplication().runReadAction(new NullableComputable<Collection<String>>() {
      @Override
      public Collection<String> compute() {
        return getStringsToSearch(element);
      }
    });
    if (stringToSearch == null) return true;
    final TextRange elementTextRange = ApplicationManager.getApplication().runReadAction(new NullableComputable<TextRange>() {
      @Override
      public TextRange compute() {
        if (!element.isValid()) return null;
        return element.getTextRange();
      }
    });
    TextOccurrencesUtil.UsageInfoFactory factory = new TextOccurrencesUtil.UsageInfoFactory() {
      @Override
      public UsageInfo createUsageInfo(@NotNull PsiElement usage, int startOffset, int endOffset) {
        if (elementTextRange != null
            && usage.getContainingFile() == element.getContainingFile()
            && elementTextRange.contains(startOffset)
            && elementTextRange.contains(endOffset)) {
          return null;
        }

        PsiReference someReference = usage.findReferenceAt(startOffset);
        if (someReference != null) {
          PsiElement refElement = someReference.getElement();
          for (PsiReference ref : PsiReferenceService.getService().getReferences(refElement, new PsiReferenceService.Hints(element, null))) {
            if (element.getManager().areElementsEquivalent(ref.resolve(), element)) {
              TextRange range = ref.getRangeInElement().shiftRight(refElement.getTextRange().getStartOffset() - usage.getTextRange().getStartOffset());
              return new UsageInfo(usage, range.getStartOffset(), range.getEndOffset(), true);
            }
          }

        }

        return new UsageInfo(usage, startOffset, endOffset, true);
      }
    };
    for (String s : stringToSearch) {
      if (!TextOccurrencesUtil.processTextOccurences(element, s, searchScope, processor, factory)) return false;
    }
    return true;
  }

  @Nullable
  protected Collection<String> getStringsToSearch(final PsiElement element) {
    if (element instanceof PsiNamedElement) {
      return ApplicationManager.getApplication().runReadAction(new Computable<Collection<String>>() {
        @Override
        public Collection<String> compute() {
          return ContainerUtil.createMaybeSingletonList(((PsiNamedElement)element).getName());
        }
      });
    }

    return Collections.singleton(element.getText());
  }

  protected boolean isSearchForTextOccurencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
    return false;
  }

  public Collection<PsiReference> findReferencesToHighlight(@NotNull PsiElement target, @NotNull SearchScope searchScope) {
    return ReferencesSearch.search(target, searchScope, false).findAll();
  }
}
