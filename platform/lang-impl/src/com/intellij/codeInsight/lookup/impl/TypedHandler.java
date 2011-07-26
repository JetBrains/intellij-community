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

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.editorActions.AutoHardWrapHandler;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.editorActions.TypedActionHandlerBase;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.CommandProcessorEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.actionSystem.TypedActionHandler;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class TypedHandler extends TypedActionHandlerBase {
  private static boolean inside = false;

  public TypedHandler(TypedActionHandler originalHandler){
    super(originalHandler);
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  public void execute(@NotNull final Editor editor, final char charTyped, @NotNull final DataContext dataContext){
    assert !inside;
    inside = true;
    try {
      final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
      if (lookup == null){
        if (myOriginalHandler != null) myOriginalHandler.execute(editor, charTyped, dataContext);
        return;
      }

      if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), editor.getProject())) {
        return;
      }

      final CharFilter.Result result = getLookupAction(charTyped, lookup);
      lookup.performGuardedChange(new Runnable() {
        public void run() {
          EditorModificationUtil.deleteSelectedText(editor);
        }
      });
      if (result == CharFilter.Result.ADD_TO_PREFIX) {
        Document document = editor.getDocument();
        long modificationStamp = document.getModificationStamp();

        lookup.performGuardedChange(new Runnable() {
          public void run() {
            EditorModificationUtil.typeInStringAtCaretHonorBlockSelection(editor, String.valueOf(charTyped), true);
          }
        });
        lookup.appendPrefix(charTyped);
        if (lookup.isStartCompletionWhenNothingMatches() && lookup.getItems().isEmpty()) {
          CompletionAutoPopupHandler.scheduleAutoPopup(editor, null);
        }

        AutoHardWrapHandler.getInstance().wrapLineIfNecessary(editor, dataContext, modificationStamp);

        final CompletionProgressIndicator completion = CompletionServiceImpl.getCompletionService().getCurrentCompletion();
        if (completion != null) {
          completion.prefixUpdated();
        }
        return;
      }

      if (result == CharFilter.Result.SELECT_ITEM_AND_FINISH_LOOKUP && lookup.isFocused()) {
        LookupElement item = lookup.getCurrentItem();
        if (item != null){
          inside = false;
          ((CommandProcessorEx)CommandProcessor.getInstance()).enterModal();
          try {
            finishLookup(charTyped, lookup, new Runnable() {
              public void run() {
                EditorModificationUtil.typeInStringAtCaretHonorBlockSelection(editor, String.valueOf(charTyped), true);
              }
            });
          }
          finally {
            ((CommandProcessorEx)CommandProcessor.getInstance()).leaveModal();
          }
          return;
        }
      }

      lookup.hide();
      if (myOriginalHandler != null) myOriginalHandler.execute(editor, charTyped, dataContext);
    }
    finally {
      inside = false;
    }
  }

  public static void finishLookup(final char charTyped, @NotNull final LookupImpl lookup, final Runnable baseChange) {
    Editor editor = lookup.getEditor();
    FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_DOT_ETC);
    CompletionProcess process = CompletionService.getCompletionService().getCurrentCompletion();
    SelectionModel sm = editor.getSelectionModel();
    final boolean smartUndo = !sm.hasSelection() && !sm.hasBlockSelection() && process != null && process.isAutopopupCompletion();
    final Runnable restore = CodeCompletionHandlerBase.rememberDocumentState(editor);
    if (smartUndo) {
      CommandProcessor.getInstance().executeCommand(editor.getProject(), new Runnable() {
        @Override
        public void run() {
          lookup.performGuardedChange(baseChange);
        }
      }, null, "Just insert the completion char");
    }

    CommandProcessor.getInstance().executeCommand(editor.getProject(), new Runnable() {
      @Override
      public void run() {
        if (smartUndo) {
          AccessToken token = WriteAction.start();
          try {
            lookup.performGuardedChange(restore);
          }
          finally {
            token.finish();
          }
        }
        lookup.finishLookup(charTyped);
      }
    }, null, "Undo inserting the completion char and select the item");
  }

  static CharFilter.Result getLookupAction(final char charTyped, final LookupImpl lookup) {
    final CharFilter.Result filtersDecision = getFiltersDecision(charTyped, lookup);

    final LookupElement currentItem = lookup.getCurrentItem();
    if (currentItem != null && charTyped != ' ') {
      if (charTyped != '*' || filtersDecision != CharFilter.Result.SELECT_ITEM_AND_FINISH_LOOKUP) {
        String postfix = lookup.getAdditionalPrefix() + charTyped;
        final PrefixMatcher matcher = lookup.itemMatcher(currentItem);
        if (matcher.cloneWithPrefix(matcher.getPrefix() + postfix).prefixMatches(currentItem)) {
          return CharFilter.Result.ADD_TO_PREFIX;
        }
        for (final LookupElement element : lookup.getItems()) {
          PrefixMatcher elementMatcher = lookup.itemMatcher(element);
          if (elementMatcher.cloneWithPrefix(elementMatcher.getPrefix() + postfix).prefixMatches(element)) {
            return CharFilter.Result.ADD_TO_PREFIX;
          }
        }
      }
    }

    if (filtersDecision != null) return filtersDecision;
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
