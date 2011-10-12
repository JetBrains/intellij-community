/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.codeInsight.template.TemplateContextType;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class LiveTemplateCompletionContributor extends CompletionContributor {
  public LiveTemplateCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull final CompletionResultSet result) {
        final PsiFile file = parameters.getPosition().getContainingFile();
        final int offset = parameters.getOffset();
        final List<TemplateImpl> templates = listApplicableTemplates(file, offset);
        if (Registry.is("show.live.templates.in.completion")) {
          final Ref<Boolean> templatesShown = Ref.create(false);
          
          result.runRemainingContributors(parameters, new Consumer<CompletionResult>() {
            @Override
            public void consume(CompletionResult completionResult) {
              result.passResult(completionResult);
              ensureTemplatesShown(templatesShown, templates, result);
            }
          });

          ensureTemplatesShown(templatesShown, templates, result);
          
          return;
        }

        if (parameters.getInvocationCount() > 0) return; //only in autopopups for now

        final String prefix = result.getPrefixMatcher().getPrefix();
        final TemplateImpl template = findApplicableTemplate(file, offset, prefix);
        if (template != null) {
          result.addElement(new LiveTemplateLookupElement(template, true));
        }
        for (final TemplateImpl possible : templates) {
          result.restartCompletionOnPrefixChange(possible.getKey());
        }

      }
    });
  }

  private static void ensureTemplatesShown(Ref<Boolean> templatesShown, List<TemplateImpl> templates, CompletionResultSet result) {
    if (!templatesShown.get()) {
      templatesShown.set(true);
      for (final TemplateImpl possible : templates) {
        result.addElement(new LiveTemplateLookupElement(possible, false));
      }
    }
  }

  private static List<TemplateImpl> listApplicableTemplates(PsiFile file, int offset) {
    Set<TemplateContextType> contextTypes = TemplateManagerImpl.getApplicableContextTypes(file, offset);

    final ArrayList<TemplateImpl> result = CollectionFactory.arrayList();
    for (final TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
      if (!template.isDeactivated() && !template.isSelectionTemplate() && TemplateManagerImpl.isApplicable(template, contextTypes)) {
        result.add(template);
      }
    }
    return result;
  }

  @Nullable
  public static TemplateImpl findApplicableTemplate(PsiFile file, int offset, final String key) {
    return ContainerUtil.find(listApplicableTemplates(file, offset), new Condition<TemplateImpl>() {
      @Override
      public boolean value(TemplateImpl template) {
        return key.equals(template.getKey());
      }
    });
  }

  public static class Skipper extends CompletionPreselectSkipper {

    @Override
    public boolean skipElement(LookupElement element, CompletionLocation location) {
      return element instanceof LiveTemplateLookupElement && ((LiveTemplateLookupElement)element).sudden;
    }
  }

}
