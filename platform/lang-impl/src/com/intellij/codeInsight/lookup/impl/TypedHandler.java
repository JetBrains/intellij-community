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

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.extensions.Extensions;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class TypedHandler implements TypedActionHandler {
  private final TypedActionHandler myOriginalHandler;

  public TypedHandler(TypedActionHandler originalHandler){
    myOriginalHandler = originalHandler;
  }

  public void execute(@NotNull final Editor editor, final char charTyped, @NotNull DataContext dataContext){
    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup == null){
      myOriginalHandler.execute(editor, charTyped, dataContext);
      return;
    }

    final LookupElement currentItem = lookup.getCurrentItem();
    final CharFilter.Result result = getLookupAction(charTyped, currentItem, lookup);

    CommandProcessor.getInstance().executeCommand(PlatformDataKeys.PROJECT.getData(dataContext), new Runnable() {
      public void run() {
        EditorModificationUtil.deleteSelectedText(editor);
        if (result == CharFilter.Result.ADD_TO_PREFIX) {
          lookup.setAdditionalPrefix(lookup.getAdditionalPrefix() + charTyped);
          EditorModificationUtil.insertStringAtCaret(editor, String.valueOf(charTyped));
        }
      }
    }, "", editor.getDocument());

    if (result == CharFilter.Result.ADD_TO_PREFIX){
      return;
    }

    if (result == CharFilter.Result.SELECT_ITEM_AND_FINISH_LOOKUP && lookup.isFocused()) {
      LookupElement item = lookup.getCurrentItem();
      if (item != null){
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_DOT_ETC);
        lookup.finishLookup(charTyped);
        return;
      }
    }

    lookup.hide();
    myOriginalHandler.execute(editor, charTyped, dataContext);
  }

  private static CharFilter.Result getLookupAction(final char charTyped, final LookupElement currentItem, final LookupImpl lookup) {
    if (currentItem != null && charTyped != ' ') {
      String postfix = lookup.getAdditionalPrefix() + charTyped;
      final PrefixMatcher matcher = currentItem.getPrefixMatcher();
      if (matcher.cloneWithPrefix(matcher.getPrefix() + postfix).prefixMatches(currentItem)) {
        return CharFilter.Result.ADD_TO_PREFIX;
      }
      for (final LookupElement element : lookup.getItems()) {
        if (element.isPrefixMatched() && element.getPrefixMatcher().cloneWithPrefix(element.getPrefixMatcher().getPrefix() + postfix).prefixMatches(element)) {
          return CharFilter.Result.ADD_TO_PREFIX;
        }
      }
    }
    final CharFilter[] filters = Extensions.getExtensions(CharFilter.EP_NAME);
    for (final CharFilter extension : filters) {
      final CharFilter.Result result = extension.acceptChar(charTyped, lookup.getMinPrefixLength() + lookup.getAdditionalPrefix().length(), lookup);
      if (result != null) {
        return result;
      }
    }
    throw new AssertionError("Typed char not handler by char filter: c=" + charTyped + "; prefix=" + currentItem + "; filters=" + Arrays.toString(filters));
  }
}
