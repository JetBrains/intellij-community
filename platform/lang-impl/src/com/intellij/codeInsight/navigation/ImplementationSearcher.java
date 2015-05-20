/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.PsiElementProcessorAdapter;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DefinitionsScopedSearch;
import com.intellij.util.CommonProcessors;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImplementationSearcher {

  public static final String SEARCHING_FOR_IMPLEMENTATIONS = CodeInsightBundle.message("searching.for.implementations");

  @NotNull
  public PsiElement[] searchImplementations(final Editor editor, final PsiElement element, final int offset) {
    final TargetElementUtil targetElementUtil = TargetElementUtil.getInstance();
    boolean onRef = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return targetElementUtil.findTargetElement(editor, getFlags() & ~(TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtil.LOOKUP_ITEM_ACCEPTED), offset) == null;
      }
    });
    return searchImplementations(element, editor, offset, onRef && ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          return element == null || targetElementUtil.includeSelfInGotoImplementation(element);
        }
    }), onRef);
  }

  @NotNull
  public PsiElement[] searchImplementations(final PsiElement element,
                                            final Editor editor, final int offset,
                                            final boolean includeSelfAlways,
                                            final boolean includeSelfIfNoOthers) {
    if (element == null) return PsiElement.EMPTY_ARRAY;
    final PsiElement[] elements = searchDefinitions(element, editor);
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
    return (includeSelfAlways || includeSelfIfNoOthers) &&
           ApplicationManager.getApplication().runReadAction(new Computable<TextRange>() {
             @Override
             public TextRange compute() {
               return element.getTextRange();
             }
           }) != null ?
           new PsiElement[] {element} :
           PsiElement.EMPTY_ARRAY;
  }

  protected static SearchScope getSearchScope(final PsiElement element, final Editor editor) {
    return ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      @Override
      public SearchScope compute() {
        return TargetElementUtil.getInstance().getSearchScope(editor, element);
      }
    });
  }

  @Nullable("For the case the search has been cancelled")
  protected PsiElement[] searchDefinitions(final PsiElement element, final Editor editor) {
    final PsiElement[][] result = new PsiElement[1][];
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        try {
          result[0] = DefinitionsScopedSearch.search(element, getSearchScope(element, editor)).toArray(PsiElement.EMPTY_ARRAY);
        }
        catch (IndexNotReadyException e) {
          dumbModeNotification(element);
          result[0] = null;
        }
      }
    }, SEARCHING_FOR_IMPLEMENTATIONS, true, element.getProject())) {
      return null;
    }
    return result[0];
  }

  public static void dumbModeNotification(final PsiElement element) {
    Project project = ApplicationManager.getApplication().runReadAction(new Computable<Project>() {
      @Override
      public Project compute() {
        return element.getProject();
      }
    });
    DumbService.getInstance(project).showDumbModeNotification("Implementation information isn't available while indices are built");
  }

  protected PsiElement[] filterElements(PsiElement element, PsiElement[] targetElements, final int offset) {
    return targetElements;
  }

  public static int getFlags() {
    return TargetElementUtil.getInstance().getDefinitionSearchFlags();
  }

  public static class FirstImplementationsSearcher extends ImplementationSearcher {
    @Override
    protected PsiElement[] searchDefinitions(final PsiElement element, final Editor editor) {
      if (canShowPopupWithOneItem(element)) {
        return new PsiElement[]{element};
      }

      final PsiElementProcessor.CollectElementsWithLimit<PsiElement> collectProcessor = new PsiElementProcessor.CollectElementsWithLimit<PsiElement>(2, new THashSet<PsiElement>());
      final PsiElement[][] result = new PsiElement[1][];
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
        @Override
        public void run() {
          try {
            DefinitionsScopedSearch.search(element, getSearchScope(element, editor)).forEach(new PsiElementProcessorAdapter<PsiElement>(collectProcessor){
              @Override
              public boolean processInReadAction(PsiElement element) {
                return !accept(element) || super.processInReadAction(element);
              }
            });
            result[0] = collectProcessor.toArray();
          }
          catch (IndexNotReadyException e) {
            ImplementationSearcher.dumbModeNotification(element);
            result[0] = null;
          }
        }
      }, SEARCHING_FOR_IMPLEMENTATIONS, true, element.getProject())) {
        return null;
      }
      return result[0];
    }

    protected boolean canShowPopupWithOneItem(PsiElement element) {
      return accept(element);
    }

    protected boolean accept(PsiElement element) {
      return true;
    }
  }

  public abstract static class BackgroundableImplementationSearcher extends ImplementationSearcher {
    @Override
    protected PsiElement[] searchDefinitions(final PsiElement element, Editor editor) {
      final CommonProcessors.CollectProcessor<PsiElement> processor = new CommonProcessors.CollectProcessor<PsiElement>() {
        @Override
        public boolean process(PsiElement element) {
          processElement(element);
          return super.process(element);
        }
      };
      try {
        DefinitionsScopedSearch.search(element, getSearchScope(element, editor)).forEach(processor);
      }
      catch (IndexNotReadyException e) {
        ImplementationSearcher.dumbModeNotification(element);
        return null;
      }
      return processor.toArray(PsiElement.EMPTY_ARRAY);
    }

    protected abstract void processElement(PsiElement element);
  }
}
