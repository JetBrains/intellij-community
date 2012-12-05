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

package com.intellij.codeInsight.lookup.impl.actions;

import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CompletionProcess;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.impl.ListTemplatesHandler;
import com.intellij.codeInsight.template.impl.SurroundWithTemplateHandler;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public abstract class ChooseItemAction extends EditorAction {
  public ChooseItemAction(Handler handler){
    super(handler);
  }

  protected static class Handler extends EditorActionHandler {
    final boolean focusedOnly;
    final char finishingChar;

    protected Handler(boolean focusedOnly, char finishingChar) {
      this.focusedOnly = focusedOnly;
      this.finishingChar = finishingChar;
    }

    @Override
    public boolean executeInCommand(Editor editor, DataContext dataContext) {
      return false;
    }

    @Override
    public void execute(@NotNull final Editor editor, final DataContext dataContext) {
      final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
      if (lookup == null) {
        throw new AssertionError("The last lookup disposed at: " + LookupImpl.getLastLookupDisposeTrace() + "\n-----------------------\n");
      }
      
      if (finishingChar == Lookup.NORMAL_SELECT_CHAR) {
        if (!lookup.isFocused()) {
          FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CONTROL_ENTER);
        }
      } else if (finishingChar == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_SMART_ENTER);
      } else if (finishingChar == Lookup.REPLACE_SELECT_CHAR) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_REPLACE);
      } else {
        //FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_DOT_ETC);
      }
      
      lookup.uninstallPreview();

      Runnable command = new Runnable() {
        @Override
        public void run() {
          lookup.finishLookup(finishingChar);
        }
      };
      Document doc = editor.getDocument();
      DocCommandGroupId group = DocCommandGroupId.noneGroupId(doc);
      CommandProcessor.getInstance().executeCommand(editor.getProject(), command, "Completion", group, UndoConfirmationPolicy.DEFAULT, doc);
    }


    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
      if (lookup == null) return false;
      if (!lookup.isAvailableToUser()) return false;
      if (focusedOnly && lookup.getPreview() == null && !lookup.isFocused()) return false;
      if (finishingChar == Lookup.NORMAL_SELECT_CHAR && hasTemplatePrefix(lookup, TemplateSettings.ENTER_CHAR) ||
          finishingChar == Lookup.REPLACE_SELECT_CHAR && hasTemplatePrefix(lookup, TemplateSettings.TAB_CHAR)) {
        return false;
      }
      if (finishingChar == Lookup.REPLACE_SELECT_CHAR) {
        if (lookup.isFocused()) {
          return true;
        }
        return !lookup.getItems().isEmpty();
      }

      return true;
    }
    
  }

  public static boolean hasTemplatePrefix(LookupImpl lookup, char shortcutChar) {
    lookup.refreshUi(false, false); // to bring the list model up to date

    CompletionProcess completion = CompletionService.getCompletionService().getCurrentCompletion();
    if (completion == null || !completion.isAutopopupCompletion()) {
      return false;
    }

    if (lookup.isSelectionTouched()) {
      return false;
    }

    final PsiFile file = lookup.getPsiFile();
    if (file == null) return false;

    final Editor editor = lookup.getEditor();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());

    final int end = editor.getCaretModel().getOffset();
    final int start = lookup.getLookupStart();
    final String prefix = !lookup.getItems().isEmpty() ? editor.getDocument().getText(TextRange.create(start, end)) : ListTemplatesHandler
      .getPrefix(editor.getDocument(), end);

    if (TemplateSettings.getInstance().getTemplates(prefix).isEmpty()) {
      return false;
    }

    for (TemplateImpl template : SurroundWithTemplateHandler.getApplicableTemplates(editor, file, false)) {
      if (prefix.equals(template.getKey()) && shortcutChar == TemplateSettings.getInstance().getShortcutChar(template)) {
        return true;
      }
    }
    return false;
  }

  public static class Always extends ChooseItemAction {
    public Always() {
      super(new Handler(false, Lookup.NORMAL_SELECT_CHAR));
    }
  }
  public static class FocusedOnly extends ChooseItemAction {
    public FocusedOnly() {
      super(new Handler(true, Lookup.NORMAL_SELECT_CHAR));
    }
  }
  public static class Replacing extends ChooseItemAction {
    public Replacing() {
      super(new Handler(false, Lookup.REPLACE_SELECT_CHAR));
    }
  }
  public static class CompletingStatement extends ChooseItemAction {
    public CompletingStatement() {
      super(new Handler(true, Lookup.COMPLETE_STATEMENT_SELECT_CHAR));
    }
  }
  public static class ChooseWithDot extends ChooseItemAction {
    public ChooseWithDot() {
      super(new Handler(false, '.'));
    }
  }

}
