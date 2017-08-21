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
package com.intellij.codeInsight.template.postfix.completion;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor;
import com.intellij.codeInsight.template.postfix.settings.PostfixTemplatesSettings;
import com.intellij.codeInsight.template.postfix.templates.PostfixLiveTemplate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.patterns.StandardPatterns;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.completion.PostfixTemplateCompletionContributor.getPostfixLiveTemplate;

class PostfixTemplatesCompletionProvider extends CompletionProvider<CompletionParameters> {
  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters, ProcessingContext context, @NotNull CompletionResultSet result) {
    Editor editor = parameters.getEditor();
    if (!isCompletionEnabled(parameters) || LiveTemplateCompletionContributor.shouldShowAllTemplates() ||
        editor.getCaretModel().getCaretCount() != 1) {
      /*
        disabled or covered with {@link com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor}
       */
      return;
    }

    PsiFile originalFile = parameters.getOriginalFile();
    PostfixLiveTemplate postfixLiveTemplate = getPostfixLiveTemplate(originalFile, editor);
    if (postfixLiveTemplate != null) {
      postfixLiveTemplate.addCompletions(parameters, result.withPrefixMatcher(new MyPrefixMatcher(result.getPrefixMatcher().getPrefix())));
      String possibleKey = postfixLiveTemplate.computeTemplateKeyWithoutContextChecking(new CustomTemplateCallback(editor, originalFile));
      if (possibleKey != null) {
        result = result.withPrefixMatcher(possibleKey);
        result.restartCompletionOnPrefixChange(
          StandardPatterns.string().oneOf(postfixLiveTemplate.getAllTemplateKeys(originalFile, parameters.getOffset())));
      }
    }
  }

  private static boolean isCompletionEnabled(@NotNull CompletionParameters parameters) {
    ProgressManager.checkCanceled();
    if (!parameters.isAutoPopup()) {
      return false;
    }

    PostfixTemplatesSettings settings = PostfixTemplatesSettings.getInstance();
    if (settings == null || !settings.isPostfixTemplatesEnabled() || !settings.isTemplatesCompletionEnabled()) {
      return false;
    }

    return true;
  }

  private static class MyPrefixMatcher extends PrefixMatcher {
    protected MyPrefixMatcher(String prefix) {
      super(prefix);
    }

    @Override
    public boolean prefixMatches(@NotNull String name) {
      return name.equalsIgnoreCase(myPrefix);
    }

    @NotNull
    @Override
    public PrefixMatcher cloneWithPrefix(@NotNull String prefix) {
      return new MyPrefixMatcher(prefix);
    }
  }
}
