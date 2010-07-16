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

package com.intellij.codeInsight.editorActions;

import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class BackspaceHandler extends EditorWriteActionHandler {
  private final EditorActionHandler myOriginalHandler;

  public BackspaceHandler(EditorActionHandler originalHandler) {
    myOriginalHandler = originalHandler;
  }

  public void executeWriteAction(Editor editor, DataContext dataContext) {
    if (!handleBackspace(editor, dataContext)){
      myOriginalHandler.execute(editor, dataContext);
    }
  }

  private boolean handleBackspace(Editor editor, DataContext dataContext){
    Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) return false;

    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());

    if (file == null) return false;

    if (editor.getSelectionModel().hasSelection()) return false;

    int offset = editor.getCaretModel().getOffset() - 1;
    if (offset < 0) return false;
    CharSequence chars = editor.getDocument().getCharsSequence();
    char c = chars.charAt(offset);

    final Editor injectedEditor = TypedHandler.injectedEditorIfCharTypedIsSignificant(c, editor, file);
    if (injectedEditor != editor) {
      int injectedOffset = injectedEditor.getCaretModel().getOffset();
      if (isOffsetInsideInjected(injectedEditor, injectedOffset)) {
        file = PsiDocumentManager.getInstance(project).getPsiFile(injectedEditor.getDocument());
        editor = injectedEditor;
        offset = injectedOffset - 1;
        chars = editor.getDocument().getCharsSequence();
      }
    }

    final BackspaceHandlerDelegate[] delegates = Extensions.getExtensions(BackspaceHandlerDelegate.EP_NAME);
    for(BackspaceHandlerDelegate delegate: delegates) {
      delegate.beforeCharDeleted(c, file, editor);
    }

    FileType fileType = file.getFileType();
    final QuoteHandler quoteHandler = TypedHandler.getQuoteHandler(file);

    HighlighterIterator hiterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
    boolean wasClosingQuote = quoteHandler != null && quoteHandler.isClosingQuote(hiterator, offset);

    myOriginalHandler.execute(editor, dataContext);

    for(BackspaceHandlerDelegate delegate: delegates) {
      if (delegate.charDeleted(c, file, editor)) {
        return true;
      }
    }

    if (offset >= editor.getDocument().getTextLength()) return true;

    chars = editor.getDocument().getCharsSequence();
    if (c == '(' || c == '[' || c == '{'){
      char c1 = chars.charAt(offset);
      if (c == '(' && c1 != ')') return true;
      if (c == '[' && c1 != ']') return true;
      if (c == '{' && c1 != '}') return true;

      HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
      BraceMatcher braceMatcher = BraceMatchingUtil.getBraceMatcher(fileType, iterator);
      if (!braceMatcher.isLBraceToken(iterator, chars, fileType) &&
          !braceMatcher.isRBraceToken(iterator, chars, fileType)
          ) {
        return true;
      }

      int rparenOffset = BraceMatchingUtil.findRightmostRParen(iterator, iterator.getTokenType() ,chars,fileType);
      if (rparenOffset >= 0){
        iterator = ((EditorEx)editor).getHighlighter().createIterator(rparenOffset);
        boolean matched = BraceMatchingUtil.matchBrace(chars, fileType, iterator, false);
        if (matched) return true;
      }

      editor.getDocument().deleteString(offset, offset + 1);
    }
    else if (c == '"' || c == '\''){
      char c1 = chars.charAt(offset);
      if (c1 != c) return true;
      if (wasClosingQuote) return true;

      HighlighterIterator iterator = ((EditorEx)editor).getHighlighter().createIterator(offset);
      if (quoteHandler == null || !quoteHandler.isOpeningQuote(iterator,offset)) return true;

      editor.getDocument().deleteString(offset, offset + 1);
    }

    return true;
  }

  private static boolean isOffsetInsideInjected(Editor injectedEditor, int injectedOffset) {
    if (injectedOffset == 0 || injectedOffset >= injectedEditor.getDocument().getTextLength()) {
      return false;
    }
    PsiFile injectedFile = ((EditorWindow)injectedEditor).getInjectedFile();
    InjectedLanguageManager ilm = InjectedLanguageManager.getInstance(injectedFile.getProject());
    TextRange rangeToEdit = new TextRange(injectedOffset - 1, injectedOffset);
    List<TextRange> editables = ilm.intersectWithAllEditableFragments(injectedFile, rangeToEdit);

    return editables.size() == 1 && editables.get(0).equals(rangeToEdit);
  }

  @Nullable
  public static LogicalPosition getBackspaceUnindentPosition(final PsiFile file, final Editor editor) {
    if (editor.getSelectionModel().hasSelection() || editor.getSelectionModel().hasBlockSelection()) return null;

    final LogicalPosition caretPos = editor.getCaretModel().getLogicalPosition();
    if (caretPos.line == 0 || caretPos.column == 0) {
      return null;
    }
    final int startCheckRange = editor.getDocument().getLineStartOffset(caretPos.line);
    final int endCheckRange = startCheckRange + caretPos.column;

    final CharSequence charSeq = editor.getDocument().getCharsSequence();
    // smart backspace is activated only if all characters in the check range are whitespace characters
    for(int pos=0; pos<caretPos.column; pos++) {
      // use logicalPositionToOffset to make sure tabs are handled correctly
      final LogicalPosition checkPos = new LogicalPosition(caretPos.line, pos);
      final int offset = editor.logicalPositionToOffset(checkPos);
      final char c = charSeq.charAt(offset);
      if (c != '\t' && c != ' ' && c != '\n') {
        return null;
      }
    }

    // Decrease column down to indentation * n
    final int indent = CodeStyleSettingsManager.getSettings(file.getProject()).getIndentSize(file.getFileType());
    int column = ((caretPos.column - 1) / indent) * indent;
    if (column < 0) {
      column = 0;
    }
    return new LogicalPosition(caretPos.line, column);
  }
}
