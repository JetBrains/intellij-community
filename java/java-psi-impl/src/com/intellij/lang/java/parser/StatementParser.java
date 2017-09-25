/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.lang.WhitespacesBinders;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.PsiBuilderUtil.*;
import static com.intellij.lang.java.parser.JavaParserUtil.*;

public class StatementParser {
  private enum BraceMode {
    TILL_FIRST, TILL_LAST
  }

  private static final TokenSet TRY_CLOSERS_SET = TokenSet.create(JavaTokenType.CATCH_KEYWORD, JavaTokenType.FINALLY_KEYWORD);

  private final JavaParser myParser;

  public StatementParser(@NotNull final JavaParser javaParser) {
    myParser = javaParser;
  }

  @Nullable
  public PsiBuilder.Marker parseCodeBlock(final PsiBuilder builder) {
    return parseCodeBlock(builder, false);
  }

  @Nullable
  public PsiBuilder.Marker parseCodeBlock(final PsiBuilder builder, final boolean isStatement) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) return null;
    else if (isStatement && isParseStatementCodeBlocksDeep(builder)) return parseCodeBlockDeep(builder, false);

    return parseBlockLazy(builder, JavaTokenType.LBRACE, JavaTokenType.RBRACE, JavaElementType.CODE_BLOCK);
  }

  @Nullable
  public PsiBuilder.Marker parseCodeBlockDeep(final PsiBuilder builder, final boolean parseUntilEof) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) return null;

    final PsiBuilder.Marker codeBlock = builder.mark();
    builder.advanceLexer();

    parseStatements(builder, parseUntilEof ? BraceMode.TILL_LAST : BraceMode.TILL_FIRST);

    final boolean greedyBlock = !expectOrError(builder, JavaTokenType.RBRACE, "expected.rbrace");
    builder.getTokenType(); // eat spaces

    done(codeBlock, JavaElementType.CODE_BLOCK);
    if (greedyBlock) {
      codeBlock.setCustomEdgeTokenBinders(null, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    }
    return codeBlock;
  }

  public void parseStatements(final PsiBuilder builder) {
    parseStatements(builder, null);
  }

  private void parseStatements(final PsiBuilder builder, @Nullable final BraceMode braceMode) {
    while (builder.getTokenType() != null) {
      final PsiBuilder.Marker statement = parseStatement(builder);
      if (statement != null) continue;

      final IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.RBRACE) {
        if (braceMode == BraceMode.TILL_FIRST) {
          return;
        }
        else if (braceMode == BraceMode.TILL_LAST) {
          if (builder.lookAhead(1) == null) {
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
  public PsiBuilder.Marker parseStatement(final PsiBuilder builder) {
    final IElementType tokenType = builder.getTokenType();
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
      builder.advanceLexer();
      return null;
    }
    else if (tokenType == JavaTokenType.SEMICOLON) {
      final PsiBuilder.Marker empty = builder.mark();
      builder.advanceLexer();
      done(empty, JavaElementType.EMPTY_STATEMENT);
      return empty;
    }
    else if (tokenType == JavaTokenType.IDENTIFIER || tokenType == JavaTokenType.AT) {
      final PsiBuilder.Marker refPos = builder.mark();
      myParser.getDeclarationParser().parseAnnotations(builder);
      skipQualifiedName(builder);
      final IElementType suspectedLT = builder.getTokenType(), next = builder.lookAhead(1);
      refPos.rollbackTo();
      if (suspectedLT == JavaTokenType.LT || suspectedLT == JavaTokenType.DOT && next == JavaTokenType.AT) {
        final PsiBuilder.Marker declStatement = builder.mark();
        final PsiBuilder.Marker decl = myParser.getDeclarationParser().parse(builder, DeclarationParser.Context.CODE_BLOCK);
        if (decl == null) {
          PsiBuilder.Marker marker = myParser.getReferenceParser().parseType(builder, 0);
          error(builder, JavaErrorMessages.message("expected.identifier"));
          if (marker == null) builder.advanceLexer();
        }
        done(declStatement, JavaElementType.DECLARATION_STATEMENT);
        return declStatement;
      }
    }

    final PsiBuilder.Marker pos = builder.mark();
    final PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);

    if (expr != null) {
      int count = 1;
      final PsiBuilder.Marker list = expr.precede();
      final PsiBuilder.Marker statement = list.precede();
      while (builder.getTokenType() == JavaTokenType.COMMA) {
        final PsiBuilder.Marker commaPos = builder.mark();
        builder.advanceLexer();
        final PsiBuilder.Marker expr1 = myParser.getExpressionParser().parse(builder);
        if (expr1 == null) {
          commaPos.rollbackTo();
          break;
        }
        commaPos.drop();
        count++;
      }
      if (count > 1) {
        pos.drop();
        done(list, JavaElementType.EXPRESSION_LIST);
        semicolon(builder);
        done(statement, JavaElementType.EXPRESSION_LIST_STATEMENT);
        return statement;
      }
      if (exprType(expr) != JavaElementType.REFERENCE_EXPRESSION) {
        drop(list, pos);
        semicolon(builder);
        done(statement, JavaElementType.EXPRESSION_STATEMENT);
        return statement;
      }
      pos.rollbackTo();
    }
    else {
      pos.drop();
    }

    final PsiBuilder.Marker decl = myParser.getDeclarationParser().parse(builder, DeclarationParser.Context.CODE_BLOCK);
    if (decl != null) {
      final PsiBuilder.Marker statement = decl.precede();
      done(statement, JavaElementType.DECLARATION_STATEMENT);
      return statement;
    }

    if (builder.getTokenType() == JavaTokenType.IDENTIFIER && builder.lookAhead(1) == JavaTokenType.COLON) {
      final PsiBuilder.Marker statement = builder.mark();
      advance(builder, 2);
      parseStatement(builder);
      done(statement, JavaElementType.LABELED_STATEMENT);
      return statement;
    }

    if (expr != null) {
      final PsiBuilder.Marker statement = builder.mark();
      myParser.getExpressionParser().parse(builder);
      semicolon(builder);
      done(statement, JavaElementType.EXPRESSION_STATEMENT);
      return statement;
    }

    return null;
  }

  private static void skipQualifiedName(final PsiBuilder builder) {
    if (!expect(builder, JavaTokenType.IDENTIFIER)) return;
    while (builder.getTokenType() == JavaTokenType.DOT && builder.lookAhead(1) == JavaTokenType.IDENTIFIER) {
      advance(builder, 2);
    }
  }

  @NotNull
  private PsiBuilder.Marker parseIfStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!parseExpressionInParenth(builder)) {
      done(statement, JavaElementType.IF_STATEMENT);
      return statement;
    }

    final PsiBuilder.Marker thenStatement = parseStatement(builder);
    if (thenStatement == null) {
      error(builder, JavaErrorMessages.message("expected.statement"));
      done(statement, JavaElementType.IF_STATEMENT);
      return statement;
    }

    if (!expect(builder, JavaTokenType.ELSE_KEYWORD)) {
      done(statement, JavaElementType.IF_STATEMENT);
      return statement;
    }

    final PsiBuilder.Marker elseStatement = parseStatement(builder);
    if (elseStatement == null) {
      error(builder, JavaErrorMessages.message("expected.statement"));
    }

    done(statement, JavaElementType.IF_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseWhileStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!parseExpressionInParenth(builder)) {
      done(statement, JavaElementType.WHILE_STATEMENT);
      return statement;
    }

    final PsiBuilder.Marker bodyStatement = parseStatement(builder);
    if (bodyStatement == null) {
      error(builder, JavaErrorMessages.message("expected.statement"));
    }

    done(statement, JavaElementType.WHILE_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseForStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaErrorMessages.message("expected.lparen"));
      done(statement, JavaElementType.FOR_STATEMENT);
      return statement;
    }

    final PsiBuilder.Marker afterParenth = builder.mark();
    final PsiBuilder.Marker param = myParser.getDeclarationParser().parseParameter(builder, false, false, true);
    if (param == null || exprType(param) != JavaElementType.PARAMETER || builder.getTokenType() != JavaTokenType.COLON) {
      afterParenth.rollbackTo();
      return parseForLoopFromInitializer(builder, statement);
    }
    else {
      afterParenth.drop();
      return parseForEachFromColon(builder, statement);
    }
  }

  @NotNull
  private PsiBuilder.Marker parseForLoopFromInitializer(PsiBuilder builder, PsiBuilder.Marker statement) {
    PsiBuilder.Marker init = parseStatement(builder);
    if (init == null) {
      error(builder, JavaErrorMessages.message("expected.statement"));
      if (!expect(builder, JavaTokenType.RPARENTH)) {
        done(statement, JavaElementType.FOR_STATEMENT);
        return statement;
      }
    }
    else {
      boolean missingSemicolon = false;
      if (getLastToken(builder) != JavaTokenType.SEMICOLON) {
        missingSemicolon = !expectOrError(builder, JavaTokenType.SEMICOLON, "expected.semicolon");
      }

      PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
      missingSemicolon &= expr == null;

      if (!expect(builder, JavaTokenType.SEMICOLON)) {
        if (!missingSemicolon) {
          error(builder, JavaErrorMessages.message("expected.semicolon"));
        }
        if (!expect(builder, JavaTokenType.RPARENTH)) {
          done(statement, JavaElementType.FOR_STATEMENT);
          return statement;
        }
      }
      else {
        parseExpressionOrExpressionList(builder);
        if (!expect(builder, JavaTokenType.RPARENTH)) {
          error(builder, JavaErrorMessages.message("expected.rparen"));
          done(statement, JavaElementType.FOR_STATEMENT);
          return statement;
        }
      }
    }

    PsiBuilder.Marker bodyStatement = parseStatement(builder);
    if (bodyStatement == null) {
      error(builder, JavaErrorMessages.message("expected.statement"));
    }

    done(statement, JavaElementType.FOR_STATEMENT);
    return statement;
  }

  private static IElementType getLastToken(PsiBuilder builder) {
    IElementType token;
    int offset = -1;
    while (ElementType.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains((token = builder.rawLookup(offset)))) offset--;
    return token;
  }

  private void parseExpressionOrExpressionList(final PsiBuilder builder) {
    final PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    if (expr == null) return;

    final PsiBuilder.Marker expressionStatement;
    if (builder.getTokenType() != JavaTokenType.COMMA) {
      expressionStatement = expr.precede();
      done(expressionStatement, JavaElementType.EXPRESSION_STATEMENT);
    }
    else {
      final PsiBuilder.Marker expressionList = expr.precede();
      expressionStatement = expressionList.precede();

      do {
        builder.advanceLexer();
        final PsiBuilder.Marker nextExpression = myParser.getExpressionParser().parse(builder);
        if (nextExpression == null) {
          error(builder, JavaErrorMessages.message("expected.expression"));
        }
      }
      while (builder.getTokenType() == JavaTokenType.COMMA);

      done(expressionList, JavaElementType.EXPRESSION_LIST);
      done(expressionStatement, JavaElementType.EXPRESSION_LIST_STATEMENT);
    }
  }

  @NotNull
  private PsiBuilder.Marker parseForEachFromColon(PsiBuilder builder, PsiBuilder.Marker statement) {
    builder.advanceLexer();

    final PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
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

    done(statement, JavaElementType.FOREACH_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseDoWhileStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    final PsiBuilder.Marker body = parseStatement(builder);
    if (body == null) {
      error(builder, JavaErrorMessages.message("expected.statement"));
      done(statement, JavaElementType.DO_WHILE_STATEMENT);
      return statement;
    }

    if (!expect(builder, JavaTokenType.WHILE_KEYWORD)) {
      error(builder, JavaErrorMessages.message("expected.while"));
      done(statement, JavaElementType.DO_WHILE_STATEMENT);
      return statement;
    }

    if (parseExpressionInParenth(builder)) {
      semicolon(builder);
    }

    done(statement, JavaElementType.DO_WHILE_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseSwitchStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!parseExpressionInParenth(builder)) {
      done(statement, JavaElementType.SWITCH_STATEMENT);
      return statement;
    }

    final PsiBuilder.Marker body = parseCodeBlock(builder, true);
    if (body == null) {
      error(builder, JavaErrorMessages.message("expected.lbrace"));
    }

    done(statement, JavaElementType.SWITCH_STATEMENT);
    return statement;
  }

  @Nullable
  private PsiBuilder.Marker parseSwitchLabelStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    final boolean isCase = builder.getTokenType() == JavaTokenType.CASE_KEYWORD;
    builder.advanceLexer();

    if (isCase) {
      final PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
      if (expr == null) {
        statement.rollbackTo();
        return null;
      }
    }

    expectOrError(builder, JavaTokenType.COLON, "expected.colon");

    done(statement, JavaElementType.SWITCH_LABEL_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseBreakStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    expect(builder, JavaTokenType.IDENTIFIER);
    semicolon(builder);
    done(statement, JavaElementType.BREAK_STATEMENT);
    return statement;
  }

  @NotNull
  private static PsiBuilder.Marker parseContinueStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    expect(builder, JavaTokenType.IDENTIFIER);
    semicolon(builder);
    done(statement, JavaElementType.CONTINUE_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseReturnStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    myParser.getExpressionParser().parse(builder);

    semicolon(builder);
    done(statement, JavaElementType.RETURN_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseThrowStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    final PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    if (expr == null) {
      error(builder, JavaErrorMessages.message("expected.expression"));
      done(statement, JavaElementType.THROW_STATEMENT);
      return statement;
    }

    semicolon(builder);
    done(statement, JavaElementType.THROW_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseSynchronizedStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!parseExpressionInParenth(builder)) {
      done(statement, JavaElementType.SYNCHRONIZED_STATEMENT);
      return statement;
    }

    final PsiBuilder.Marker body = parseCodeBlock(builder, true);
    if (body == null) {
      error(builder, JavaErrorMessages.message("expected.lbrace"));
    }

    done(statement, JavaElementType.SYNCHRONIZED_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseTryStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    final boolean hasResourceList = builder.getTokenType() == JavaTokenType.LPARENTH;
    if (hasResourceList) {
      myParser.getDeclarationParser().parseResourceList(builder);
    }

    final PsiBuilder.Marker tryBlock = parseCodeBlock(builder, true);
    if (tryBlock == null) {
      error(builder, JavaErrorMessages.message("expected.lbrace"));
      done(statement, JavaElementType.TRY_STATEMENT);
      return statement;
    }

    if (!hasResourceList && !TRY_CLOSERS_SET.contains(builder.getTokenType())) {
      error(builder, JavaErrorMessages.message("expected.catch.or.finally"));
      done(statement, JavaElementType.TRY_STATEMENT);
      return statement;
    }

    while (builder.getTokenType() == JavaTokenType.CATCH_KEYWORD) {
      if (!parseCatchBlock(builder)) break;
    }

    if (expect(builder, JavaTokenType.FINALLY_KEYWORD)) {
      final PsiBuilder.Marker finallyBlock = parseCodeBlock(builder, true);
      if (finallyBlock == null) {
        error(builder, JavaErrorMessages.message("expected.lbrace"));
      }
    }

    done(statement, JavaElementType.TRY_STATEMENT);
    return statement;
  }

  public boolean parseCatchBlock(final PsiBuilder builder) {
    assert builder.getTokenType() == JavaTokenType.CATCH_KEYWORD : builder.getTokenType();
    final PsiBuilder.Marker section = builder.mark();
    builder.advanceLexer();

    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaErrorMessages.message("expected.lparen"));
      done(section, JavaElementType.CATCH_SECTION);
      return false;
    }

    final PsiBuilder.Marker param = myParser.getDeclarationParser().parseParameter(builder, false, true, false);
    if (param == null) {
      error(builder, JavaErrorMessages.message("expected.parameter"));
    }

    if (!expect(builder, JavaTokenType.RPARENTH)) {
      error(builder, JavaErrorMessages.message("expected.rparen"));
      done(section, JavaElementType.CATCH_SECTION);
      return false;
    }

    final PsiBuilder.Marker body = parseCodeBlock(builder, true);
    if (body == null) {
      error(builder, JavaErrorMessages.message("expected.lbrace"));
      done(section, JavaElementType.CATCH_SECTION);
      return false;
    }

    done(section, JavaElementType.CATCH_SECTION);
    return true;
  }

  @NotNull
  private PsiBuilder.Marker parseAssertStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    final PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    if (expr == null) {
      error(builder, JavaErrorMessages.message("expected.boolean.expression"));
      done(statement, JavaElementType.ASSERT_STATEMENT);
      return statement;
    }

    if (expect(builder, JavaTokenType.COLON)) {
      final PsiBuilder.Marker expr2 = myParser.getExpressionParser().parse(builder);
      if (expr2 == null) {
        error(builder, JavaErrorMessages.message("expected.expression"));
        done(statement, JavaElementType.ASSERT_STATEMENT);
        return statement;
      }
    }

    semicolon(builder);
    done(statement, JavaElementType.ASSERT_STATEMENT);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseBlockStatement(final PsiBuilder builder) {
    final PsiBuilder.Marker statement = builder.mark();
    parseCodeBlock(builder, true);
    done(statement, JavaElementType.BLOCK_STATEMENT);
    return statement;
  }

  private boolean parseExpressionInParenth(final PsiBuilder builder) {
    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaErrorMessages.message("expected.lparen"));
      return false;
    }

    final PsiBuilder.Marker beforeExpr = builder.mark();
    final PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
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
