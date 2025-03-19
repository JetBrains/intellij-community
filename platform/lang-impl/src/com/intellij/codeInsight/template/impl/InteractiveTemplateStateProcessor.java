// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.*;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.idea.ActionsBundle;
import com.intellij.lang.ASTNode;
import com.intellij.lang.LangBundle;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.CoreCodeStyleUtil;
import com.intellij.psi.impl.source.codeStyle.NewLineIndentMarkerProvider;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import com.intellij.util.CharTable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class InteractiveTemplateStateProcessor implements TemplateStateProcessor {
  private static final @NonNls String DUMMY_IDENTIFIER = "xxx";

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
    return doInsertNewLineIndentMarker(file, document, offset);
  }

  /**
   * Allows checking if given offset points to a white space element within the given PSI file and return that white space
   * element in the case of positive answer.
   *
   * @param file    target file
   * @param offset  offset that might point to a white space element within the given PSI file
   * @return        target a white space element for the given offset within the given file (if any); {@code null} otherwise
   */
  @Override
  public PsiElement findWhiteSpaceNode(PsiFile file, int offset) {
    return doFindWhiteSpaceNode(file, offset).first;
  }

  private static @NotNull Pair<PsiElement, CharTable> doFindWhiteSpaceNode(@NotNull PsiFile file, int offset) {
    ASTNode astNode = SourceTreeToPsiMap.psiElementToTree(file);
    if (!(astNode instanceof FileElement)) {
      return new Pair<>(null, null);
    }
    PsiElement elementAt = InjectedLanguageManager.getInstance(file.getProject()).findInjectedElementAt(file, offset);
    final CharTable charTable = ((FileElement)astNode).getCharTable();
    if (elementAt == null) {
      elementAt = CoreCodeStyleUtil.findElementInTreeWithFormatterEnabled(file, offset);
    }

    if( elementAt == null) {
      return new Pair<>(null, charTable);
    }
    ASTNode node = elementAt.getNode();
    if (node == null || node.getElementType() != TokenType.WHITE_SPACE) {
      return new Pair<>(null, charTable);
    }
    return Pair.create(elementAt, charTable);
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

      AsyncEditorLoader.Companion.performWhenLoaded(editor, () ->
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
    EditorModificationUtilEx.insertStringAtCaret(editor, first.getLookupString());
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

  /**
   * Formatter trims line that contains white spaces symbols only, however, there is a possible case that we want
   * to preserve them for a particular line
   * (e.g., for live template that defines line with whitespaces that contains $END$ marker: templateText   $END$).
   * <p/>
   * Current approach is to do the following:
   * <pre>
   * <ol>
   *   <li>Insert dummy text at the end of the blank line which white space symbols should be preserved;</li>
   *   <li>Perform formatting;</li>
   *   <li>Remove dummy text;</li>
   * </ol>
   * </pre>
   * <p/>
   * This method inserts that dummy comment (fallback to identifier {@code xxx}, see {@link #createMarker(PsiFile, int)})
   * if necessary.
   * <p/>
   * <b>Note:</b> it's expected that the whole white space region that contains given offset is processed in a way that all
   * {@link RangeMarker range markers} registered for the given offset are expanded to the whole white space region.
   * E.g., there is a possible case that a particular range marker serves for defining formatting range, hence, its start/end offsets
   * are updated correspondingly after the current method call, and a whole white space region is reformatted.
   *
   * @param file        target PSI file
   * @param document    target document
   * @param offset      offset that defines end boundary of the target line text fragment (start boundary is the first line's symbol)
   * @return            text range that points to the newly inserted dummy text if any; {@code null} otherwise
   * @throws IncorrectOperationException  if given file is read-only
   */
  private static @Nullable TextRange doInsertNewLineIndentMarker(@NotNull PsiFile file, @NotNull Document document, int offset) {
    CharSequence text = document.getImmutableCharSequence();
    if (offset <= 0 || offset >= text.length() || !isWhiteSpaceSymbol(text.charAt(offset))) {
      return null;
    }

    if (!isWhiteSpaceSymbol(text.charAt(offset - 1))) {
      return null; // no whitespaces before offset
    }

    int end = offset;
    for (; end < text.length(); end++) {
      if (text.charAt(end) == '\n') {
        break; // the line is empty till the end
      }
      if (!isWhiteSpaceSymbol(text.charAt(end))) {
        return null;
      }
    }

    String marker = createMarker(file, offset);
    document.insertString(offset, marker);
    return new TextRange(offset, offset + marker.length());
  }

  private static boolean isWhiteSpaceSymbol(char c) {
    return c == ' ' || c == '\t' || c == '\n';
  }

  private static @NotNull String createMarker(@NotNull PsiFile file, int offset) {
    Project project = file.getProject();
    PsiElement injectedElement = InjectedLanguageManager.getInstance(project).findInjectedElementAt(file, offset);
    Language language = injectedElement != null ? injectedElement.getLanguage() : PsiUtilCore.getLanguageAtOffset(file, offset);

    CoreCodeStyleUtil.setSequentialProcessingAllowed(false);
    NewLineIndentMarkerProvider markerProvider = NewLineIndentMarkerProvider.EP.forLanguage(language);
    String marker = markerProvider == null ? null : markerProvider.createMarker(file, offset);
    if (marker != null) {
      return marker;
    }

    PsiComment comment = null;
    try {
      comment = PsiParserFacade.getInstance(project).createLineOrBlockCommentFromText(language, "");
    }
    catch (Throwable ignored) {
    }
    String text = comment != null ? comment.getText() : null;
    return text != null ? text : DUMMY_IDENTIFIER;
  }

  private static final class MyBasicUndoableAction extends BasicUndoableAction implements Disposable {
    private final Project myProject;
    private @Nullable TemplateState myTemplateState;

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
