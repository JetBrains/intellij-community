// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.lookup.impl.actions;

import com.intellij.codeInsight.completion.CompletionProcess;
import com.intellij.codeInsight.completion.CompletionService;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupFocusDegree;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.TemplateActionContext;
import com.intellij.codeInsight.template.impl.*;
import com.intellij.codeInsight.template.impl.editorActions.ExpandLiveTemplateCustomAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.LatencyAwareEditorAction;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class ChooseItemAction extends EditorAction implements HintManagerImpl.ActionToIgnore, LatencyAwareEditorAction {
  public ChooseItemAction(Handler handler) {
    super(handler);
  }

  public static class Handler extends EditorActionHandler {
    final boolean focusedOnly;
    final char finishingChar;

    public Handler(boolean focusedOnly, char finishingChar) {
      this.focusedOnly = focusedOnly;
      this.finishingChar = finishingChar;
    }

    @Override
    public void doExecute(@NotNull Editor editor, @Nullable Caret caret, DataContext dataContext) {
      final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
      assert lookup != null;

      if ((finishingChar == Lookup.NORMAL_SELECT_CHAR || finishingChar == Lookup.REPLACE_SELECT_CHAR) &&
          hasTemplatePrefix(lookup, finishingChar)) {
        lookup.hideLookup(true);

        ExpandLiveTemplateCustomAction.createExpandTemplateHandler(finishingChar).execute(editor, null, dataContext);

        return;
      }

      lookup.finishLookup(finishingChar);
    }


    @Override
    public boolean isEnabledForCaret(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
      LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(editor);
      if (lookup == null) return false;
      if (!lookup.isAvailableToUser()) return false;
      if (lookup.getCurrentItemOrEmpty() == null) return false;
      if (focusedOnly && lookup.getLookupFocusDegree() == LookupFocusDegree.UNFOCUSED) return false;
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
    final int offset = editor.getCaretModel().getOffset();
    PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());

    final LiveTemplateLookupElement liveTemplateLookup = ContainerUtil.findInstance(lookup.getItems(), LiveTemplateLookupElement.class);
    if (liveTemplateLookup == null || !liveTemplateLookup.sudden) {
      // Lookup doesn't contain sudden live templates. It means that
      // - there are no live template with given key:
      //    in this case we should find live template with appropriate prefix (custom live templates doesn't participate in this action).
      // - completion provider worked too long:
      //    in this case we should check custom templates that provides completion lookup.
      if (LiveTemplateCompletionContributor.customTemplateAvailableAndHasCompletionItem(shortcutChar, editor, file, offset)) {
        return true;
      }

      List<TemplateImpl> templates = TemplateManagerImpl.listApplicableTemplateWithInsertingDummyIdentifier(
        TemplateActionContext.expanding(file, editor)
      );
      TemplateImpl template = LiveTemplateCompletionContributor.findFullMatchedApplicableTemplate(editor, offset, templates);
      if (template != null && shortcutChar == TemplateSettings.getInstance().getShortcutChar(template)) {
        return true;
      }
      return false;
    }

    return liveTemplateLookup.getTemplateShortcut() == shortcutChar;
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
