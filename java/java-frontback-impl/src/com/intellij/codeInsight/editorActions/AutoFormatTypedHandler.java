// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorModificationUtilEx;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtilBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import java.util.List;

public final class AutoFormatTypedHandler extends TypedHandlerDelegate {
  private static boolean myIsEnabledInTests;

  private static final char[] NO_SPACE_AFTER = {
    '+', '-', '*', '/', '%', '&', '^', '|', '<', '>', '!', '=', ' '
  };

  private static final List<IElementType> COMPLEX_ASSIGNMENTS = List.of(JavaTokenType.PLUSEQ, JavaTokenType.MINUSEQ,
                                                                        JavaTokenType.ASTERISKEQ, JavaTokenType.DIVEQ,
                                                                        JavaTokenType.PERCEQ,
                                                                        JavaTokenType.ANDEQ, JavaTokenType.XOREQ, JavaTokenType.OREQ,
                                                                        JavaTokenType.LTLTEQ, JavaTokenType.GTGTEQ);

  private static boolean isEnabled(Editor editor) {
    boolean isEnabled = myIsEnabledInTests && ApplicationManager.getApplication().isUnitTestMode()
                        || Registry.is("editor.reformat.on.typing");

    if (!isEnabled) {
      return false;
    }

    Project project = editor.getProject();
    Language language = null;
    if (project != null) {
      PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
      if (file != null) {
        language = file.getLanguage();
      }
    }

    return language == JavaLanguage.INSTANCE;
  }

  @TestOnly
  public static void setEnabledInTests(boolean value) {
    myIsEnabledInTests = value;
  }

  @Override
  public @NotNull Result beforeCharTyped(char c,
                                         @NotNull Project project,
                                         @NotNull Editor editor,
                                         @NotNull PsiFile file,
                                         @NotNull FileType fileType) {
    if (!isEnabled(editor)) {
      return Result.CONTINUE;
    }
    if (isInsertSpaceAtCaret(editor, c, project)) {
      EditorModificationUtilEx.insertStringAtCaret(editor, " ");
    }

    return Result.CONTINUE;
  }

  private static boolean isInsertSpaceAtCaret(@NotNull Editor editor, char charTyped, @NotNull Project project) {
    if (!isSpaceAroundAssignment(editor, project)) {
      return false;
    }

    int caretOffset = editor.getCaretModel().getOffset();
    CharSequence text = editor.getDocument().getImmutableCharSequence();

    HighlighterIterator lexerIterator = createLexerIterator(editor, caretOffset);
    if (lexerIterator == null || lexerIterator.getTokenType() == JavaTokenType.STRING_LITERAL) {
      return false;
    }

    boolean insertBeforeEq = charTyped == '=' && isInsertSpaceBeforeEq(caretOffset, text);
    boolean insertAfterEq = caretOffset > 0 && caretOffset - 1 < text.length() && text.charAt(caretOffset - 1) == '='
                            && isAssignmentOperator(lexerIterator) && isInsertSpaceAfterEq(charTyped);

    return (insertBeforeEq || insertAfterEq);
  }

  private static boolean isAssignmentOperator(HighlighterIterator iterator) {
    IElementType type = iterator.getTokenType();
    if (type == TokenType.WHITE_SPACE) {
      iterator.retreat();
      type = iterator.getTokenType();
    }

    if (COMPLEX_ASSIGNMENTS.contains(type)) {
      return true;
    }

    if (type == JavaTokenType.EQ) {
      iterator.retreat();
      type = iterator.getTokenType();
      if (type == JavaTokenType.GT) {
        iterator.retreat();
        type = iterator.getTokenType();
        if (type == JavaTokenType.GT) {
          return true;
        }
      }

      else if (type == TokenType.WHITE_SPACE || type == JavaTokenType.IDENTIFIER) {
        return true;
      }
    }

    return false;
  }

  private static boolean isInsertSpaceAfterEq(char charTyped) {
    return charTyped != '=' && charTyped != ' ';
  }

  private static HighlighterIterator createLexerIterator(Editor editor, int offset) {
    if (editor.getDocument().getTextLength() == 0) return null;
    return editor.getHighlighter().createIterator(offset);
  }

  private static boolean isInsertSpaceBeforeEq(int caretOffset, CharSequence text) {
    if (caretOffset == 0) return false;
    char charBefore = text.charAt(caretOffset - 1);

    for (char c : NO_SPACE_AFTER) {
      if (c == charBefore) {
        return false;
      }
    }

    return true;
  }

  private static boolean isSpaceAroundAssignment(Editor editor, Project project) {
    PsiFile file = project == null ? null : PsiUtilBase.getPsiFileInEditor(editor, project);
    if (file != null) {
      Language language = file.getLanguage();
      CodeStyleSettings settings = CodeStyle.getSettings(editor);
      CommonCodeStyleSettings common = settings.getCommonSettings(language);
      return common.SPACE_AROUND_ASSIGNMENT_OPERATORS;
    }
    return false;
  }
}
