// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileWithOneLanguage;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BackspaceHandler extends EditorWriteActionHandler.ForEachCaret {
  private static final Logger LOGGER = Logger.getInstance(BackspaceHandler.class);

  protected final EditorActionHandler myOriginalHandler;

  public BackspaceHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  @Override
  public void executeWriteAction(@NotNull Editor editor, @NotNull Caret caret, DataContext dataContext) {
    if (!handleBackspace(editor, caret, dataContext, false)) {
      myOriginalHandler.execute(editor, caret, dataContext);
    }
  }

  boolean handleBackspace(@NotNull Editor editor, @NotNull Caret caret, @NotNull DataContext dataContext, boolean toWordStart) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) return false;

    PsiFile file = PsiUtilBase.getPsiFileInEditor(editor, project);

    if (file == null) return false;

    if (editor.getSelectionModel().hasSelection()) return false;

    int offset = DocumentUtil.getPreviousCodePointOffset(editor.getDocument(), editor.getCaretModel().getOffset());
    if (offset < 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    int c = Character.codePointAt(chars, offset);

    final Editor injectedEditor = TypedHandler.injectedEditorIfCharTypedIsSignificant(c, editor, file);
    final Editor originalEditor = editor;
    if (injectedEditor != editor) {
      int injectedOffset = injectedEditor.getCaretModel().getOffset();
      if (isOffsetInsideInjected(injectedEditor, injectedOffset)) {
        file = PsiDocumentManager.getInstance(project).getPsiFile(injectedEditor.getDocument());
        editor = injectedEditor;
        offset = DocumentUtil.getPreviousCodePointOffset(injectedEditor.getDocument(), injectedOffset);
      }
    }

    final List<BackspaceHandlerDelegate> delegates = BackspaceHandlerDelegate.EP_NAME.getExtensionList();
    if (!toWordStart && Character.isBmpCodePoint(c)) {
      for(BackspaceHandlerDelegate delegate: delegates) {
        delegate.beforeCharDeleted((char)c, file, editor);
      }
    }

    FileType fileType = file.getFileType();
    final QuoteHandler quoteHandler = TypedHandler.getQuoteHandler(file, editor);

    HighlighterIterator hiterator = editor.getHighlighter().createIterator(offset);
    boolean wasClosingQuote = quoteHandler != null && quoteHandler.isClosingQuote(hiterator, offset);

    myOriginalHandler.execute(originalEditor, caret, dataContext);

    if (!toWordStart && Character.isBmpCodePoint(c)) {
      try {
        for(BackspaceHandlerDelegate delegate: delegates) {
          if (delegate.charDeleted((char)c, file, editor)) {
            return true;
          }
        }
      }
      finally {
        deleteCustomFoldRegionIfNeeded(caret);
      }
    }

    if (offset >= editor.getDocument().getTextLength()) return true;

    chars = editor.getDocument().getCharsSequence();
    if ((c == '(' || c == '[' || c == '{') && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) {
      char c1 = chars.charAt(offset);
      if (c1 != getRightChar((char)c)) return true;

      HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
      BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
      if (!braceMatcher.isLBraceToken(iterator, chars, fileType) &&
          !braceMatcher.isRBraceToken(iterator, chars, fileType)
          ) {
        return true;
      }

      int rparenOffset = BraceMatchingUtil.findRightmostRParen(iterator, iterator.getTokenType(), chars, fileType);
      if (rparenOffset >= 0){
        iterator = editor.getHighlighter().createIterator(rparenOffset);
        boolean matched = BraceMatchingUtil.matchBrace(chars, fileType, iterator, false, true);
        if (matched) return true;
      }

      editor.getDocument().deleteString(offset, offset + 1);
    }
    else if ((c == '"' || c == '\'' || c == '`') && CodeInsightSettings.getInstance().AUTOINSERT_PAIR_QUOTE){
      char c1 = chars.charAt(offset);
      if (c1 != c) return true;
      if (wasClosingQuote) return true;

      HighlighterIterator iterator = editor.getHighlighter().createIterator(offset);
      if (quoteHandler == null || !quoteHandler.isOpeningQuote(iterator,offset)) return true;

      editor.getDocument().deleteString(offset, offset + 1);
    }

    return true;
  }

  private static void deleteCustomFoldRegionIfNeeded(@NotNull Caret caret) {
    int caretOffset = caret.getOffset();
    Editor editor = caret.getEditor();
    FoldRegion foldRegion = editor.getFoldingModel().getCollapsedRegionAtOffset(caretOffset - 1);
    if (foldRegion instanceof CustomFoldRegion && foldRegion.getEndOffset() == caretOffset) {
      editor.getDocument().deleteString(foldRegion.getStartOffset(), foldRegion.getEndOffset());
    }
  }

  public static char getRightChar(final char c) {
    if (c == '(') return ')';
    if (c == '[') return ']';
    if (c == '{') return '}';
    assert false;
    return c;
  }

  private static boolean isOffsetInsideInjected(Editor injectedEditor, int injectedOffset) {
    if (injectedOffset == 0 || injectedOffset >= injectedEditor.getDocument().getTextLength()) {
      return false;
    }
    PsiFile injectedFile = ((EditorWindow)injectedEditor).getInjectedFile();
    InjectedLanguageManager ilm = InjectedLanguageManager.getInstance(injectedFile.getProject());
    TextRange rangeToEdit = new TextRange(injectedOffset - 1, injectedOffset+1);
    List<TextRange> editables = ilm.intersectWithAllEditableFragments(injectedFile, rangeToEdit);

    return editables.size() == 1 && editables.get(0).equals(rangeToEdit);
  }

  static @NotNull Language getLanguageAtCursorPosition(final PsiFile file, final Editor editor) {
    if (file instanceof PsiFileWithOneLanguage) {
      return file.getLanguage();
    }

    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    Language language = element != null ? PsiUtilCore.findLanguageFromElement(element) : Language.ANY;
    if (language != Language.ANY) {
      return language;
    }
    return file.getLanguage();
  }

  @Nullable
  public static LogicalPosition getBackspaceUnindentPosition(final PsiFile file, final Editor editor) {
    if (editor.getSelectionModel().hasSelection()) return null;

    final LogicalPosition caretPos = editor.getCaretModel().getLogicalPosition();
    if (caretPos.column == 0) {
      return null;
    }
    if (!isWhitespaceBeforeCaret(editor)) {
      return null;
    }

    // Determine indent size
    CommonCodeStyleSettings.IndentOptions fileIndentOptions = CodeStyle.getIndentOptions(file);
    int indent = fileIndentOptions.INDENT_SIZE;
    if (!fileIndentOptions.isOverrideLanguageOptions()) {
      Language language = getLanguageAtCursorPosition(file, editor);
      if (language != file.getLanguage()) {
        indent = CodeStyle.getSettings(file).getLanguageIndentOptions(language).INDENT_SIZE;
      }
    }
    // Decrease column down to indentation * n
    int column = indent > 0 ? (caretPos.column - 1) / indent * indent : 0;
    return new LogicalPosition(caretPos.line, column);
  }

  public static void deleteToTargetPosition(@NotNull Editor editor, @NotNull LogicalPosition pos) {
    LogicalPosition logicalPosition = editor.getCaretModel().getLogicalPosition();
    if (logicalPosition.line != pos.line) {
      LOGGER.error("Unexpected caret position: " + logicalPosition + ", target indent position: " + pos);
      return;
    }
    if (pos.column < logicalPosition.column) {
      int targetOffset = editor.logicalPositionToOffset(pos);
      int offset = editor.getCaretModel().getOffset();
      editor.getCaretModel().getCurrentCaret().setSelection(targetOffset, offset, false);
      EditorModificationUtilEx.deleteSelectedText(editor);
      editor.getCaretModel().moveToLogicalPosition(pos);
    }
    else if (pos.column > logicalPosition.column) {
      EditorModificationUtilEx.insertStringAtCaret(editor, StringUtil.repeatSymbol(' ', pos.column - logicalPosition.column));
    }
  }

  public static boolean isWhitespaceBeforeCaret(Editor editor) {
    final LogicalPosition caretPos = editor.getCaretModel().getLogicalPosition();
    final CharSequence charSeq = editor.getDocument().getCharsSequence();
    // smart backspace is activated only if all characters in the check range are whitespace characters
    for(int pos=0; pos<caretPos.column; pos++) {
      // use logicalPositionToOffset to make sure tabs are handled correctly
      final LogicalPosition checkPos = new LogicalPosition(caretPos.line, pos);
      final int offset = editor.logicalPositionToOffset(checkPos);
      if (offset < charSeq.length()) {
        final char c = charSeq.charAt(offset);
        if (c != '\t' && c != ' ' && c != '\n') {
          return false;
        }
      }
    }
    return true;
  }
}
