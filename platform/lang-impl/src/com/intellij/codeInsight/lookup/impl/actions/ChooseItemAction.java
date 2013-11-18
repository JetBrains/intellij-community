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

package com.intellij.codeInsight.lookup.impl.actions;

import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CompletionProcess;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.CustomLiveTemplate;
import com.intellij.codeInsight.template.CustomLiveTemplateBase;
import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
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
      } else if (finishingChar == '.')  {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_FINISH_BY_CONTROL_DOT);
      }

      lookup.finishLookup(finishingChar);
    }


    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
      if (lookup == null) return false;
      if (!lookup.isAvailableToUser()) return false;
      if (focusedOnly && lookup.getFocusDegree() == LookupImpl.FocusDegree.UNFOCUSED) return false;
      if (finishingChar == Lookup.NORMAL_SELECT_CHAR && hasTemplatePrefix(lookup, TemplateSettings.ENTER_CHAR) ||
          finishingChar == Lookup.REPLACE_SELECT_CHAR && hasTemplatePrefix(lookup, TemplateSettings.TAB_CHAR)) {
        return false;
      }
      if (finishingChar == Lookup.REPLACE_SELECT_CHAR) {
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

    final LiveTemplateLookupElement liveTemplateLookup = ContainerUtil.findInstance(lookup.getItems(), LiveTemplateLookupElement.class);
    if (liveTemplateLookup == null) {
      // Lookup doesn't contain live templates. It means that 
      // - there are no any live template:
      //    in this case we should find live template with appropriate prefix (custom live templates doesn't participate in this action). 
      // - completion provider worked too long:
      //    in this case we should check custom templates that provides completion lookup.
      
      final CustomTemplateCallback callback = new CustomTemplateCallback(editor, file, false);
      for (CustomLiveTemplate customLiveTemplate : CustomLiveTemplate.EP_NAME.getExtensions()) {
        if (customLiveTemplate instanceof CustomLiveTemplateBase) {
          final int offset = editor.getCaretModel().getOffset();
          if (customLiveTemplate.getShortcut() == shortcutChar 
              && ((CustomLiveTemplateBase)customLiveTemplate).hasCompletionItem(file, offset)) {
            return customLiveTemplate.computeTemplateKey(callback) != null;
          }
        }
      }


      final int end = editor.getCaretModel().getOffset();
      final int start = lookup.getLookupStart();
      final String prefix = !lookup.getItems().isEmpty()
                            ? editor.getDocument().getText(TextRange.create(start, end))
                            : ListTemplatesHandler.getPrefix(editor.getDocument(), end, false);

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

    return TemplateSettings.getInstance().getShortcutChar(liveTemplateLookup.getTemplate()) == shortcutChar;
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
