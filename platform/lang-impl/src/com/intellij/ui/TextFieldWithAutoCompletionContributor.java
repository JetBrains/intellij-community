/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author Roman.Chernyatchik
 */
public class TextFieldWithAutoCompletionContributor<T> extends CompletionContributor implements DumbAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ui.TextFieldWithAutoCompletionContributor");
  private static final Key<TextFieldWithAutoCompletionListProvider> KEY = Key.create("text field simple completion available");
  private static final Key<Boolean> AUTO_POPUP_KEY = Key.create("text Field simple completion auto-popup");

  public static <T> void installCompletion(Document document,
                                           Project project,
                                           @Nullable TextFieldWithAutoCompletionListProvider<T> provider,
                                           boolean autoPopup) {
    PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
    if (psiFile != null) {
      //noinspection unchecked
      psiFile.putUserData(KEY, provider == null ? TextFieldWithAutoCompletion.EMPTY_COMPLETION : provider);
      psiFile.putUserData(AUTO_POPUP_KEY, autoPopup);
    }
  }


  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiFile file = parameters.getOriginalFile();
    final TextFieldWithAutoCompletionListProvider<T> provider = file.getUserData(KEY);

    if (provider == null || (DumbService.isDumb(file.getProject()) && !DumbService.isDumbAware(provider))) {
      return;
    }
    String adv = provider.getAdvertisement();
    if (adv == null) {
      final String shortcut = getActionShortcut(IdeActions.ACTION_QUICK_JAVADOC);
      if (shortcut != null) {
        adv = provider.getQuickDocHotKeyAdvertisement(shortcut);
      }
    }
    if (adv != null) {
      result.addLookupAdvertisement(adv);
    }

    final String prefix = provider.getPrefix(parameters);
    if (prefix == null) {
      return;
    }
    if (parameters.getInvocationCount() == 0 && !file.getUserData(AUTO_POPUP_KEY)) {   // is autopopup
      return;
    }
    final PrefixMatcher prefixMatcher = provider.createPrefixMatcher(prefix);
    if (prefixMatcher != null) {
      result = result.withPrefixMatcher(prefixMatcher);
    }

    Collection<T> items = provider.getItems(prefix, true, parameters);
    addCompletionElements(result, provider, items, -10000);

    final ProgressManager progressManager = ProgressManager.getInstance();
    ProgressIndicator mainIndicator = progressManager.getProgressIndicator();
    final ProgressIndicator indicator = mainIndicator != null ? new SensitiveProgressWrapper(mainIndicator) : new EmptyProgressIndicator();
    Future<Collection<T>>
      future =
      ApplicationManager.getApplication().executeOnPooledThread(new Callable<Collection<T>>() {
        @Override
        public Collection<T> call() {
          return progressManager.runProcess(new Computable<Collection<T>>() {
            @Override
            public Collection<T> compute() {
              return provider.getItems(prefix, false, parameters);
            }
          }, indicator);
        }
      });

    while (true) {
      try {
        Collection<T> tasks = future.get(100, TimeUnit.MILLISECONDS);
        if (tasks != null) {
          addCompletionElements(result, provider, tasks, 0);
          return;
        }
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Exception ignore) {

      }
      ProgressManager.checkCanceled();
    }
  }

  private static <T> void addCompletionElements(final CompletionResultSet result,
                                                final TextFieldWithAutoCompletionListProvider<T> listProvider,
                                                final Collection<T> items,
                                                final int index) {
    final AutoCompletionPolicy completionPolicy = ApplicationManager.getApplication().isUnitTestMode()
                                                  ? AutoCompletionPolicy.ALWAYS_AUTOCOMPLETE
                                                  : AutoCompletionPolicy.NEVER_AUTOCOMPLETE;
    int grouping = index;
    for (final T item : items) {
      if (item == null) {
        LOG.error("Null item from " + listProvider);
        continue;
      }

      LookupElementBuilder builder = listProvider.createLookupBuilder(item);

      result.addElement(PrioritizedLookupElement.withGrouping(builder.withAutoCompletionPolicy(completionPolicy), grouping--));
    }
  }
}
