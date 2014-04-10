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
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomLiveTemplateBase;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPlainTextFile;
import com.intellij.ui.EditorTextField;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author peter
 */
public class LiveTemplateCompletionContributor extends CompletionContributor {
  public static boolean ourShowTemplatesInTests = false;

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
        final PsiFile file = parameters.getPosition().getContainingFile();
        if (file instanceof PsiPlainTextFile &&
            parameters.getEditor().getComponent().getParent() instanceof EditorTextField) {
          return;
        }

        final int offset = parameters.getOffset();
        final List<TemplateImpl> templates = listApplicableTemplates(file, offset);
        Editor editor = parameters.getEditor();
        if (showAllTemplates()) {
          final AtomicBoolean templatesShown = new AtomicBoolean(false);
          final CompletionResultSet finalResult = result;
          result.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
            @Override
            public void consume(CompletionResult completionResult) {
              finalResult.passResult(completionResult);
              ensureTemplatesShown(templatesShown, templates, parameters, finalResult);
            }
          });

          ensureTemplatesShown(templatesShown, templates, parameters, result);
          return;
        }

        if (parameters.getInvocationCount() > 0) return; //only in autopopups for now

        String templatePrefix = findLiveTemplatePrefix(file, editor, result.getPrefixMatcher().getPrefix());
        final TemplateImpl template = findApplicableTemplate(file, offset, templatePrefix);
        if (template != null) {
          result = result.withPrefixMatcher(template.getKey());
          result.addElement(new LiveTemplateLookupElementImpl(template, true));
        }
        for (final TemplateImpl possible : templates) {
          result.restartCompletionOnPrefixChange(possible.getKey());
        }
      }
    });
  }

  @SuppressWarnings("MethodMayBeStatic") //for Kotlin
  protected boolean showAllTemplates() {
    return shouldShowAllTemplates();
  }

  private static void ensureTemplatesShown(AtomicBoolean templatesShown,
                                           List<TemplateImpl> templates,
                                           CompletionParameters parameters, 
                                           CompletionResultSet result) {
    if (!templatesShown.getAndSet(true)) {
      for (final TemplateImpl possible : templates) {
        result.addElement(new LiveTemplateLookupElementImpl(possible, false));
      }

      PsiFile file = parameters.getPosition().getContainingFile();
      Editor editor = parameters.getEditor();
      for (CustomLiveTemplate customLiveTemplate : CustomLiveTemplate.EP_NAME.getExtensions()) {
        if (customLiveTemplate instanceof CustomLiveTemplateBase && TemplateManagerImpl.isApplicable(customLiveTemplate, editor, file)) {
          ((CustomLiveTemplateBase)customLiveTemplate).addCompletions(parameters, result);
        }
      }
    }
  }

  private static List<TemplateImpl> listApplicableTemplates(PsiFile file, int offset) {
    Set<TemplateContextType> contextTypes = TemplateManagerImpl.getApplicableContextTypes(file, offset);

    final ArrayList<TemplateImpl> result = ContainerUtil.newArrayList();
    for (final TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
      if (!template.isDeactivated() && TemplateManagerImpl.isApplicable(template, contextTypes)) {
        result.add(template);
      }
    }
    return result;
  }

  @Nullable
  public static TemplateImpl findApplicableTemplate(final PsiFile file, int offset, @NotNull final String possiblePrefix) {
    return ContainerUtil.find(listApplicableTemplates(file, offset), new Condition<TemplateImpl>() {
      @Override
      public boolean value(TemplateImpl template) {
        return possiblePrefix.equals(template.getKey());
      }
    });
  }

  @NotNull
  public static String findLiveTemplatePrefix(@NotNull PsiFile file, @NotNull Editor editor, @NotNull String defaultValue) {
    final CustomTemplateCallback callback = new CustomTemplateCallback(editor, file, false);
    for (CustomLiveTemplate customLiveTemplate : CustomLiveTemplate.EP_NAME.getExtensions()) {
      final String customKey = customLiveTemplate.computeTemplateKey(callback);
      if (customKey != null) {
        return customKey;
      }
    }
    return defaultValue;
  }

  public static class Skipper extends CompletionPreselectSkipper {

    @Override
    public boolean skipElement(LookupElement element, CompletionLocation location) {
      return element instanceof LiveTemplateLookupElement && ((LiveTemplateLookupElement)element).sudden && !Registry.is("ide.completion.autopopup.select.live.templates");
    }
  }

}
