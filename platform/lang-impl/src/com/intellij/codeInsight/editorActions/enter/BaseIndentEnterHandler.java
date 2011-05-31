/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInsight.editorActions.enter;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actions.EditorActionUtil;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author oleg
 * @date 11/17/10
 */
public class BaseIndentEnterHandler extends EnterHandlerDelegateAdapter {
  private final Language myLanguage;
  private final TokenSet myIndentTokens;
  private final IElementType myLineCommentType;
  private final String myLineCommentPrefix;
  private final TokenSet myWhitespaceTokens;

  public BaseIndentEnterHandler(final Language language,
                                final TokenSet indentTokens,
                                final IElementType lineCommentType,
                                final String lineCommentPrefix,
                                final TokenSet whitespaceTokens) {
    myLanguage = language;
    myIndentTokens = indentTokens;
    myLineCommentType = lineCommentType;
    myLineCommentPrefix = lineCommentPrefix;
    myWhitespaceTokens = whitespaceTokens;
  }

  public Result preprocessEnter(@NotNull final PsiFile file,
                                @NotNull final Editor editor,
                                @NotNull final Ref<Integer> caretOffset,
                                @NotNull final Ref<Integer> caretAdvance,
                                @NotNull final DataContext dataContext,
                                final EditorActionHandler originalHandler) {
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return Result.Continue;
    }

    if (!file.getViewProvider().getLanguages().contains(myLanguage)) {
      return Result.Continue;
    }

    if (editor.isViewer()) {
      return Result.Continue;
    }

    final Document document = editor.getDocument();
    if (!document.isWritable()) {
      return Result.Continue;
    }

    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

    int caret = editor.getCaretModel().getOffset();
    if (caret == 0) {
      return Result.Continue;
    }

    final int lineNumber = document.getLineNumber(caret);
    final int lineStartOffset = document.getLineStartOffset(lineNumber);
    final EditorHighlighter highlighter = ((EditorEx)editor).getHighlighter();
    final HighlighterIterator iterator = highlighter.createIterator(caret - 1);
    final IElementType type = getNonWhitespaceElementType(iterator, lineStartOffset);

    final CharSequence editorCharSequence = editor.getDocument().getCharsSequence();
    final CharSequence lineIndent =
      editorCharSequence.subSequence(lineStartOffset, EditorActionUtil.findFirstNonSpaceOffsetOnTheLine(document, lineNumber));

    // Enter in line comment
    if (type == myLineCommentType) {
      final String restString = editorCharSequence.subSequence(caret, document.getLineEndOffset(lineNumber)).toString();
      if (!StringUtil.isEmptyOrSpaces(restString)) {
        EditorModificationUtil.insertStringAtCaret(editor, "\n" + lineIndent + myLineCommentPrefix);
        editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(lineNumber + 1, 1));
        return Result.Stop;
      }
    }

    if (myIndentTokens.contains(type)) {
      final String singleIndent = getSingleIndent(file);
      EditorModificationUtil.insertStringAtCaret(editor, "\n" + lineIndent + singleIndent);
      return Result.Stop;
    }

    EditorModificationUtil.insertStringAtCaret(editor, "\n" + lineIndent);
    editor.getCaretModel().moveToLogicalPosition(new LogicalPosition(lineNumber + 1, lineIndent.length()));
    return Result.Stop;
  }

  protected String getSingleIndent(final PsiFile file) {
    return StringUtil.repeatSymbol(' ', CodeStyleSettingsManager.getInstance().getCurrentSettings().getIndentSize(myLanguage.getAssociatedFileType()));
  }

  @Nullable
  private IElementType getNonWhitespaceElementType(final HighlighterIterator iterator, final int lineStartOffset) {
    while (!iterator.atEnd() && iterator.getStart() >= lineStartOffset) {
      final IElementType tokenType = iterator.getTokenType();
      if (!myWhitespaceTokens.contains(tokenType)) {
        return tokenType;
      }
      iterator.retreat();
    }
    return null;
  }
}

