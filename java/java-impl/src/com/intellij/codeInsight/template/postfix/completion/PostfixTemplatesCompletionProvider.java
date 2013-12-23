/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.completion.PostfixTemplateCompletionContributor.getPostfixLiveTemplate;

class PostfixTemplatesCompletionProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
    if (!isCompletionEnabled(parameters)) {
      return;
    }

    PostfixLiveTemplate postfixLiveTemplate = getPostfixLiveTemplate(parameters.getOriginalFile(), parameters.getEditor());
    if (postfixLiveTemplate != null) {
      PsiFile file = parameters.getPosition().getContainingFile();
      final CustomTemplateCallback callback = new CustomTemplateCallback(parameters.getEditor(), file, false);
      String computedKey = postfixLiveTemplate.computeTemplateKey(callback);
      if (computedKey != null) {
        PostfixTemplate template = postfixLiveTemplate.getTemplateByKey(computedKey);
        if (template != null) {
          result = result.withPrefixMatcher(computedKey);
          result.addElement(new PostfixTemplateLookupElement(template, postfixLiveTemplate.getShortcut()));
        }
      }

      CharSequence documentContent = parameters.getEditor().getDocument().getCharsSequence();
      String possibleKey = postfixLiveTemplate.computeTemplateKeyWithoutContextChecking(documentContent, parameters.getOffset());
      if (StringUtil.isNotEmpty(possibleKey)) {
        result = result.withPrefixMatcher(possibleKey);
        result.restartCompletionOnPrefixChange(StandardPatterns.string().startsWith(possibleKey));
      }
    }
  }

  private static boolean isCompletionEnabled(@NotNull CompletionParameters parameters) {
    if (!parameters.isAutoPopup()) {
      return false;
    }

    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    if (settings == null || !settings.isPostfixTemplatesEnabled() || !settings.isTemplatesCompletionEnabled()) {
      return false;
    }

    return true;
  }
}
