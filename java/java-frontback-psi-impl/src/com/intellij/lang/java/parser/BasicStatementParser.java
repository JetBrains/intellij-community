// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesBinders;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.source.AbstractBasicJavaElementTypeFactory;
import com.intellij.psi.impl.source.WhiteSpaceAndCommentSetHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

import static com.intellij.lang.PsiBuilderUtil.*;
import static com.intellij.lang.java.parser.BasicJavaParserUtil.*;
import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_JAVA_COMMENT_OR_WHITESPACE_BIT_SET;

public class BasicStatementParser {
  private static final TokenSet YIELD_STMT_INDICATOR_TOKENS = TokenSet.create(
    JavaTokenType.PLUS, JavaTokenType.MINUS, JavaTokenType.EXCL, JavaTokenType.TILDE,
    JavaTokenType.SUPER_KEYWORD, JavaTokenType.THIS_KEYWORD,

    JavaTokenType.TRUE_KEYWORD, JavaTokenType.FALSE_KEYWORD, JavaTokenType.NULL_KEYWORD,

    JavaTokenType.STRING_LITERAL, JavaTokenType.INTEGER_LITERAL, JavaTokenType.DOUBLE_LITERAL,
    JavaTokenType.FLOAT_LITERAL, JavaTokenType.LONG_LITERAL, JavaTokenType.CHARACTER_LITERAL,
    JavaTokenType.TEXT_BLOCK_LITERAL,

    JavaTokenType.IDENTIFIER, JavaTokenType.SWITCH_KEYWORD, JavaTokenType.NEW_KEYWORD,

    JavaTokenType.LPARENTH,

    // recovery
    JavaTokenType.RBRACE, JavaTokenType.SEMICOLON, JavaTokenType.CASE_KEYWORD
  );

  private enum BraceMode {
    TILL_FIRST, TILL_LAST
  }

  private static final TokenSet TRY_CLOSERS_SET = TokenSet.create(JavaTokenType.CATCH_KEYWORD, JavaTokenType.FINALLY_KEYWORD);

  private final BasicJavaParser myParser;
  private final AbstractBasicJavaElementTypeFactory.JavaElementTypeContainer myJavaElementTypeContainer;
  private final WhiteSpaceAndCommentSetHolder myWhiteSpaceAndCommentSetHolder = WhiteSpaceAndCommentSetHolder.INSTANCE;

  public BasicStatementParser(@NotNull BasicJavaParser javaParser) {
    myParser = javaParser;
    myJavaElementTypeContainer = javaParser.getJavaElementTypeFactory().getContainer();
  }

  @Nullable
  public PsiBuilder.Marker parseCodeBlock(@NotNull PsiBuilder builder) {
    return parseCodeBlock(builder, false);
  }

  @Nullable
  public PsiBuilder.Marker parseCodeBlock(@NotNull PsiBuilder builder, boolean isStatement) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) return null;
    if (isStatement && isParseStatementCodeBlocksDeep(builder)) return parseCodeBlockDeep(builder, false);
    return parseBlockLazy(builder, JavaTokenType.LBRACE, JavaTokenType.RBRACE, myJavaElementTypeContainer.CODE_BLOCK);
  }

  @Nullable
  public PsiBuilder.Marker parseCodeBlockDeep(@NotNull PsiBuilder builder, boolean parseUntilEof) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) return null;

    PsiBuilder.Marker codeBlock = builder.mark();
    builder.advanceLexer();

    parseStatements(builder, parseUntilEof ? BraceMode.TILL_LAST : BraceMode.TILL_FIRST);

    boolean greedyBlock = !expectOrError(builder, JavaTokenType.RBRACE, "expected.rbrace");
    builder.getTokenType(); // eat spaces

    done(codeBlock, myJavaElementTypeContainer.CODE_BLOCK, myWhiteSpaceAndCommentSetHolder);
    if (greedyBlock) {
      codeBlock.setCustomEdgeTokenBinders(null, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    }
    return codeBlock;
  }

  public void parseStatements(@NotNull PsiBuilder builder) {
    parseStatements(builder, null);
  }

  private void parseStatements(PsiBuilder builder, @Nullable BraceMode braceMode) {
    while (builder.getTokenType() != null) {
      PsiBuilder.Marker statement = parseStatement(builder);
      if (statement != null) continue;

      IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.RBRACE &&
          (braceMode == BraceMode.TILL_FIRST || braceMode == BraceMode.TILL_LAST && builder.lookAhead(1) == null)) {
        break;
      }

      PsiBuilder.Marker error = builder.mark();
      builder.advanceLexer();
      if (tokenType == JavaTokenType.ELSE_KEYWORD) {
        error.error(JavaPsiBundle.message("else.without.if"));
      }
      else if (tokenType == JavaTokenType.CATCH_KEYWORD) {
        error.error(JavaPsiBundle.message("catch.without.try"));
      }
      else if (tokenType == JavaTokenType.FINALLY_KEYWORD) {
        error.error(JavaPsiBundle.message("finally.without.try"));
      }
      else {
        error.error(JavaPsiBundle.message("unexpected.token"));
      }
    }
  }

  @Nullable
  public PsiBuilder.Marker parseStatement(@NotNull PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
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
    else if (isStmtYieldToken(builder, tokenType)) {
      return parseYieldStatement(builder);
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
      PsiBuilder.Marker empty = builder.mark();
      builder.advanceLexer();
      done(empty, myJavaElementTypeContainer.EMPTY_STATEMENT, myWhiteSpaceAndCommentSetHolder);
      return empty;
    }
    else if (tokenType == JavaTokenType.IDENTIFIER || tokenType == JavaTokenType.AT) {
      PsiBuilder.Marker refPos = builder.mark();
      boolean nonSealed = BasicDeclarationParser.isNonSealedToken(builder, tokenType);
      myParser.getDeclarationParser().parseAnnotations(builder);
      skipQualifiedName(builder);
      IElementType current = builder.getTokenType(), next = builder.lookAhead(1);
      refPos.rollbackTo();

      if (current == JavaTokenType.LT || current == JavaTokenType.DOT && next == JavaTokenType.AT || nonSealed) {
        PsiBuilder.Marker declStatement = builder.mark();

        if (myParser.getDeclarationParser().parse(builder, BasicDeclarationParser.BaseContext.CODE_BLOCK) != null) {
          done(declStatement, myJavaElementTypeContainer.DECLARATION_STATEMENT, myWhiteSpaceAndCommentSetHolder);
          return declStatement;
        }

        BasicReferenceParser.TypeInfo type = myParser.getReferenceParser().parseTypeInfo(builder, 0);
        if (current == JavaTokenType.LT && (type == null || !type.isParameterized)) {
          declStatement.rollbackTo();
        }
        else if (type == null || builder.getTokenType() != JavaTokenType.DOUBLE_COLON) {
          error(builder, JavaPsiBundle.message("expected.identifier"));
          if (type == null) builder.advanceLexer();
          done(declStatement, myJavaElementTypeContainer.DECLARATION_STATEMENT, myWhiteSpaceAndCommentSetHolder);
          return declStatement;
        }
        else {
          declStatement.rollbackTo();  // generic type followed by the double colon is a good candidate for being a constructor reference
        }
      }
    }

    PsiBuilder.Marker pos = builder.mark();
    PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    IElementType incompleteDeclarationRestrictedTokenType = null;

    if (expr != null) {
      int count = 1;
      PsiBuilder.Marker list = expr.precede();
      PsiBuilder.Marker statement = list.precede();
      while (builder.getTokenType() == JavaTokenType.COMMA) {
        PsiBuilder.Marker commaPos = builder.mark();
        builder.advanceLexer();
        PsiBuilder.Marker expr1 = myParser.getExpressionParser().parse(builder);
        if (expr1 == null) {
          commaPos.rollbackTo();
          break;
        }
        commaPos.drop();
        count++;
      }
      if (count > 1) {
        pos.drop();
        done(list, myJavaElementTypeContainer.EXPRESSION_LIST, myWhiteSpaceAndCommentSetHolder);
        semicolon(builder);
        done(statement, myJavaElementTypeContainer.EXPRESSION_LIST_STATEMENT, myWhiteSpaceAndCommentSetHolder);
        return statement;
      }
      if (exprType(expr) != myJavaElementTypeContainer.REFERENCE_EXPRESSION) {
        drop(list, pos);
        semicolon(builder);
        done(statement, myJavaElementTypeContainer.EXPRESSION_STATEMENT, myWhiteSpaceAndCommentSetHolder);
        return statement;
      }
      boolean singleToken = expr.getEndIndex() - expr.getStartIndex() == 1;
      pos.rollbackTo();
      if (singleToken && builder.getTokenType() == JavaTokenType.IDENTIFIER) {
        String text = builder.getTokenText();
        if (PsiKeyword.RECORD.equals(text) && JavaFeature.RECORDS.isSufficient(getLanguageLevel(builder))) {
          incompleteDeclarationRestrictedTokenType = JavaTokenType.RECORD_KEYWORD;
        }
        if (PsiKeyword.VAR.equals(text) && JavaFeature.LVTI.isSufficient(getLanguageLevel(builder))) {
          incompleteDeclarationRestrictedTokenType = JavaTokenType.VAR_KEYWORD;
        }
      }
    }
    else {
      pos.drop();
    }

    PsiBuilder.Marker decl = myParser.getDeclarationParser().parse(builder, BasicDeclarationParser.BaseContext.CODE_BLOCK);
    if (decl != null) {
      PsiBuilder.Marker statement = decl.precede();
      done(statement, myJavaElementTypeContainer.DECLARATION_STATEMENT, myWhiteSpaceAndCommentSetHolder);
      return statement;
    }

    if (builder.getTokenType() == JavaTokenType.IDENTIFIER && builder.lookAhead(1) == JavaTokenType.COLON) {
      PsiBuilder.Marker statement = builder.mark();
      advance(builder, 2);
      parseStatement(builder);
      done(statement, myJavaElementTypeContainer.LABELED_STATEMENT, myWhiteSpaceAndCommentSetHolder);
      return statement;
    }

    if (expr != null) {
      PsiBuilder.Marker statement = builder.mark();
      IElementType statementType;
      if (incompleteDeclarationRestrictedTokenType != null) {
        builder.remapCurrentToken(incompleteDeclarationRestrictedTokenType);
        builder.advanceLexer();
        error(builder, JavaPsiBundle.message("expected.identifier"));
        statementType = myJavaElementTypeContainer.DECLARATION_STATEMENT;
      } else {
        myParser.getExpressionParser().parse(builder);
        statementType = myJavaElementTypeContainer.EXPRESSION_STATEMENT;
        semicolon(builder);
      }
      done(statement, statementType, myWhiteSpaceAndCommentSetHolder);
      return statement;
    }

    return null;
  }

  private static boolean isStmtYieldToken(@NotNull PsiBuilder builder, IElementType tokenType) {
    if (!(tokenType == JavaTokenType.IDENTIFIER &&
          PsiKeyword.YIELD.equals(builder.getTokenText()) &&
          JavaFeature.SWITCH_EXPRESSION.isSufficient(getLanguageLevel(builder)))) {
      return false;
    }
    // we prefer to parse it as yield stmt wherever possible (even in incomplete syntax)
    PsiBuilder.Marker maybeYieldStmt = builder.mark();
    builder.advanceLexer();
    IElementType tokenAfterYield = builder.getTokenType();
    if (tokenAfterYield == null || YIELD_STMT_INDICATOR_TOKENS.contains(tokenAfterYield)) {
      maybeYieldStmt.rollbackTo();
      return true;
    }
    if (JavaTokenType.PLUSPLUS.equals(tokenAfterYield) || JavaTokenType.MINUSMINUS.equals(tokenAfterYield)) {
      builder.advanceLexer();
      boolean isYieldStmt = !builder.getTokenType().equals(JavaTokenType.SEMICOLON);
      maybeYieldStmt.rollbackTo();
      return isYieldStmt;
    }
    maybeYieldStmt.rollbackTo();
    return false;
  }

  private static void skipQualifiedName(PsiBuilder builder) {
    if (!expect(builder, JavaTokenType.IDENTIFIER)) return;
    while (builder.getTokenType() == JavaTokenType.DOT && builder.lookAhead(1) == JavaTokenType.IDENTIFIER) {
      advance(builder, 2);
    }
  }

  @NotNull
  private PsiBuilder.Marker parseIfStatement(PsiBuilder builder) {
    ArrayList<PsiBuilder.Marker> stack = null;
    PsiBuilder.Marker statement;
    while (true) {
      // replaced recursion with iteration plus stack to avoid huge call stack for extremely large else-if chains
      statement = builder.mark();
      builder.advanceLexer();

      if (parseExprInParenth(builder)) {
        if (parseStatement(builder) == null) {
          error(builder, JavaPsiBundle.message("expected.statement"));
        }
        else if (expect(builder, JavaTokenType.ELSE_KEYWORD)) {
          if (builder.getTokenType() == JavaTokenType.IF_KEYWORD) {
            if (stack == null) stack = new ArrayList<>();
            stack.add(statement);
            continue;
          }
          else if (parseStatement(builder) == null) {
            error(builder, JavaPsiBundle.message("expected.statement"));
          }
        }
      }
      break;
    }
    done(statement, myJavaElementTypeContainer.IF_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    if (stack != null) {
      for (int i = stack.size() - 1; i >= 0; i--) {
        statement = stack.get(i);
        done(statement, myJavaElementTypeContainer.IF_STATEMENT, myWhiteSpaceAndCommentSetHolder);
      }
    }
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseWhileStatement(PsiBuilder builder) {
    return parseExprInParenthWithBlock(builder, myJavaElementTypeContainer.WHILE_STATEMENT, false);
  }


  @Contract(pure = true)
  private boolean isRecordPatternInForEach(final PsiBuilder builder) {
    PsiBuilder.Marker patternStart = myParser.getPatternParser().preParsePattern(builder);
    if (patternStart == null) {
      return false;
    }
    if (builder.getTokenType() != JavaTokenType.LPARENTH) {
      patternStart.rollbackTo();
      return false;
    }
    builder.advanceLexer();

    // we must distinguish a record pattern from method call in for (foo();;)
    int parenBalance = 1;
    while (true) {
      IElementType current = builder.getTokenType();
      if (current == null) {
        patternStart.rollbackTo();
        return false;
      }
      if (current == JavaTokenType.LPARENTH) {
        parenBalance++;
      }
      if (current == JavaTokenType.RPARENTH) {
        parenBalance--;
        if (parenBalance == 0) {
          break;
        }
      }
      builder.advanceLexer();
    }
    builder.advanceLexer();
    boolean isRecordPattern = builder.getTokenType() != JavaTokenType.SEMICOLON && builder.getTokenType() != JavaTokenType.DOT;
    patternStart.rollbackTo();
    return isRecordPattern;
  }

  @NotNull
  private PsiBuilder.Marker parseForStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaPsiBundle.message("expected.lparen"));
      done(statement, myJavaElementTypeContainer.FOR_STATEMENT, myWhiteSpaceAndCommentSetHolder);
      return statement;
    }

    if (isRecordPatternInForEach(builder)) {
      myParser.getPatternParser().parsePattern(builder);
      if (builder.getTokenType() == JavaTokenType.COLON) {
        return parseForEachFromColon(builder, statement, myJavaElementTypeContainer.FOREACH_PATTERN_STATEMENT);
      }
      error(builder, JavaPsiBundle.message("expected.colon"));
      // recovery: just skip everything until ')'
      while (true) {
        IElementType tokenType = builder.getTokenType();
        if (tokenType == null) {
          done(statement, myJavaElementTypeContainer.FOREACH_PATTERN_STATEMENT, myWhiteSpaceAndCommentSetHolder);
          return statement;
        }
        if (tokenType == JavaTokenType.RPARENTH) {
          return parserForEachFromRparenth(builder, statement, myJavaElementTypeContainer.FOREACH_PATTERN_STATEMENT);
        }
        builder.advanceLexer();
      }
    }

    PsiBuilder.Marker afterParenth = builder.mark();
    PsiBuilder.Marker param = myParser.getDeclarationParser().parseParameter(builder, false, false, true);
    if (param == null || exprType(param) != myJavaElementTypeContainer.PARAMETER || builder.getTokenType() != JavaTokenType.COLON) {
      afterParenth.rollbackTo();
      return parseForLoopFromInitializer(builder, statement);
    }
    else {
      afterParenth.drop();
      return parseForEachFromColon(builder, statement, myJavaElementTypeContainer.FOREACH_STATEMENT);
    }
  }

  @NotNull
  private PsiBuilder.Marker parseForLoopFromInitializer(PsiBuilder builder, PsiBuilder.Marker statement) {
    if (parseStatement(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.statement"));
      if (!expect(builder, JavaTokenType.RPARENTH)) {
        done(statement, myJavaElementTypeContainer.FOR_STATEMENT, myWhiteSpaceAndCommentSetHolder);
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
          error(builder, JavaPsiBundle.message("expected.semicolon"));
        }
        if (!expect(builder, JavaTokenType.RPARENTH)) {
          done(statement, myJavaElementTypeContainer.FOR_STATEMENT, myWhiteSpaceAndCommentSetHolder);
          return statement;
        }
      }
      else {
        parseForUpdateExpressions(builder);
        if (!expect(builder, JavaTokenType.RPARENTH)) {
          error(builder, JavaPsiBundle.message("expected.rparen"));
          done(statement, myJavaElementTypeContainer.FOR_STATEMENT, myWhiteSpaceAndCommentSetHolder);
          return statement;
        }
      }
    }

    PsiBuilder.Marker bodyStatement = parseStatement(builder);
    if (bodyStatement == null) {
      error(builder, JavaPsiBundle.message("expected.statement"));
    }

    done(statement, myJavaElementTypeContainer.FOR_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  private static IElementType getLastToken(PsiBuilder builder) {
    IElementType token;
    int offset = -1;
    while (BASIC_JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains((token = builder.rawLookup(offset)))) offset--;
    return token;
  }

  private void parseForUpdateExpressions(PsiBuilder builder) {
    PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    if (expr == null) return;

    PsiBuilder.Marker expressionStatement;
    if (builder.getTokenType() != JavaTokenType.COMMA) {
      expressionStatement = expr.precede();
      done(expressionStatement, myJavaElementTypeContainer.EXPRESSION_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    }
    else {
      PsiBuilder.Marker expressionList = expr.precede();
      expressionStatement = expressionList.precede();

      do {
        builder.advanceLexer();
        PsiBuilder.Marker nextExpression = myParser.getExpressionParser().parse(builder);
        if (nextExpression == null) {
          error(builder, JavaPsiBundle.message("expected.expression"));
        }
      }
      while (builder.getTokenType() == JavaTokenType.COMMA);

      done(expressionList, myJavaElementTypeContainer.EXPRESSION_LIST, myWhiteSpaceAndCommentSetHolder);
      done(expressionStatement, myJavaElementTypeContainer.EXPRESSION_LIST_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    }

    expressionStatement.setCustomEdgeTokenBinders(null, WhitespacesBinders.DEFAULT_RIGHT_BINDER);
  }

  @NotNull
  private PsiBuilder.Marker parseForEachFromColon(PsiBuilder builder, PsiBuilder.Marker statement, IElementType foreachStatement) {
    builder.advanceLexer();

    if (myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
    }

    return parserForEachFromRparenth(builder, statement, foreachStatement);
  }

  private PsiBuilder.Marker parserForEachFromRparenth(PsiBuilder builder, PsiBuilder.Marker statement, IElementType forEachType) {
    if (expectOrError(builder, JavaTokenType.RPARENTH, "expected.rparen") && parseStatement(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.statement"));
    }

    done(statement, forEachType, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseDoWhileStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    PsiBuilder.Marker body = parseStatement(builder);
    if (body == null) {
      error(builder, JavaPsiBundle.message("expected.statement"));
    }
    else if (!expect(builder, JavaTokenType.WHILE_KEYWORD)) {
      error(builder, JavaPsiBundle.message("expected.while"));
    }
    else if (parseExprInParenth(builder)) {
      semicolon(builder);
    }

    done(statement, myJavaElementTypeContainer.DO_WHILE_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseSwitchStatement(PsiBuilder builder) {
    return parseExprInParenthWithBlock(builder, myJavaElementTypeContainer.SWITCH_STATEMENT, true);
  }

  /**
   * @return marker and whether it contains expression inside
   */
  @NotNull
  @ApiStatus.Internal
  public Pair<PsiBuilder.@Nullable Marker, Boolean> parseCaseLabel(PsiBuilder builder) {
    if (builder.getTokenType() == JavaTokenType.DEFAULT_KEYWORD) {
      PsiBuilder.Marker defaultElement = builder.mark();
      builder.advanceLexer();
      done(defaultElement, myJavaElementTypeContainer.DEFAULT_CASE_LABEL_ELEMENT, myWhiteSpaceAndCommentSetHolder);
      return Pair.create(defaultElement, false);
    }
    if (myParser.getPatternParser().isPattern(builder)) {
      PsiBuilder.Marker pattern = myParser.getPatternParser().parsePattern(builder);
      return Pair.create(pattern, false);
    }
    return Pair.create(myParser.getExpressionParser().parseAssignmentForbiddingLambda(builder), true);
  }

  private PsiBuilder.Marker parseSwitchLabelStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    boolean isCase = builder.getTokenType() == JavaTokenType.CASE_KEYWORD;
    builder.advanceLexer();

    if (isCase) {
      boolean patternsAllowed = JavaFeature.PATTERNS_IN_SWITCH.isSufficient(getLanguageLevel(builder));
      PsiBuilder.Marker list = builder.mark();
      do {
        Pair<PsiBuilder.Marker, Boolean> markerAndIsExpression = parseCaseLabel(builder);
        PsiBuilder.Marker caseLabel = markerAndIsExpression.first;
        if (caseLabel == null) {
          error(builder, JavaPsiBundle.message(patternsAllowed ? "expected.case.label.element" : "expected.expression"));
        }
      }
      while (expect(builder, JavaTokenType.COMMA));
      done(list, myJavaElementTypeContainer.CASE_LABEL_ELEMENT_LIST, myWhiteSpaceAndCommentSetHolder);
      parseGuard(builder);
    }

    if (expect(builder, JavaTokenType.ARROW)) {
      PsiBuilder.Marker expr;
      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        PsiBuilder.Marker body = builder.mark();
        parseCodeBlock(builder, true);
        body.done(myJavaElementTypeContainer.BLOCK_STATEMENT);
        if (builder.getTokenType() == JavaTokenType.SEMICOLON) {
          PsiBuilder.Marker mark = builder.mark();
          while (builder.getTokenType() == JavaTokenType.SEMICOLON) builder.advanceLexer();
          mark.error(JavaPsiBundle.message("expected.switch.label"));
        }
      }
      else if (builder.getTokenType() == JavaTokenType.THROW_KEYWORD) {
        parseThrowStatement(builder);
      }
      else if ((expr = myParser.getExpressionParser().parse(builder)) != null) {
        PsiBuilder.Marker body = expr.precede();
        semicolon(builder);
        body.done(myJavaElementTypeContainer.EXPRESSION_STATEMENT);
      }
      else {
        error(builder, JavaPsiBundle.message("expected.switch.rule"));
        expect(builder, JavaTokenType.SEMICOLON);
      }
      done(statement, myJavaElementTypeContainer.SWITCH_LABELED_RULE, myWhiteSpaceAndCommentSetHolder);
    }
    else {
      expectOrError(builder, JavaTokenType.COLON, "expected.colon.or.arrow");
      done(statement, myJavaElementTypeContainer.SWITCH_LABEL_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    }

    return statement;
  }

  private void parseGuard(PsiBuilder builder) {
    if (builder.getTokenType() == JavaTokenType.IDENTIFIER && PsiKeyword.WHEN.equals(builder.getTokenText())) {
      builder.remapCurrentToken(JavaTokenType.WHEN_KEYWORD);
      builder.advanceLexer();
      PsiBuilder.Marker guardingExpression = myParser.getExpressionParser().parseAssignmentForbiddingLambda(builder);
      if (guardingExpression == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }
    }
  }

  @NotNull
  private PsiBuilder.Marker parseBreakStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    expect(builder, JavaTokenType.IDENTIFIER);
    semicolon(builder);
    done(statement, myJavaElementTypeContainer.BREAK_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseYieldStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.remapCurrentToken(JavaTokenType.YIELD_KEYWORD);
    builder.advanceLexer();

    if (myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
    }
    else {
      semicolon(builder);
    }

    done(statement, myJavaElementTypeContainer.YIELD_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseContinueStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    expect(builder, JavaTokenType.IDENTIFIER);
    semicolon(builder);
    done(statement, myJavaElementTypeContainer.CONTINUE_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseReturnStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();
    myParser.getExpressionParser().parse(builder);
    semicolon(builder);
    done(statement, myJavaElementTypeContainer.RETURN_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseThrowStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
    }
    else {
      semicolon(builder);
    }

    done(statement, myJavaElementTypeContainer.THROW_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseSynchronizedStatement(PsiBuilder builder) {
    return parseExprInParenthWithBlock(builder, myJavaElementTypeContainer.SYNCHRONIZED_STATEMENT, true);
  }

  @NotNull
  private PsiBuilder.Marker parseTryStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    boolean hasResourceList = builder.getTokenType() == JavaTokenType.LPARENTH;
    if (hasResourceList) {
      myParser.getDeclarationParser().parseResourceList(builder);
    }

    PsiBuilder.Marker tryBlock = parseCodeBlock(builder, true);
    if (tryBlock == null) {
      error(builder, JavaPsiBundle.message("expected.lbrace"));
    }
    else if (!hasResourceList && !TRY_CLOSERS_SET.contains(builder.getTokenType())) {
      error(builder, JavaPsiBundle.message("expected.catch.or.finally"));
    }
    else {
      while (builder.getTokenType() == JavaTokenType.CATCH_KEYWORD) {
        if (!parseCatchBlock(builder)) break;
      }

      if (expect(builder, JavaTokenType.FINALLY_KEYWORD)) {
        PsiBuilder.Marker finallyBlock = parseCodeBlock(builder, true);
        if (finallyBlock == null) {
          error(builder, JavaPsiBundle.message("expected.lbrace"));
        }
      }
    }

    done(statement, myJavaElementTypeContainer.TRY_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  public boolean parseCatchBlock(@NotNull PsiBuilder builder) {
    assert builder.getTokenType() == JavaTokenType.CATCH_KEYWORD : builder.getTokenType();
    PsiBuilder.Marker section = builder.mark();
    builder.advanceLexer();

    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaPsiBundle.message("expected.lparen"));
      done(section, myJavaElementTypeContainer.CATCH_SECTION, myWhiteSpaceAndCommentSetHolder);
      return false;
    }

    PsiBuilder.Marker param = myParser.getDeclarationParser().parseParameter(builder, false, true, false);
    if (param == null) {
      error(builder, JavaPsiBundle.message("expected.parameter"));
    }

    if (!expect(builder, JavaTokenType.RPARENTH)) {
      error(builder, JavaPsiBundle.message("expected.rparen"));
      done(section, myJavaElementTypeContainer.CATCH_SECTION, myWhiteSpaceAndCommentSetHolder);
      return false;
    }

    PsiBuilder.Marker body = parseCodeBlock(builder, true);
    if (body == null) {
      error(builder, JavaPsiBundle.message("expected.lbrace"));
      done(section, myJavaElementTypeContainer.CATCH_SECTION, myWhiteSpaceAndCommentSetHolder);
      return false;
    }

    done(section, myJavaElementTypeContainer.CATCH_SECTION, myWhiteSpaceAndCommentSetHolder);
    return true;
  }

  @NotNull
  private PsiBuilder.Marker parseAssertStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.boolean.expression"));
    }
    else if (expect(builder, JavaTokenType.COLON) && myParser.getExpressionParser().parse(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
    }
    else {
      semicolon(builder);
    }

    done(statement, myJavaElementTypeContainer.ASSERT_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  @NotNull
  private PsiBuilder.Marker parseBlockStatement(PsiBuilder builder) {
    PsiBuilder.Marker statement = builder.mark();
    parseCodeBlock(builder, true);
    done(statement, myJavaElementTypeContainer.BLOCK_STATEMENT, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  @NotNull
  public PsiBuilder.Marker parseExprInParenthWithBlock(@NotNull PsiBuilder builder, @NotNull IElementType type, boolean block) {
    PsiBuilder.Marker statement = builder.mark();
    builder.advanceLexer();

    if (parseExprInParenth(builder)) {
      PsiBuilder.Marker body = block ? parseCodeBlock(builder, true) : parseStatement(builder);
      if (body == null) {
        error(builder, JavaPsiBundle.message(block ? "expected.lbrace" : "expected.statement"));
      }
    }

    done(statement, type, myWhiteSpaceAndCommentSetHolder);
    return statement;
  }

  private boolean parseExprInParenth(PsiBuilder builder) {
    if (!expect(builder, JavaTokenType.LPARENTH)) {
      error(builder, JavaPsiBundle.message("expected.lparen"));
      return false;
    }

    PsiBuilder.Marker beforeExpr = builder.mark();
    PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    if (expr == null || builder.getTokenType() == JavaTokenType.SEMICOLON) {
      beforeExpr.rollbackTo();
      error(builder, JavaPsiBundle.message("expected.expression"));
      if (builder.getTokenType() != JavaTokenType.RPARENTH) {
        return false;
      }
    }
    else {
      beforeExpr.drop();
      if (builder.getTokenType() != JavaTokenType.RPARENTH) {
        error(builder, JavaPsiBundle.message("expected.rparen"));
        return false;
      }
    }

    builder.advanceLexer();
    return true;
  }
}
