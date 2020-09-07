// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.codeStyle.CodeStyleManagerImpl;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class InteractiveTemplateStateProcessor implements TemplateStateProcessor {
  private boolean myLookupShown;

  @Override
  public boolean isUndoOrRedoInProgress(Project project) {
    return UndoManager.getInstance(project).isUndoOrRedoInProgress();
  }

  @Override
  public void registerUndoableAction(TemplateState state, Project project, Document document) {
    MyBasicUndoableAction undoableAction = new MyBasicUndoableAction(state, project, document);
    UndoManager.getInstance(project).undoableActionPerformed(undoableAction);
    Disposer.register(state, undoableAction);
  }

  @Override
  public TextRange insertNewLineIndentMarker(PsiFile file, Document document, int offset) {
    return CodeStyleManagerImpl.insertNewLineIndentMarker(file, document, offset);
  }

  @Override
  public PsiElement findWhiteSpaceNode(PsiFile file, int offset) {
    return CodeStyleManagerImpl.findWhiteSpaceNode(file, offset);
  }

  @Override
  public void logTemplate(Project project, TemplateImpl template, Language language) {
    LiveTemplateRunLogger.log(project, template, language);
  }

  @Override
  public void runLookup(TemplateState state, Project project, Editor editor, LookupElement @NotNull [] elements,
                        Expression expressionNode) {
    List<TemplateExpressionLookupElement> lookupItems = new ArrayList<>();
    for (int i = 0; i < elements.length; i++) {
      lookupItems.add(new TemplateExpressionLookupElement(state, elements[i], i));
    }
    if (((TemplateManagerImpl)TemplateManager.getInstance(project)).shouldSkipInTests()) {
      insertSingleItem(editor, lookupItems);
    }
    else {
      for (LookupElement lookupItem : lookupItems) {
        assert lookupItem != null : expressionNode;
      }

      AsyncEditorLoader.performWhenLoaded(editor, () ->
        runLookup(state, lookupItems, project, editor, expressionNode.getAdvertisingText(), expressionNode.getLookupFocusDegree()));
    }
  }

  private void runLookup(TemplateState state, final List<TemplateExpressionLookupElement> lookupItems, Project project, Editor editor,
                         @Nullable @NlsContexts.PopupAdvertisement String advertisingText, @NotNull LookupFocusDegree lookupFocusDegree) {
    if (state.isDisposed()) return;

    final LookupManager lookupManager = LookupManager.getInstance(project);

    final LookupImpl lookup = (LookupImpl)lookupManager.showLookup(editor, lookupItems.toArray(LookupElement.EMPTY_ARRAY));
    if (lookup == null) return;

    if (CodeInsightSettings.getInstance().AUTO_POPUP_COMPLETION_LOOKUP && editor.getUserData(InplaceRefactoring.INPLACE_RENAMER) == null) {
      lookup.setStartCompletionWhenNothingMatches(true);
    }

    if (advertisingText != null) {
      lookup.addAdvertisement(advertisingText, null);
    }
    else {
      ActionManager am = ActionManager.getInstance();
      String enterShortcut = KeymapUtil.getFirstKeyboardShortcutText(am.getAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM));
      String tabShortcut = KeymapUtil.getFirstKeyboardShortcutText(am.getAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE));
      lookup.addAdvertisement(LangBundle.message("popup.advertisement.press.or.to.replace", enterShortcut, tabShortcut), null);
    }
    lookup.setLookupFocusDegree(lookupFocusDegree);
    lookup.refreshUi(true, true);
    myLookupShown = true;
    lookup.addLookupListener(new LookupListener() {
      @Override
      public void lookupCanceled(@NotNull LookupEvent event) {
        lookup.removeLookupListener(this);
        myLookupShown = false;
      }

      @Override
      public void itemSelected(@NotNull LookupEvent event) {
        lookup.removeLookupListener(this);
        if (state.isFinished()) return;
        myLookupShown = false;

        LookupElement item = event.getItem();
        if (item instanceof TemplateExpressionLookupElement) {
          ((TemplateExpressionLookupElement)item).handleTemplateInsert(lookupItems, event.getCompletionChar());
        }
      }
    });
  }

  private static void insertSingleItem(Editor editor, List<TemplateExpressionLookupElement> lookupItems) {
    TemplateExpressionLookupElement first = lookupItems.get(0);
    EditorModificationUtil.insertStringAtCaret(editor, first.getLookupString());
    first.handleTemplateInsert(lookupItems, Lookup.AUTO_INSERT_SELECT_CHAR);
  }

  @Override
  public boolean isLookupShown() {
    return myLookupShown;
  }

  @Override
  public boolean skipSettingFinalEditorState(Project project) {
    return !((TemplateManagerImpl)TemplateManager.getInstance(project)).shouldSkipInTests();
  }

  @Override
  public boolean isCaretOutsideCurrentSegment(Editor editor, TemplateSegments segments, int currentSegmentNumber, String commandName) {
    if (editor != null && currentSegmentNumber >= 0) {
      final int offset = editor.getCaretModel().getOffset();
      boolean hasSelection = editor.getSelectionModel().hasSelection();

      final int segmentStart = segments.getSegmentStart(currentSegmentNumber);
      if (offset < segmentStart ||
          !hasSelection && offset == segmentStart && ActionsBundle.actionText(IdeActions.ACTION_EDITOR_BACKSPACE).equals(commandName)) return true;

      final int segmentEnd = segments.getSegmentEnd(currentSegmentNumber);
      if (offset > segmentEnd ||
          !hasSelection && offset == segmentEnd && ActionsBundle.actionText(IdeActions.ACTION_EDITOR_DELETE).equals(commandName)) return true;
    }
    return false;
  }

  private static final class MyBasicUndoableAction extends BasicUndoableAction implements Disposable {
    private final Project myProject;
    @Nullable
    private TemplateState myTemplateState;

    private MyBasicUndoableAction(@NotNull TemplateState templateState, Project project, @Nullable Document document) {
      super(document != null ? new DocumentReference[]{DocumentReferenceManager.getInstance().create(document)} : null);
      myTemplateState = templateState;
      myProject = project;
    }

    @Override
    public void undo() {
      if (myTemplateState != null) {
        LookupManager.getInstance(myProject).hideActiveLookup();
        myTemplateState.cancelTemplate();
      }
    }

    @Override
    public void redo() {
    }

    @Override
    public void dispose() {
      myTemplateState = null;
    }
  }
}
