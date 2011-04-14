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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author peter
 */
public class LiveTemplateCompletionContributor extends CompletionContributor {
  public LiveTemplateCompletionContributor() {
    extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
      @Override
      protected void addCompletions(@NotNull CompletionParameters parameters,
                                    ProcessingContext context,
                                    @NotNull CompletionResultSet result) {
        if (parameters.getInvocationCount() > 0) return; //only in autopopups for now

        final PsiFile file = parameters.getOriginalFile();
        final int offset = parameters.getOffset();
        if (Registry.is("show.live.templates.in.completion")) {
          for (final TemplateImpl possible : listApplicableTemplates(file, offset)) {
            result.addElement(new LiveTemplateLookupElement(possible));
          }
          return;
        }

        final String prefix = result.getPrefixMatcher().getPrefix();
        final TemplateImpl template = findApplicableTemplate(file, offset, prefix);
        if (template != null) {
          result.addElement(new LiveTemplateLookupElement(template));
        } else {
          for (final TemplateImpl possible : listApplicableTemplates(file, offset)) {
            result.restartCompletionOnPrefixChange(possible.getKey());
          }
        }

      }
    });
  }

  private static List<TemplateImpl> listApplicableTemplates(PsiFile file, int offset) {
    final ArrayList<TemplateImpl> result = CollectionFactory.arrayList();
    for (final TemplateImpl template : TemplateSettings.getInstance().getTemplates()) {
      if (!template.isDeactivated() && !template.isSelectionTemplate() && TemplateManagerImpl.isApplicable(file, offset, template)) {
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
}
