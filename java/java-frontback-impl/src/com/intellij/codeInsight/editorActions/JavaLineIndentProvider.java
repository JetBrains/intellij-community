// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions;

import com.intellij.application.options.CodeStyle;
import com.intellij.formatting.Indent;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.codeStyle.SemanticEditorPosition;
import com.intellij.psi.impl.source.codeStyle.lineIndent.JavaLikeLangLineIndentProvider;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.formatting.Indent.Type.CONTINUATION;
import static com.intellij.psi.impl.source.codeStyle.lineIndent.JavaLikeLangLineIndentProvider.JavaLikeElement.*;

public final class JavaLineIndentProvider extends JavaLikeLangLineIndentProvider {
  private static final Map<IElementType, SemanticEditorPosition.SyntaxElement> SYNTAX_MAP = Map.ofEntries(
    Map.entry(TokenType.WHITE_SPACE, Whitespace),
    Map.entry(JavaTokenType.SEMICOLON, Semicolon),
    Map.entry(JavaTokenType.LBRACE, BlockOpeningBrace),
    Map.entry(JavaTokenType.RBRACE, BlockClosingBrace),
    Map.entry(JavaTokenType.LBRACKET, ArrayOpeningBracket),
    Map.entry(JavaTokenType.RBRACKET, ArrayClosingBracket),
    Map.entry(JavaTokenType.RPARENTH, RightParenthesis),
    Map.entry(JavaTokenType.LPARENTH, LeftParenthesis),
    Map.entry(JavaTokenType.COLON, Colon),
    Map.entry(JavaTokenType.CASE_KEYWORD, SwitchCase),
    Map.entry(JavaTokenType.DEFAULT_KEYWORD, SwitchDefault),
    Map.entry(JavaTokenType.IF_KEYWORD, IfKeyword),
    Map.entry(JavaTokenType.WHILE_KEYWORD, IfKeyword),
    Map.entry(JavaTokenType.ELSE_KEYWORD, ElseKeyword),
    Map.entry(JavaTokenType.FOR_KEYWORD, ForKeyword),
    Map.entry(JavaTokenType.DO_KEYWORD, DoKeyword),
    Map.entry(JavaTokenType.C_STYLE_COMMENT, BlockComment),
    Map.entry(JavaDocTokenType.DOC_COMMENT_START, DocBlockStart),
    Map.entry(JavaDocTokenType.DOC_COMMENT_END, DocBlockEnd),
    Map.entry(JavaTokenType.COMMA, Comma),
    Map.entry(JavaTokenType.END_OF_LINE_COMMENT, LineComment),
    Map.entry(JavaTokenType.TRY_KEYWORD, TryKeyword)
  );

  @Override
  protected @Nullable SemanticEditorPosition.SyntaxElement mapType(@NotNull IElementType tokenType) {
    return SYNTAX_MAP.get(tokenType);
  }

  @Override
  public boolean isSuitableForLanguage(@NotNull Language language) {
    return language.isKindOf(JavaLanguage.INSTANCE);
  }

  @Override
  protected @Nullable Indent getIndentInBlock(@NotNull Project project,
                                              @Nullable Language language,
                                              @NotNull SemanticEditorPosition blockStartPosition) {
    SemanticEditorPosition beforeStart = blockStartPosition.before().beforeOptional(Whitespace);
    if (beforeStart.isAt(JavaTokenType.EQ) ||
        beforeStart.isAt(JavaTokenType.RBRACKET) ||
        beforeStart.isAt(JavaTokenType.LPARENTH)
    ) {
      // For arrays like int x = {<caret>0, 1, 2}
      return getDefaultIndentFromType(CONTINUATION);
    }
    else if (beforeStart.isAt(JavaTokenType.IDENTIFIER)) {
      moveBeforeExtendsImplementsAndIdentifier(beforeStart);
      if (beforeStart.isAt(JavaTokenType.CLASS_KEYWORD) && doNotIndentClassMembers(beforeStart)) {
        return Indent.getNoneIndent();
      }
    }
    return super.getIndentInBlock(project, language, blockStartPosition);
  }

  private static void moveBeforeExtendsImplementsAndIdentifier(@NotNull SemanticEditorPosition position) {
    while (position.isAt(JavaTokenType.IDENTIFIER) || position.isAtAnyOf(Whitespace, Comma) ||
           position.isAt(JavaTokenType.EXTENDS_KEYWORD) || position.isAt(JavaTokenType.IMPLEMENTS_KEYWORD)) {
      position.moveBefore();
    }
  }

  private static boolean doNotIndentClassMembers(@NotNull SemanticEditorPosition position) {
    Editor editor = position.getEditor();
    CommonCodeStyleSettings javaSettings = CodeStyle.getSettings(editor).getCommonSettings(JavaLanguage.INSTANCE);
    return javaSettings.DO_NOT_INDENT_TOP_LEVEL_CLASS_MEMBERS;
  }

  @Override
  protected boolean isInsideForLikeConstruction(SemanticEditorPosition position) {
    return position.isAfterOnSameLine(ForKeyword, TryKeyword);
  }

  @Override
  protected boolean isInArray(@NotNull Editor editor, int offset) {
    SemanticEditorPosition position = getPosition(editor, offset);
    position.moveBefore();
    if (position.isAt(JavaTokenType.LBRACE)) {
      if (position.before().beforeOptional(Whitespace).isAt(JavaTokenType.RBRACKET)) return true;
    }
    return super.isInArray(editor, offset);
  }

  @Override
  protected boolean isIndentProvider(@NotNull SemanticEditorPosition position, boolean ignoreLabels) {
    return !(position.afterOptionalMix(Whitespace, BlockComment).after().isAt(Colon)
             && position.isAt(JavaTokenType.IDENTIFIER));
  }
}
