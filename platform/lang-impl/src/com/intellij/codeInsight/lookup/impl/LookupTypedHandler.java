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

package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.editorActions.AutoHardWrapHandler;
import com.intellij.codeInsight.editorActions.TypedHandler;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.actions.ChooseItemAction;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.DataManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class LookupTypedHandler extends TypedHandlerDelegate {

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  @Override
  public Result beforeCharTyped(final char charTyped,
                                Project project,
                                final Editor editor,
                                PsiFile file,
                                FileType fileType) {
    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
    if (lookup == null){
      return Result.CONTINUE;
    }

    if (charTyped == ' ' && ChooseItemAction.hasTemplatePrefix(lookup, TemplateSettings.SPACE_CHAR)) {
      return Result.CONTINUE;
    }

    final CharFilter.Result result = getLookupAction(charTyped, lookup);
    if (lookup.isLookupDisposed()) {
      return Result.CONTINUE;
    }

    if (!lookup.performGuardedChange(new Runnable() {
      @Override
      public void run() {
        EditorModificationUtil.deleteSelectedText(editor);
      }
    })) {
      return Result.STOP;
    }
    if (result == CharFilter.Result.ADD_TO_PREFIX) {
      Document document = editor.getDocument();
      long modificationStamp = document.getModificationStamp();

      if (!lookup.performGuardedChange(new Runnable() {
        @Override
        public void run() {
          EditorModificationUtil.typeInStringAtCaretHonorBlockSelection(editor, String.valueOf(charTyped), true);
        }
      })) {
        return Result.STOP;
      }
      lookup.appendPrefix(charTyped);
      if (lookup.isStartCompletionWhenNothingMatches() && lookup.getItems().isEmpty()) {
        final CompletionProgressIndicator completion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
        if (completion != null) {
          completion.scheduleRestart();
        } else {
          AutoPopupController.getInstance(editor.getProject()).scheduleAutoPopup(editor);
        }
      }

      AutoHardWrapHandler.getInstance().wrapLineIfNecessary(editor, DataManager.getInstance().getDataContext(editor.getContentComponent()), modificationStamp);

      final CompletionProgressIndicator completion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
      if (completion != null) {
        completion.prefixUpdated();
      }
      return Result.STOP;
    }

    if (result == CharFilter.Result.SELECT_ITEM_AND_FINISH_LOOKUP && lookup.isFocused()) {
      LookupElement item = lookup.getCurrentItem();
      if (item != null) {
        if (completeTillTypedCharOccurrence(charTyped, lookup, item)) {
          return Result.STOP;
        }

        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_DOT_ETC);
        lookup.finishLookup(charTyped);
        return Result.STOP;
      }
    }

    lookup.hide();
    TypedHandler.autoPopupCompletion(editor, charTyped, project, file);
    return Result.CONTINUE;
  }

  private static boolean completeTillTypedCharOccurrence(char charTyped, LookupImpl lookup, LookupElement item) {
    PrefixMatcher matcher = lookup.itemMatcher(item);
    final String oldPrefix = matcher.getPrefix() + lookup.getAdditionalPrefix();
    PrefixMatcher expanded = matcher.cloneWithPrefix(oldPrefix + charTyped);
    if (expanded.prefixMatches(item)) {
      for (String s : item.getAllLookupStrings()) {
        if (matcher.prefixMatches(s)) {
          int i = -1;
          while (true) {
            i = s.indexOf(charTyped, i + 1);
            if (i < 0)  break;
            final String newPrefix = s.substring(0, i + 1);
            if (expanded.prefixMatches(newPrefix)) {
              lookup.replacePrefix(oldPrefix, newPrefix);
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  static CharFilter.Result getLookupAction(final char charTyped, final LookupImpl lookup) {
    final CharFilter.Result filtersDecision = getFiltersDecision(charTyped, lookup);

    final LookupElement currentItem = lookup.getCurrentItem();
    if (currentItem != null && charTyped != ' ') {
      String postfix = lookup.getAdditionalPrefix() + charTyped;
      final PrefixMatcher matcher = lookup.itemMatcher(currentItem);
      for (String lookupString : currentItem.getAllLookupStrings()) {
        if (lookupString.startsWith(matcher.getPrefix() + postfix)) {
          return CharFilter.Result.ADD_TO_PREFIX;
        }
      }
    }

    if (filtersDecision != null) {
      return filtersDecision;
    }
    throw new AssertionError("Typed char not handler by char filter: c=" + charTyped +
                             "; prefix=" + currentItem +
                             "; filters=" + Arrays.toString(getFilters()));
  }

  @Nullable
  private static CharFilter.Result getFiltersDecision(char charTyped, LookupImpl lookup) {
    LookupElement item = lookup.getCurrentItem();
    int prefixLength = item == null ? lookup.getAdditionalPrefix().length(): lookup.itemPattern(item).length();

    for (final CharFilter extension : getFilters()) {
      final CharFilter.Result result = extension.acceptChar(charTyped, prefixLength, lookup);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private static CharFilter[] getFilters() {
    return Extensions.getExtensions(CharFilter.EP_NAME);
  }
}
