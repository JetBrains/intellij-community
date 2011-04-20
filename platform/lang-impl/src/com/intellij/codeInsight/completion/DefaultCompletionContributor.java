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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.documentation.actions.ShowQuickDocInfoAction;
import com.intellij.codeInsight.hint.actions.ShowImplementationsAction;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * @author peter
 */
public class DefaultCompletionContributor extends CompletionContributor {

  @Nullable
  public static String getDefaultAdvertisementText(@NotNull final CompletionParameters parameters) {
    final Random random = new Random();
    if (random.nextInt(5) < 2 && CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_DOT_ETC)) {
      return LangBundle.message("completion.dot.etc.ad");
    }
    if (random.nextInt(5) < 2 && CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_SMART_ENTER)) {
      final String shortcut = getActionShortcut(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT);
      if (shortcut != null) {
        return LangBundle.message("completion.smart.enter.ad", shortcut);
      }
    }

    if (random.nextInt(5) < 2 &&
        CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_DOT_ETC)) {
      return LangBundle.message("completion.dot.etc.ad");
    }
    if (random.nextInt(5) < 2 &&
        CompletionUtil.shouldShowFeature(parameters, CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_SMART_ENTER)) {
      final String shortcut = getActionShortcut(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT);
      if (shortcut != null) {
        return LangBundle.message("completion.smart.enter.ad", shortcut);
      }
    }

    if (random.nextInt(5) < 2 &&
        (CompletionUtil.shouldShowFeature(parameters, ShowQuickDocInfoAction.CODEASSISTS_QUICKJAVADOC_FEATURE) ||
         CompletionUtil.shouldShowFeature(parameters, ShowQuickDocInfoAction.CODEASSISTS_QUICKJAVADOC_LOOKUP_FEATURE))) {
      final String shortcut = getActionShortcut(IdeActions.ACTION_QUICK_JAVADOC);
      if (shortcut != null) {
        return LangBundle.message("completion.quick.javadoc.ad", shortcut);
      }
    }

    if (CompletionUtil.shouldShowFeature(parameters, ShowImplementationsAction.CODEASSISTS_QUICKDEFINITION_FEATURE) ||
        CompletionUtil.shouldShowFeature(parameters, ShowImplementationsAction.CODEASSISTS_QUICKDEFINITION_LOOKUP_FEATURE)) {
      final String shortcut = getActionShortcut(IdeActions.ACTION_QUICK_IMPLEMENTATIONS);
      if (shortcut != null) {
        return LangBundle.message("completion.quick.implementations.ad", shortcut);
      }
    }

    return null;
  }

  public String handleEmptyLookup(@NotNull final CompletionParameters parameters, final Editor editor) {
    return LangBundle.message("completion.no.suggestions");
  }

  @Override
  public AutoCompletionDecision handleAutoCompletionPossibility(AutoCompletionContext context) {
    final LookupElement[] items = context.getItems();
    if (items.length == 1) {
      final LookupElement item = items[0];
      if (!StringUtil.isEmpty(context.getLookup().itemPattern(item)) || context.getParameters().getCompletionType() == CompletionType.SMART) {
        return AutoCompletionDecision.insertItem(item);
      }
    }
    return null;
  }

}
