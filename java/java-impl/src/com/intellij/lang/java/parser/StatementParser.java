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
package com.intellij.lang.java.parser;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.lang.PsiBuilderUtil.*;
import static com.intellij.lang.java.parser.JavaParserUtil.*;


public class StatementParser {
  private static final boolean DEEP_PARSE_BLOCKS_IN_STATEMENTS = true;  // todo: reset after testing done

  private enum BraceMode {
    TILL_FIRST, TILL_LAST
  }

  private static final TokenSet TRY_CLOSERS_SET = TokenSet.create(JavaTokenType.CATCH_KEYWORD, JavaTokenType.FINALLY_KEYWORD);

  private StatementParser() { }

  @Nullable
  public static PsiBuilder.Marker parseCodeBlock(final PsiBuilder builder) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) return null;
    else if (DEEP_PARSE_BLOCKS_IN_STATEMENTS) return parseCodeBlockDeep(builder, false);

    final PsiBuilder.Marker codeBlock = builder.mark();
    builder.advanceLexer();

    int braceCount = 1;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == null) {
        break;
      }
      if (tokenType == JavaTokenType.LBRACE) {
        braceCount++;
      }
      else if (tokenType == JavaTokenType.RBRACE) {
        braceCount--;
      }
      builder.advanceLexer();

      if (braceCount == 0) {
        break;
      }
      else if (braceCount == 1 && (tokenType == JavaTokenType.SEMICOLON || tokenType == JavaTokenType.RBRACE)) {
        final PsiBuilder.Marker position = builder.mark();
        final List<IElementType> list = new SmartList<IElementType>();
        while (true) {
          final IElementType type = builder.getTokenType();
          if (ElementType.PRIMITIVE_TYPE_BIT_SET.contains(type) || ElementType.MODIFIER_BIT_SET.contains(type) ||
              type == JavaTokenType.IDENTIFIER || type == JavaTokenType.LT || type == JavaTokenType.GT ||
              type == JavaTokenType.GTGT || type == JavaTokenType.GTGTGT || type == JavaTokenType.COMMA ||
              type == JavaTokenType.DOT || type == JavaTokenType.EXTENDS_KEYWORD || type == JavaTokenType.IMPLEMENTS_KEYWORD) {
            list.add(type);
            builder.advanceLexer();
          } else {
            break;
          }
        }
        if (builder.getTokenType() == JavaTokenType.LPARENTH && list.size() >= 2) {
          final IElementType last = list.get(list.size() - 1);
          final IElementType prevLast = list.get(list.size() - 2);
          if (last == JavaTokenType.IDENTIFIER &&
              (prevLast == JavaTokenType.IDENTIFIER || ElementType.PRIMITIVE_TYPE_BIT_SET.contains(prevLast))) {
            position.rollbackTo();
            break;
          }
        }
        position.drop();
      }
    }

    codeBlock.collapse(JavaElementType.CODE_BLOCK);
    return codeBlock;
  }

  @Nullable
  public static PsiBuilder.Marker parseCodeBlockDeep(final PsiBuilder builder, final boolean parseUntilEof) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) return null;

    final PsiBuilder.Marker codeBlock = builder.mark();
    builder.advanceLexer();

    parseStatements(builder, (parseUntilEof ? BraceMode.TILL_LAST : BraceMode.TILL_FIRST));

    expectOrError(builder, JavaTokenType.RBRACE, JavaErrorMessages.message("expected.rbrace"));

    codeBlock.done(JavaElementType.CODE_BLOCK);
    return codeBlock;
  }

  private static void parseStatements(final PsiBuilder builder, final BraceMode braceMode) {
    while (builder.getTokenType() != null) {
      final PsiBuilder.Marker statement = parseStatement(builder);
      if (statement != null) continue;

      final IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.RBRACE) {
        if (braceMode == BraceMode.TILL_FIRST) {
          return;
        }
        else if (braceMode == BraceMode.TILL_LAST) {
          if (nextTokenType(builder) == null) {
            return;
          }
        }
      }

      final PsiBuilder.Marker error = builder.mark();
      builder.advanceLexer();
      if (tokenType == JavaTokenType.ELSE_KEYWORD) {
        error.error(JavaErrorMessages.message("else.without.if"));
      }
      else if (tokenType == JavaTokenType.CATCH_KEYWORD) {
        error.error(JavaErrorMessages.message("catch.without.try"));
      }
      else if (tokenType == JavaTokenType.FINALLY_KEYWORD) {
        error.error(JavaErrorMessages.message("finally.without.try"));
      }
      else {
        error.error(JavaErrorMessages.message("unexpected.token"));
      }
    }
  }

  @Nullable
  private static PsiBuilder.Marker parseStatement(final PsiBuilder builder) {
    final IElementType tokenType = builder.getTokenType();

    // todo: custom parsers (?)

    if (tokenType == JavaTokenType.IF_KEYWORD) {
      return parseIfStatement(builder);
    }
    else if (tokenType == JavaTokenType.WHILE_KEYWORD) {
      return parseWhileStatement(builder);
    }
    else if (tokenType == JavaTokenType.FOR_KEYWORD) {
      return parseForStatement(builder);
    }
    else if (tokenType == JavaTokenType.DO_KEYWORD) {
      return parseDoWhileStatement(builder);
    }
    else if (tokenType == JavaTokenType.SWITCH_KEYWORD) {
      return parseSwitchStatement(builder);
    }
    else if (tokenType == JavaTokenType.CASE_KEYWORD || tokenType == JavaTokenType.DEFAULT_KEYWORD) {
      return parseSwitchLabelStatement(builder);
    }
    else if (tokenType == JavaTokenType.BREAK_KEYWORD) {
      return parseBreakStatement(builder);
    }
    else if (tokenType == JavaTokenType.CONTINUE_KEYWORD) {
      return parseContinueStatement(builder);
    }
    else if (tokenType == JavaTokenType.RETURN_KEYWORD) {
      return parseReturnStatement(builder);
    }
    else if (tokenType == JavaTokenType.THROW_KEYWORD) {
      return parseThrowStatement(builder);
    }
    else if (tokenType == JavaTokenType.SYNCHRONIZED_KEYWORD) {
      return parseSynchronizedStatement(builder);
    }
    else if (tokenType == JavaTokenType.TRY_KEYWORD) {
      return parseTryStatement(builder);
    }
    else if (tokenType == JavaTokenType.ASSERT_KEYWORD) {
      return parseAssertStatement(builder);
    }
    else if (tokenType == JavaTokenType.LBRACE) {
      return parseBlockStatement(builder);
    }
    else if (tokenType instanceof ILazyParseableElementType) {
      final PsiBuilder.Marker marker = builder.mark();
      marker.drop();
      builder.advanceLexer();
      return marker;
    }
    else if (tokenType == JavaTokenType.SEMICOLON) {
      final PsiBuilder.Marker empty = builder.mark();
      builder.advanceLexer();
      empty.done(JavaElementType.EMPTY_STATEMENT);
      return empty;
    }
    else if (tokenType == JavaTokenType.IDENTIFIER || tokenType == JavaTokenType.AT) {
      final PsiBuilder.Marker refPos = builder.mark();
      DeclarationParser.parseAnnotations(builder);
      skipQualifiedName(builder);
      final IElementType suspectedLT = builder.getTokenType();
      refPos.rollbackTo();
      if (suspectedLT == JavaTokenType.LT) {
        final PsiBuilder.Marker declStatement = builder.mark();
        final PsiBuilder.Marker decl = DeclarationParser.parse(builder, DeclarationParser.Context.CODE_BLOCK);
        if (decl == null) {
          error(builder, JavaErrorMessages.message("expected.identifier"));
        }
        declStatement.done(JavaElementType.DECLARATION_STATEMENT);
        return declStatement;
      }
    }

    final PsiBuilder.Marker pos = builder.mark();
    final PsiBuilder.Marker expr = ExpressionParser.parse(builder);

    if (expr != null) {
      int count = 1;
      final PsiBuilder.Marker list = expr.precede();
      final PsiBuilder.Marker statement = list.precede();
      while (builder.getTokenType() == JavaTokenType.COMMA) {
        final PsiBuilder.Marker commaPos = builder.mark();
        builder.advanceLexer();
        final PsiBuilder.Marker expr1 = ExpressionParser.parse(builder);
        if (expr1 == null) {
          commaPos.rollbackTo();
          break;
        }
        commaPos.drop();
        count++;
      }
      if (count > 1) {
        pos.drop();
        list.done(JavaElementType.EXPRESSION_LIST);
        semicolon(builder);
        statement.done(JavaElementType.EXPRESSION_LIST_STATEMENT);
        return statement;
      }
      if (exprType(expr) != JavaElementType.REFERENCE_EXPRESSION) {
        drop(list, pos);
        semicolon(builder);
        statement.done(JavaElementType.EXPRESSION_STATEMENT);
        return statement;
      }
      pos.rollbackTo();
    }
    else {
      pos.drop();
    }

    final PsiBuilder.Marker decl = DeclarationParser.parse(builder, DeclarationParser.Context.CODE_BLOCK);
    if (decl != null) {
      final PsiBuilder.Marker statement = decl.precede();
      statement.done(JavaElementType.DECLARATION_STATEMENT);
      return statement;
    }

    if (lookAhead(builder, JavaTokenType.IDENTIFIER, JavaTokenType.COLON)) {
      final PsiBuilder.Marker statement = builder.mark();
      advance(builder, 2);
      parseStatement(builder);
      statement.done(JavaElementType.LABELED_STATEMENT);
      return statement;
    }

    if (expr != null) {
      final PsiBuilder.Marker statement = builder.mark();
      ExpressionParser.parse(builder);
      semicolon(builder);
      statement.done(JavaElementType.EXPRESSION_STATEMENT);
      return statement;
    }

    return null;
  }

  private static void skipQualifiedName(final PsiBuilder builder) {
    if (!expect(builder, JavaTokenType.IDENTIFIER)) return;
    while (lookAhead(builder, JavaTokenType.DOT, JavaTokenType.IDENTIFIER)) {
      advance(builder, 2);
    }
  }

  @NotNull
  private static PsiBuilder.Marker parseIfStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!parseExpressionInParenth(builder)) {
      statement.done(JavaElementType.IF_STATEMENT);
      return statement;
    }

    final PsiBuilder.Marker thenStatement = parseStatement(builder);
    if (thenStatement == null) {
      error(builder, JavaErrorMessages.message("expected.statement"));
      statement.done(JavaElementType.IF_STATEMENT);
      return statement;
    }

    if (!expect(builder, JavaTokenType.ELSE_KEYWORD)) {
      statement.done(JavaElementType.IF_STATEMENT);
      return statement;
    }

    final PsiBuilder.Marker elseStatement = parseStatement(builder);
    if (elseStatement == null) {
      error(builder, JavaErrorMessages.message("expected.statement"));
    }

    statement.done(JavaElementType.IF_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseWhileStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!parseExpressionInParenth(builder)) {
      statement.done(JavaElementType.WHILE_STATEMENT);
      return statement;
    }

    final PsiBuilder.Marker bodyStatement = parseStatement(builder);
    if (bodyStatement == null) {
      error(builder, JavaErrorMessages.message("expected.statement"));
    }

    statement.done(JavaElementType.WHILE_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseForStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaErrorMessages.message("expected.lparen"));
      statement.done(JavaElementType.FOR_STATEMENT);
      return statement;
    }

    final PsiBuilder.Marker afterParenth = builder.mark();
    final PsiBuilder.Marker param = DeclarationParser.parseParameter(builder, false);
    if (param == null || builder.getTokenType() != JavaTokenType.COLON) {
      afterParenth.rollbackTo();
      return parseForLoopFromInitialization(builder, statement);
    }
    else {
      afterParenth.drop();
      return parseForEachFromColon(builder, statement);
    }
  }

  @NotNull
  private static PsiBuilder.Marker parseForLoopFromInitialization(final PsiBuilder builder, final PsiBuilder.Marker statement) {
    final PsiBuilder.Marker init = parseStatement(builder);
    if (init == null){
      error(builder, JavaErrorMessages.message("expected.statement"));
      if (!expect(builder, JavaTokenType.RPARENTH)) {
        statement.done(JavaElementType.FOR_STATEMENT);
        return statement;
      }
    }
    else {
      ExpressionParser.parse(builder);
      if (!expect(builder, JavaTokenType.SEMICOLON)) {
        error(builder, JavaErrorMessages.message("expected.semicolon"));
        if (!expect(builder, JavaTokenType.RPARENTH)) {
          statement.done(JavaElementType.FOR_STATEMENT);
          return statement;
        }
      }
      else {
        parseExpressionOrExpressionList(builder);
        if (!expect(builder, JavaTokenType.RPARENTH)) {
          error(builder, JavaErrorMessages.message("expected.rparen"));
          statement.done(JavaElementType.FOR_STATEMENT);
          return statement;
        }
      }
    }

    final PsiBuilder.Marker bodyStatement = parseStatement(builder);
    if (bodyStatement == null) {
      error(builder, JavaErrorMessages.message("expected.statement"));
    }

    statement.done(JavaElementType.FOR_STATEMENT);
    return statement;
  }

  private static void parseExpressionOrExpressionList(final PsiBuilder builder) {
    final PsiBuilder.Marker expr = ExpressionParser.parse(builder);
    if (expr == null) return;

    final PsiBuilder.Marker expressionStatement;
    if (builder.getTokenType() != JavaTokenType.COMMA) {
      expressionStatement = expr.precede();
      expressionStatement.done(JavaElementType.EXPRESSION_STATEMENT);
    }
    else {
      final PsiBuilder.Marker expressionList = expr.precede();
      expressionStatement = expressionList.precede();

      do {
        builder.advanceLexer();
        final PsiBuilder.Marker nextExpression = ExpressionParser.parse(builder);
        if (nextExpression == null) {
          error(builder, JavaErrorMessages.message("expected.expression"));
        }
      }
      while (builder.getTokenType() == JavaTokenType.COMMA);

      expressionList.done(JavaElementType.EXPRESSION_LIST);
      expressionStatement.done(JavaElementType.EXPRESSION_LIST_STATEMENT);
    }
  }

  @NotNull
  private static PsiBuilder.Marker parseForEachFromColon(PsiBuilder builder, PsiBuilder.Marker statement) {
    builder.advanceLexer();

    final PsiBuilder.Marker expr = ExpressionParser.parse(builder);
    if (expr == null) {
      error(builder, JavaErrorMessages.message("expected.expression"));
    }

    if (expect(builder, JavaTokenType.RPARENTH)) {
      final PsiBuilder.Marker bodyStatement = parseStatement(builder);
      if (bodyStatement == null) {
        error(builder, JavaErrorMessages.message("expected.statement"));
      }
    }
    else {
      error(builder, JavaErrorMessages.message("expected.rparen"));
    }

    statement.done(JavaElementType.FOREACH_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseDoWhileStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    final PsiBuilder.Marker body = parseStatement(builder);
    if (body == null) {
      error(builder, JavaErrorMessages.message("expected.statement"));
      statement.done(JavaElementType.DO_WHILE_STATEMENT);
      return statement;
    }

    if (!expect(builder, JavaTokenType.WHILE_KEYWORD)) {
      error(builder, JavaErrorMessages.message("expected.while"));
      statement.done(JavaElementType.DO_WHILE_STATEMENT);
      return statement;
    }

    if (parseExpressionInParenth(builder)) {
      semicolon(builder);
    }

    statement.done(JavaElementType.DO_WHILE_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseSwitchStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!parseExpressionInParenth(builder)) {
      statement.done(JavaElementType.SWITCH_STATEMENT);
      return statement;
    }

    final PsiBuilder.Marker body = parseCodeBlock(builder);
    if (body == null) {
      error(builder, JavaErrorMessages.message("expected.lbrace"));
    }

    statement.done(JavaElementType.SWITCH_STATEMENT);
    return statement;
  }

  @Nullable
  private static PsiBuilder.Marker parseSwitchLabelStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    final boolean isCase = builder.getTokenType() == JavaTokenType.CASE_KEYWORD;
    builder.advanceLexer();

    if (isCase) {
      final PsiBuilder.Marker expr = ExpressionParser.parse(builder);
      if (expr == null) {
        statement.rollbackTo();
        return null;
      }
    }

    expectOrError(builder, JavaTokenType.COLON, JavaErrorMessages.message("expected.colon"));

    statement.done(JavaElementType.SWITCH_LABEL_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseBreakStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    expect(builder, JavaTokenType.IDENTIFIER);
    semicolon(builder);
    statement.done(JavaElementType.BREAK_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseContinueStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    expect(builder, JavaTokenType.IDENTIFIER);
    semicolon(builder);
    statement.done(JavaElementType.CONTINUE_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseReturnStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    ExpressionParser.parse(builder);

    semicolon(builder);
    statement.done(JavaElementType.RETURN_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseThrowStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    final PsiBuilder.Marker expr = ExpressionParser.parse(builder);
    if (expr == null) {
      error(builder, JavaErrorMessages.message("expected.expression"));
      statement.done(JavaElementType.THROW_STATEMENT);
      return statement;
    }

    semicolon(builder);
    statement.done(JavaElementType.THROW_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseSynchronizedStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!parseExpressionInParenth(builder)) {
      statement.done(JavaElementType.SYNCHRONIZED_STATEMENT);
      return statement;
    }

    final PsiBuilder.Marker body = parseCodeBlock(builder);
    if (body == null) {
      error(builder, JavaErrorMessages.message("expected.lbrace"));
    }

    statement.done(JavaElementType.SYNCHRONIZED_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseTryStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    final PsiBuilder.Marker tryBlock = parseCodeBlock(builder);
    if (tryBlock == null) {
      error(builder, JavaErrorMessages.message("expected.lbrace"));
      statement.done(JavaElementType.TRY_STATEMENT);
      return statement;
    }

    if (!TRY_CLOSERS_SET.contains(builder.getTokenType())) {
      error(builder, JavaErrorMessages.message("expected.catch.or.finally"));
      statement.done(JavaElementType.TRY_STATEMENT);
      return statement;
    }

    while (builder.getTokenType() == JavaTokenType.CATCH_KEYWORD) {
      if (!parseCatchBlock(builder)) break;
    }

    if (expect(builder, JavaTokenType.FINALLY_KEYWORD)) {
      final PsiBuilder.Marker finallyBlock = parseCodeBlock(builder);
      if (finallyBlock == null) {
        error(builder, JavaErrorMessages.message("expected.lbrace"));
      }
    }

    statement.done(JavaElementType.TRY_STATEMENT);
    return statement;
  }

  private static boolean parseCatchBlock(final PsiBuilder builder) {
    final PsiBuilder.Marker section = builder.mark();
    builder.advanceLexer();

    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaErrorMessages.message("expected.lparen"));
      section.done(JavaElementType.CATCH_SECTION);
      return false;
    }

    final PsiBuilder.Marker param = DeclarationParser.parseParameter(builder, false);
    if (param == null) {
      error(builder, JavaErrorMessages.message("expected.parameter"));
    }

    if (!expect(builder, JavaTokenType.RPARENTH)) {
      error(builder, JavaErrorMessages.message("expected.rparen"));
      section.done(JavaElementType.CATCH_SECTION);
      return false;
    }

    final PsiBuilder.Marker body = parseCodeBlock(builder);
    if (body == null) {
      error(builder, JavaErrorMessages.message("expected.lbrace"));
      section.done(JavaElementType.CATCH_SECTION);
      return false;
    }

    section.done(JavaElementType.CATCH_SECTION);
    return true;
  }

  @NotNull
  private static PsiBuilder.Marker parseAssertStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    final PsiBuilder.Marker expr = ExpressionParser.parse(builder);
    if (expr == null) {
      error(builder, JavaErrorMessages.message("expected.boolean.expression"));
      statement.done(JavaElementType.ASSERT_STATEMENT);
      return statement;
    }

    if (expect(builder, JavaTokenType.COLON)) {
      final PsiBuilder.Marker expr2 = ExpressionParser.parse(builder);
      if (expr2 == null) {
        error(builder, JavaErrorMessages.message("expected.expression"));
        statement.done(JavaElementType.ASSERT_STATEMENT);
        return statement;
      }
    }

    semicolon(builder);
    statement.done(JavaElementType.ASSERT_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseBlockStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    parseCodeBlock(builder);
    statement.done(JavaElementType.BLOCK_STATEMENT);
    return statement;
  }

  private static boolean parseExpressionInParenth(final PsiBuilder builder) {
    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaErrorMessages.message("expected.lparen"));
      return false;
    }

    final PsiBuilder.Marker beforeExpr = builder.mark();
    final PsiBuilder.Marker expr = ExpressionParser.parse(builder);
    if (expr == null || builder.getTokenType() == JavaTokenType.SEMICOLON) {
      beforeExpr.rollbackTo();
      error(builder, JavaErrorMessages.message("expected.expression"));
      if (builder.getTokenType() != JavaTokenType.RPARENTH) {
        return false;
      }
    }
    else {
      beforeExpr.drop();
      if (builder.getTokenType() != JavaTokenType.RPARENTH) {
        error(builder, JavaErrorMessages.message("expected.rparen"));
        return false;
      }
    }

    builder.advanceLexer();
    return true;
  }
}
