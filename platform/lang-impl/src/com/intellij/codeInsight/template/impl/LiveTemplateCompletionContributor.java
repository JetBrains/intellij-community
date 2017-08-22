/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomLiveTemplateBase;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.ui.EditorTextField;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.codeInsight.template.impl.ListTemplatesHandler.filterTemplatesByPrefix;

/**
 * @author peter
 */
public class LiveTemplateCompletionContributor extends CompletionContributor {
  private static boolean ourShowTemplatesInTests = false;

  @TestOnly
  public static void setShowTemplatesInTests(boolean show, @NotNull Disposable parentDisposable) {
    ourShowTemplatesInTests = show;
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        //noinspection AssignmentToStaticFieldFromInstanceMethod
        ourShowTemplatesInTests = false;
      }
    });
  }

  public static boolean shouldShowAllTemplates() {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return ourShowTemplatesInTests;
    }
    return Registry.is("show.live.templates.in.completion");
  }

  public LiveTemplateCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull final CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        ProgressManager.checkCanceled();
        final PsiFile file = parameters.getPosition().getContainingFile();
        if (file instanceof PsiPlainTextFile && parameters.getEditor().getComponent().getParent() instanceof EditorTextField) {
          return;
        }

        Editor editor = parameters.getEditor();
        int offset = editor.getCaretModel().getOffset();
        final List<TemplateImpl> availableTemplates = TemplateManagerImpl.listApplicableTemplates(file, offset, false);
        final Map<TemplateImpl, String> templates = filterTemplatesByPrefix(availableTemplates, editor, offset, false, false);
        if (showAllTemplates()) {
          final AtomicBoolean templatesShown = new AtomicBoolean(false);
          final CompletionResultSet finalResult = result;
          result.runRemainingContributors(parameters, completionResult -> {
            finalResult.passResult(completionResult);
            if (completionResult.isStartMatch()) {
              ensureTemplatesShown(templatesShown, templates, finalResult);
            }
          });

          ensureTemplatesShown(templatesShown, templates, result);
          showCustomLiveTemplates(parameters, result);
          return;
        }

        if (parameters.getInvocationCount() > 0) return; //only in autopopups for now

        // custom templates should handle this situation by itself (return true from hasCompletionItems() and provide lookup element)
        // regular templates won't be shown in this case
        if (!customTemplateAvailableAndHasCompletionItem(null, editor, file, offset)) {
          TemplateImpl template = findFullMatchedApplicableTemplate(editor, offset, availableTemplates);
          if (template != null) {
            result.withPrefixMatcher(result.getPrefixMatcher().cloneWithPrefix(template.getKey()))
              .addElement(new LiveTemplateLookupElementImpl(template, true));
          }
        }

        for (Map.Entry<TemplateImpl, String> possible : templates.entrySet()) {
          ProgressManager.checkCanceled();
          String templateKey = possible.getKey().getKey();
          String currentPrefix = possible.getValue();
          result.withPrefixMatcher(result.getPrefixMatcher().cloneWithPrefix(currentPrefix))
            .restartCompletionOnPrefixChange(templateKey);
        }
      }
    });
  }

  public static boolean customTemplateAvailableAndHasCompletionItem(@Nullable Character shortcutChar, @NotNull Editor editor, @NotNull PsiFile file, int offset) {
    CustomTemplateCallback callback = new CustomTemplateCallback(editor, file);
    for (CustomLiveTemplate customLiveTemplate : TemplateManagerImpl.listApplicableCustomTemplates(editor, file, false)) {
      ProgressManager.checkCanceled();
      if (customLiveTemplate instanceof CustomLiveTemplateBase) {
        if ((shortcutChar == null || customLiveTemplate.getShortcut() == shortcutChar.charValue())
            && ((CustomLiveTemplateBase)customLiveTemplate).hasCompletionItem(file, offset)) {
          return customLiveTemplate.computeTemplateKey(callback) != null;
        }
      }
    }
    return false;
  }

  @SuppressWarnings("MethodMayBeStatic") //for Kotlin
  protected boolean showAllTemplates() {
    return shouldShowAllTemplates();
  }

  private static void ensureTemplatesShown(AtomicBoolean templatesShown, Map<TemplateImpl, String> templates,
                                           CompletionResultSet result) {
    if (!templatesShown.getAndSet(true)) {
      for (final Map.Entry<TemplateImpl, String> entry : templates.entrySet()) {
        ProgressManager.checkCanceled();
        result.withPrefixMatcher(result.getPrefixMatcher().cloneWithPrefix(StringUtil.notNullize(entry.getValue())))
          .addElement(new LiveTemplateLookupElementImpl(entry.getKey(), false));
      }
    }
  }

  private static void showCustomLiveTemplates(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiFile file = parameters.getPosition().getContainingFile();
    Editor editor = parameters.getEditor();
    for (CustomLiveTemplate customLiveTemplate : TemplateManagerImpl.listApplicableCustomTemplates(editor, file, false)) {
      ProgressManager.checkCanceled();
      if (customLiveTemplate instanceof CustomLiveTemplateBase) {
        ((CustomLiveTemplateBase)customLiveTemplate).addCompletions(parameters, result);
      }
    }
  }

  @Nullable
  public static TemplateImpl findFullMatchedApplicableTemplate(@NotNull Editor editor,
                                                               int offset,
                                                               @NotNull Collection<TemplateImpl> availableTemplates) {
    Map<TemplateImpl, String> templates = filterTemplatesByPrefix(availableTemplates, editor, offset, true, false);
    if (templates.size() == 1) {
      TemplateImpl template = ContainerUtil.getFirstItem(templates.keySet());
      if (template != null) {
        return template;
      }
    }
    return null;
  }

}
