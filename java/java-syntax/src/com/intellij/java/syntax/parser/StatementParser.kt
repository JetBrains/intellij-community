// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.parser

import com.intellij.java.syntax.JavaSyntaxBundle.message
import com.intellij.java.syntax.element.JavaSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.element.SyntaxElementTypes
import com.intellij.java.syntax.element.WhiteSpaceAndCommentSetHolder
import com.intellij.java.syntax.parser.JavaParserUtil.done
import com.intellij.java.syntax.parser.JavaParserUtil.error
import com.intellij.java.syntax.parser.JavaParserUtil.expectOrError
import com.intellij.java.syntax.parser.JavaParserUtil.exprType
import com.intellij.java.syntax.parser.JavaParserUtil.isParseStatementCodeBlocksDeep
import com.intellij.java.syntax.parser.JavaParserUtil.semicolon
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.WhitespacesBinders.defaultRightBinder
import com.intellij.platform.syntax.parser.WhitespacesBinders.greedyRightBinder
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.advance
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.expect
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.parseBlockLazy
import com.intellij.pom.java.JavaFeature
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

class StatementParser(private val myParser: JavaParser) {
  private enum class BraceMode {
    TILL_FIRST, TILL_LAST
  }

  private val myWhiteSpaceAndCommentSetHolder = WhiteSpaceAndCommentSetHolder

  @JvmOverloads
  fun parseCodeBlock(builder: SyntaxTreeBuilder, isStatement: Boolean = false): SyntaxTreeBuilder.Marker? {
    if (builder.tokenType !== JavaSyntaxTokenType.LBRACE) return null
    if (isStatement && isParseStatementCodeBlocksDeep(builder)) return parseCodeBlockDeep(builder, false)
    return builder.parseBlockLazy(JavaSyntaxTokenType.LBRACE, JavaSyntaxTokenType.RBRACE, JavaSyntaxElementType.CODE_BLOCK)
  }

  fun parseCodeBlockDeep(builder: SyntaxTreeBuilder, parseUntilEof: Boolean): SyntaxTreeBuilder.Marker? {
    if (builder.tokenType !== JavaSyntaxTokenType.LBRACE) return null

    val codeBlock = builder.mark()
    builder.advanceLexer()

    parseStatements(builder, if (parseUntilEof) BraceMode.TILL_LAST else BraceMode.TILL_FIRST)

    val greedyBlock = !expectOrError(builder, JavaSyntaxTokenType.RBRACE, "expected.rbrace")
    builder.tokenType // eat spaces

    done(codeBlock, JavaSyntaxElementType.CODE_BLOCK, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    if (greedyBlock) {
      codeBlock.setCustomEdgeTokenBinders(null, greedyRightBinder())
    }
    return codeBlock
  }

  fun parseStatements(builder: SyntaxTreeBuilder) {
    parseStatements(builder, null)
  }

  private fun parseStatements(builder: SyntaxTreeBuilder, braceMode: BraceMode?) {
    while (builder.tokenType != null) {
      val statement = parseStatement(builder)
      if (statement != null) continue

      val tokenType = builder.tokenType
      if (tokenType === JavaSyntaxTokenType.RBRACE &&
          (braceMode == BraceMode.TILL_FIRST || braceMode == BraceMode.TILL_LAST && builder.lookAhead(1) == null)
      ) {
        break
      }

      val error = builder.mark()
      builder.advanceLexer()
      when (tokenType) {
        JavaSyntaxTokenType.ELSE_KEYWORD -> error.error(message("else.without.if"))
        JavaSyntaxTokenType.CATCH_KEYWORD -> error.error(message("catch.without.try"))
        JavaSyntaxTokenType.FINALLY_KEYWORD -> error.error(message("finally.without.try"))
        else -> error.error(message("unexpected.token"))
      }
    }
  }

  fun parseStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val tokenType = builder.tokenType
    if (tokenType === JavaSyntaxTokenType.IF_KEYWORD) {
      return parseIfStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.WHILE_KEYWORD) {
      return parseWhileStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.FOR_KEYWORD) {
      return parseForStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.DO_KEYWORD) {
      return parseDoWhileStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.SWITCH_KEYWORD) {
      return parseSwitchStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.CASE_KEYWORD || tokenType === JavaSyntaxTokenType.DEFAULT_KEYWORD) {
      return parseSwitchLabelStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.BREAK_KEYWORD) {
      return parseBreakStatement(builder)
    }
    else if (isStmtYieldToken(builder, tokenType)) {
      return parseYieldStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.CONTINUE_KEYWORD) {
      return parseContinueStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.RETURN_KEYWORD) {
      return parseReturnStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.THROW_KEYWORD) {
      return parseThrowStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.SYNCHRONIZED_KEYWORD) {
      return parseSynchronizedStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.TRY_KEYWORD) {
      return parseTryStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.ASSERT_KEYWORD) {
      return parseAssertStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.LBRACE) {
      return parseBlockStatement(builder)
    }
    else if (tokenType === JavaSyntaxTokenType.SEMICOLON) {
      val empty = builder.mark()
      builder.advanceLexer()
      done(empty, JavaSyntaxElementType.EMPTY_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      return empty
    }
    else if (tokenType === JavaSyntaxTokenType.IDENTIFIER || tokenType === JavaSyntaxTokenType.AT) {
      val refPos = builder.mark()
      val nonSealed = isNonSealedToken(builder, tokenType, languageLevel)
      myParser.declarationParser.parseAnnotations(builder)
      skipQualifiedName(builder)
      val current = builder.tokenType
      val next = builder.lookAhead(1)
      refPos.rollbackTo()

      if (current === JavaSyntaxTokenType.LT || current === JavaSyntaxTokenType.DOT && next === JavaSyntaxTokenType.AT || nonSealed) {
        val declStatement = builder.mark()

        if (myParser.declarationParser.parse(builder, DeclarationParser.Context.CODE_BLOCK) != null) {
          done(declStatement, JavaSyntaxElementType.DECLARATION_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
          return declStatement
        }

        val type = myParser.referenceParser.parseTypeInfo(builder, 0)
        if (current === JavaSyntaxTokenType.LT && (type == null || !type.isParameterized)) {
          declStatement.rollbackTo()
        }
        else if (type == null || builder.tokenType !== JavaSyntaxTokenType.DOUBLE_COLON) {
          error(builder, message("expected.identifier"))
          if (type == null) builder.advanceLexer()
          done(declStatement, JavaSyntaxElementType.DECLARATION_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
          return declStatement
        }
        else {
          declStatement.rollbackTo() // generic type followed by the double colon is a good candidate for being a constructor reference
        }
      }
    }

    val pos = builder.mark()
    val expr = myParser.expressionParser.parse(builder)
    var incompleteDeclarationRestrictedTokenType: SyntaxElementType? = null

    if (expr != null) {
      var count = 1
      val list = expr.precede()
      val statement = list.precede()
      while (builder.tokenType === JavaSyntaxTokenType.COMMA) {
        val commaPos = builder.mark()
        builder.advanceLexer()
        val expr1 = myParser.expressionParser.parse(builder)
        if (expr1 == null) {
          commaPos.rollbackTo()
          break
        }
        commaPos.drop()
        count++
      }
      if (count > 1) {
        pos.drop()
        done(list, JavaSyntaxElementType.EXPRESSION_LIST, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
        semicolon(builder)
        done(statement, JavaSyntaxElementType.EXPRESSION_LIST_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
        return statement
      }
      if (exprType(expr) !== JavaSyntaxElementType.REFERENCE_EXPRESSION) {
        SyntaxBuilderUtil.drop(list, pos)
        semicolon(builder)
        done(statement, JavaSyntaxElementType.EXPRESSION_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
        return statement
      }
      val singleToken = expr.getEndTokenIndex() - expr.getStartTokenIndex() == 1
      pos.rollbackTo()
      if (singleToken && builder.tokenType === JavaSyntaxTokenType.IDENTIFIER) {
        val text = builder.tokenText
        if (JavaKeywords.RECORD == text && JavaFeature.RECORDS.isSufficient(
            this.languageLevel)
        ) {
          incompleteDeclarationRestrictedTokenType = JavaSyntaxTokenType.RECORD_KEYWORD
        }
        if (JavaKeywords.VAR == text && JavaFeature.LVTI.isSufficient(
            this.languageLevel)
        ) {
          incompleteDeclarationRestrictedTokenType = JavaSyntaxTokenType.VAR_KEYWORD
        }
      }
    }
    else {
      pos.drop()
    }

    val decl = myParser.declarationParser.parse(builder, DeclarationParser.Context.CODE_BLOCK)
    if (decl != null) {
      val statement = decl.precede()
      done(statement, JavaSyntaxElementType.DECLARATION_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      return statement
    }

    if (builder.tokenType === JavaSyntaxTokenType.IDENTIFIER && builder.lookAhead(1) === JavaSyntaxTokenType.COLON) {
      val statement = builder.mark()
      builder.advance(2)
      parseStatement(builder)
      done(statement, JavaSyntaxElementType.LABELED_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      return statement
    }

    if (expr != null) {
      val statement = builder.mark()
      val statementType: SyntaxElementType?
      if (incompleteDeclarationRestrictedTokenType != null) {
        builder.remapCurrentToken(incompleteDeclarationRestrictedTokenType)
        builder.advanceLexer()
        error(builder, message("expected.identifier"))
        statementType = JavaSyntaxElementType.DECLARATION_STATEMENT
      }
      else {
        myParser.expressionParser.parse(builder)
        statementType = JavaSyntaxElementType.EXPRESSION_STATEMENT
        semicolon(builder)
      }
      done(statement, statementType, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      return statement
    }

    return null
  }

  private val languageLevel: LanguageLevel
    get() = myParser.languageLevel

  private fun isStmtYieldToken(builder: SyntaxTreeBuilder, tokenType: SyntaxElementType?): Boolean {
    if (!(tokenType === JavaSyntaxTokenType.IDENTIFIER &&
          JavaKeywords.YIELD == builder.tokenText &&
          JavaFeature.SWITCH_EXPRESSION.isSufficient(this.languageLevel))
    ) {
      return false
    }
    // we prefer to parse it as yield stmt wherever possible (even in incomplete syntax)
    val maybeYieldStmt = builder.mark()
    builder.advanceLexer()
    val tokenAfterYield = builder.tokenType
    if (tokenAfterYield == null || YIELD_STMT_INDICATOR_TOKENS.contains(tokenAfterYield)) {
      maybeYieldStmt.rollbackTo()
      return true
    }
    if (tokenAfterYield === JavaSyntaxTokenType.PLUSPLUS || tokenAfterYield === JavaSyntaxTokenType.MINUSMINUS) {
      builder.advanceLexer()
      val isYieldStmt = builder.tokenType !== JavaSyntaxTokenType.SEMICOLON
      maybeYieldStmt.rollbackTo()
      return isYieldStmt
    }
    maybeYieldStmt.rollbackTo()
    return false
  }

  private fun parseIfStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    var stack: MutableList<SyntaxTreeBuilder.Marker>? = null
    var statement: SyntaxTreeBuilder.Marker
    while (true) {
      // replaced recursion with iteration plus stack to avoid huge call stack for extremely large else-if chains
      statement = builder.mark()
      builder.advanceLexer()

      if (parseExprInParenth(builder)) {
        if (parseStatement(builder) == null) {
          error(builder, message("expected.statement"))
        }
        else if (builder.expect(JavaSyntaxTokenType.ELSE_KEYWORD)) {
          if (builder.tokenType === JavaSyntaxTokenType.IF_KEYWORD) {
            if (stack == null) stack = mutableListOf()
            stack.add(statement)
            continue
          }
          else if (parseStatement(builder) == null) {
            error(builder, message("expected.statement"))
          }
        }
      }
      break
    }
    done(statement, JavaSyntaxElementType.IF_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    if (stack != null) {
      for (i in stack.indices.reversed()) {
        statement = stack[i]
        done(statement, JavaSyntaxElementType.IF_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      }
    }
    return statement
  }

  private fun parseWhileStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    return parseExprInParenthWithBlock(builder, JavaSyntaxElementType.WHILE_STATEMENT, false)
  }


  @Contract(pure = true)
  private fun isRecordPatternInForEach(builder: SyntaxTreeBuilder): Boolean {
    val patternStart = myParser.patternParser.preParsePattern(builder) ?: return false
    if (builder.tokenType !== JavaSyntaxTokenType.LPARENTH) {
      patternStart.rollbackTo()
      return false
    }
    builder.advanceLexer()

    // we must distinguish a record pattern from method call in for (foo();;)
    var parenBalance = 1
    while (true) {
      val current = builder.tokenType
      if (current == null) {
        patternStart.rollbackTo()
        return false
      }
      if (current === JavaSyntaxTokenType.LPARENTH) {
        parenBalance++
      }
      if (current === JavaSyntaxTokenType.RPARENTH) {
        parenBalance--
        if (parenBalance == 0) {
          break
        }
      }
      builder.advanceLexer()
    }
    builder.advanceLexer()
    val isRecordPattern = builder.tokenType !== JavaSyntaxTokenType.SEMICOLON && builder.tokenType !== JavaSyntaxTokenType.DOT
    patternStart.rollbackTo()
    return isRecordPattern
  }

  private fun parseForStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    builder.advanceLexer()

    if (!builder.expect(JavaSyntaxTokenType.LPARENTH)) {
      error(builder, message("expected.lparen"))
      done(statement, JavaSyntaxElementType.FOR_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      return statement
    }

    if (isRecordPatternInForEach(builder)) {
      myParser.patternParser.parsePattern(builder)
      if (builder.tokenType === JavaSyntaxTokenType.COLON) {
        return parseForEachFromColon(builder, statement, JavaSyntaxElementType.FOREACH_PATTERN_STATEMENT)
      }
      error(builder, message("expected.colon"))
      // recovery: just skip everything until ')'
      while (true) {
        val tokenType = builder.tokenType
        if (tokenType == null) {
          done(statement, JavaSyntaxElementType.FOREACH_PATTERN_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
          return statement
        }
        if (tokenType === JavaSyntaxTokenType.RPARENTH) {
          return parserForEachFromRparenth(builder, statement, JavaSyntaxElementType.FOREACH_PATTERN_STATEMENT)
        }
        builder.advanceLexer()
      }
    }

    val afterParenth = builder.mark()
    val param = myParser.declarationParser.parseParameter(builder, false, false, true)
    if (param == null || exprType(param) !== JavaSyntaxElementType.PARAMETER || builder.tokenType !== JavaSyntaxTokenType.COLON) {
      afterParenth.rollbackTo()
      return parseForLoopFromInitializer(builder, statement)
    }
    else {
      afterParenth.drop()
      return parseForEachFromColon(builder, statement, JavaSyntaxElementType.FOREACH_STATEMENT)
    }
  }

  private fun parseForLoopFromInitializer(builder: SyntaxTreeBuilder, statement: SyntaxTreeBuilder.Marker): SyntaxTreeBuilder.Marker {
    if (parseStatement(builder) == null) {
      error(builder, message("expected.statement"))
      if (!builder.expect(JavaSyntaxTokenType.RPARENTH)) {
        done(statement, JavaSyntaxElementType.FOR_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
        return statement
      }
    }
    else {
      var missingSemicolon = false
      if (getLastToken(builder) !== JavaSyntaxTokenType.SEMICOLON) {
        missingSemicolon = !expectOrError(builder, JavaSyntaxTokenType.SEMICOLON, "expected.semicolon")
      }

      val expr = myParser.expressionParser.parse(builder)
      missingSemicolon = missingSemicolon and (expr == null)

      if (!builder.expect(JavaSyntaxTokenType.SEMICOLON)) {
        if (!missingSemicolon) {
          error(builder, message("expected.semicolon"))
        }
        if (!builder.expect(JavaSyntaxTokenType.RPARENTH)) {
          done(statement, JavaSyntaxElementType.FOR_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
          return statement
        }
      }
      else {
        parseForUpdateExpressions(builder)
        if (!builder.expect(JavaSyntaxTokenType.RPARENTH)) {
          error(builder, message("expected.rparen"))
          done(statement, JavaSyntaxElementType.FOR_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
          return statement
        }
      }
    }

    val bodyStatement = parseStatement(builder)
    if (bodyStatement == null) {
      error(builder, message("expected.statement"))
    }

    done(statement, JavaSyntaxElementType.FOR_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return statement
  }

  private fun parseForUpdateExpressions(builder: SyntaxTreeBuilder) {
    val expr = myParser.expressionParser.parse(builder)
    if (expr == null) return

    val expressionStatement: SyntaxTreeBuilder.Marker?
    if (builder.tokenType !== JavaSyntaxTokenType.COMMA) {
      expressionStatement = expr.precede()
      done(expressionStatement, JavaSyntaxElementType.EXPRESSION_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    }
    else {
      val expressionList = expr.precede()
      expressionStatement = expressionList.precede()

      do {
        builder.advanceLexer()
        val nextExpression = myParser.expressionParser.parse(builder)
        if (nextExpression == null) {
          error(builder, message("expected.expression"))
        }
      }
      while (builder.tokenType === JavaSyntaxTokenType.COMMA)

      done(expressionList, JavaSyntaxElementType.EXPRESSION_LIST, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      done(expressionStatement, JavaSyntaxElementType.EXPRESSION_LIST_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    }

    expressionStatement.setCustomEdgeTokenBinders(null, defaultRightBinder())
  }

  private fun parseForEachFromColon(
    builder: SyntaxTreeBuilder,
    statement: SyntaxTreeBuilder.Marker,
    foreachStatement: SyntaxElementType
  ): SyntaxTreeBuilder.Marker {
    builder.advanceLexer()

    if (myParser.expressionParser.parse(builder) == null) {
      error(builder, message("expected.expression"))
    }

    return parserForEachFromRparenth(builder, statement, foreachStatement)
  }

  private fun parserForEachFromRparenth(
    builder: SyntaxTreeBuilder,
    statement: SyntaxTreeBuilder.Marker,
    forEachType: SyntaxElementType
  ): SyntaxTreeBuilder.Marker {
    if (expectOrError(builder, JavaSyntaxTokenType.RPARENTH, "expected.rparen") && parseStatement(builder) == null) {
      error(builder, message("expected.statement"))
    }

    done(statement, forEachType, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return statement
  }

  private fun parseDoWhileStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    builder.advanceLexer()

    val body = parseStatement(builder)
    if (body == null) {
      error(builder, message("expected.statement"))
    }
    else if (!builder.expect(JavaSyntaxTokenType.WHILE_KEYWORD)) {
      error(builder, message("expected.while"))
    }
    else if (parseExprInParenth(builder)) {
      semicolon(builder)
    }

    done(statement, JavaSyntaxElementType.DO_WHILE_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return statement
  }

  private fun parseSwitchStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    return parseExprInParenthWithBlock(builder, JavaSyntaxElementType.SWITCH_STATEMENT, true)
  }

  /**
   * @return marker and whether it contains expression inside
   */
  @ApiStatus.Internal
  fun parseCaseLabel(builder: SyntaxTreeBuilder): Pair<SyntaxTreeBuilder.Marker?, Boolean?> {
    if (builder.tokenType === JavaSyntaxTokenType.DEFAULT_KEYWORD) {
      val defaultElement = builder.mark()
      builder.advanceLexer()
      done(defaultElement, JavaSyntaxElementType.DEFAULT_CASE_LABEL_ELEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      return Pair<SyntaxTreeBuilder.Marker?, Boolean?>(defaultElement, false)
    }
    if (myParser.patternParser.isPattern(builder)) {
      val pattern = myParser.patternParser.parsePattern(builder)
      return Pair<SyntaxTreeBuilder.Marker?, Boolean?>(pattern, false)
    }
    return Pair<SyntaxTreeBuilder.Marker?, Boolean?>(myParser.expressionParser.parseAssignmentForbiddingLambda(builder), true)
  }

  private fun parseSwitchLabelStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    val isCase = builder.tokenType === JavaSyntaxTokenType.CASE_KEYWORD
    builder.advanceLexer()

    if (isCase) {
      val patternsAllowed = JavaFeature.PATTERNS_IN_SWITCH.isSufficient(this.languageLevel)
      val list = builder.mark()
      do {
        val markerAndIsExpression = parseCaseLabel(builder)
        val caseLabel = markerAndIsExpression.first
        if (caseLabel == null) {
          error(builder, message(if (patternsAllowed) "expected.case.label.element" else "expected.expression"))
        }
      }
      while (builder.expect(JavaSyntaxTokenType.COMMA))
      done(list, JavaSyntaxElementType.CASE_LABEL_ELEMENT_LIST, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      parseGuard(builder)
    }

    if (builder.expect(JavaSyntaxTokenType.ARROW)) {
      val expr: SyntaxTreeBuilder.Marker?
      if (builder.tokenType === JavaSyntaxTokenType.LBRACE) {
        val body = builder.mark()
        parseCodeBlock(builder, true)
        body.done(JavaSyntaxElementType.BLOCK_STATEMENT)
        if (builder.tokenType === JavaSyntaxTokenType.SEMICOLON) {
          val mark = builder.mark()
          while (builder.tokenType === JavaSyntaxTokenType.SEMICOLON) builder.advanceLexer()
          mark.error(message("expected.switch.label"))
        }
      }
      else if (builder.tokenType === JavaSyntaxTokenType.THROW_KEYWORD) {
        parseThrowStatement(builder)
      }
      else if ((myParser.expressionParser.parse(builder).also { expr = it }) != null) {
        val body = expr!!.precede()
        semicolon(builder)
        body.done(JavaSyntaxElementType.EXPRESSION_STATEMENT)
      }
      else {
        error(builder, message("expected.switch.rule"))
        builder.expect(JavaSyntaxTokenType.SEMICOLON)
      }
      done(statement, JavaSyntaxElementType.SWITCH_LABELED_RULE, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    }
    else {
      expectOrError(builder, JavaSyntaxTokenType.COLON, "expected.colon.or.arrow")
      done(statement, JavaSyntaxElementType.SWITCH_LABEL_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    }

    return statement
  }

  private fun parseGuard(builder: SyntaxTreeBuilder) {
    if (builder.tokenType === JavaSyntaxTokenType.IDENTIFIER && JavaKeywords.WHEN == builder.tokenText) {
      builder.remapCurrentToken(JavaSyntaxTokenType.WHEN_KEYWORD)
      builder.advanceLexer()
      val guardingExpression = myParser.expressionParser.parseAssignmentForbiddingLambda(builder)
      if (guardingExpression == null) {
        error(builder, message("expected.expression"))
      }
    }
  }

  private fun parseBreakStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    builder.advanceLexer()
    builder.expect(JavaSyntaxTokenType.IDENTIFIER)
    semicolon(builder)
    done(statement, JavaSyntaxElementType.BREAK_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return statement
  }

  private fun parseYieldStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    builder.remapCurrentToken(JavaSyntaxTokenType.YIELD_KEYWORD)
    builder.advanceLexer()

    if (myParser.expressionParser.parse(builder) == null) {
      error(builder, message("expected.expression"))
    }
    else {
      semicolon(builder)
    }

    done(statement, JavaSyntaxElementType.YIELD_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return statement
  }

  private fun parseContinueStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    builder.advanceLexer()
    builder.expect(JavaSyntaxTokenType.IDENTIFIER)
    semicolon(builder)
    done(statement, JavaSyntaxElementType.CONTINUE_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return statement
  }

  private fun parseReturnStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    builder.advanceLexer()
    myParser.expressionParser.parse(builder)
    semicolon(builder)
    done(statement, JavaSyntaxElementType.RETURN_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return statement
  }

  private fun parseThrowStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    builder.advanceLexer()

    if (myParser.expressionParser.parse(builder) == null) {
      error(builder, message("expected.expression"))
    }
    else {
      semicolon(builder)
    }

    done(statement, JavaSyntaxElementType.THROW_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return statement
  }

  private fun parseSynchronizedStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    return parseExprInParenthWithBlock(builder, JavaSyntaxElementType.SYNCHRONIZED_STATEMENT, true)
  }

  private fun parseTryStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    builder.advanceLexer()

    val hasResourceList = builder.tokenType === JavaSyntaxTokenType.LPARENTH
    if (hasResourceList) {
      myParser.declarationParser.parseResourceList(builder)
    }

    val tryBlock = parseCodeBlock(builder, true)
    if (tryBlock == null) {
      error(builder, message("expected.lbrace"))
    }
    else if (!hasResourceList && !TRY_CLOSERS_SET.contains(builder.tokenType)) {
      error(builder, message("expected.catch.or.finally"))
    }
    else {
      while (builder.tokenType === JavaSyntaxTokenType.CATCH_KEYWORD) {
        if (!parseCatchBlock(builder)) break
      }

      if (builder.expect(JavaSyntaxTokenType.FINALLY_KEYWORD)) {
        val finallyBlock = parseCodeBlock(builder, true)
        if (finallyBlock == null) {
          error(builder, message("expected.lbrace"))
        }
      }
    }

    done(statement, JavaSyntaxElementType.TRY_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return statement
  }

  fun parseCatchBlock(builder: SyntaxTreeBuilder): Boolean {
    assert(builder.tokenType === JavaSyntaxTokenType.CATCH_KEYWORD) { builder.tokenType!! }
    val section = builder.mark()
    builder.advanceLexer()

    if (!builder.expect(JavaSyntaxTokenType.LPARENTH)) {
      error(builder, message("expected.lparen"))
      done(section, JavaSyntaxElementType.CATCH_SECTION, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      return false
    }

    val param = myParser.declarationParser.parseParameter(builder, false, true, false)
    if (param == null) {
      error(builder, message("expected.parameter"))
    }

    if (!builder.expect(JavaSyntaxTokenType.RPARENTH)) {
      error(builder, message("expected.rparen"))
      done(section, JavaSyntaxElementType.CATCH_SECTION, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      return false
    }

    val body = parseCodeBlock(builder, true)
    if (body == null) {
      error(builder, message("expected.lbrace"))
      done(section, JavaSyntaxElementType.CATCH_SECTION, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
      return false
    }

    done(section, JavaSyntaxElementType.CATCH_SECTION, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return true
  }

  private fun parseAssertStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    builder.advanceLexer()

    if (myParser.expressionParser.parse(builder) == null) {
      error(builder, message("expected.boolean.expression"))
    }
    else if (builder.expect(JavaSyntaxTokenType.COLON) && myParser.expressionParser.parse(builder) == null) {
      error(builder, message("expected.expression"))
    }
    else {
      semicolon(builder)
    }

    done(statement, JavaSyntaxElementType.ASSERT_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return statement
  }

  private fun parseBlockStatement(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    parseCodeBlock(builder, true)
    done(statement, JavaSyntaxElementType.BLOCK_STATEMENT, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return statement
  }

  fun parseExprInParenthWithBlock(builder: SyntaxTreeBuilder, type: SyntaxElementType, block: Boolean): SyntaxTreeBuilder.Marker {
    val statement = builder.mark()
    builder.advanceLexer()

    if (parseExprInParenth(builder)) {
      val body = if (block) parseCodeBlock(builder, true) else parseStatement(builder)
      if (body == null) {
        error(builder, message(if (block) "expected.lbrace" else "expected.statement"))
      }
    }

    done(statement, type, myParser.languageLevel, myWhiteSpaceAndCommentSetHolder)
    return statement
  }

  private fun parseExprInParenth(builder: SyntaxTreeBuilder): Boolean {
    if (!builder.expect(JavaSyntaxTokenType.LPARENTH)) {
      error(builder, message("expected.lparen"))
      return false
    }

    val beforeExpr = builder.mark()
    val expr = myParser.expressionParser.parse(builder)
    if (expr == null || builder.tokenType === JavaSyntaxTokenType.SEMICOLON) {
      beforeExpr.rollbackTo()
      error(builder, message("expected.expression"))
      if (builder.tokenType !== JavaSyntaxTokenType.RPARENTH) {
        return false
      }
    }
    else {
      beforeExpr.drop()
      if (builder.tokenType !== JavaSyntaxTokenType.RPARENTH) {
        error(builder, message("expected.rparen"))
        return false
      }
    }

    builder.advanceLexer()
    return true
  }

  private fun skipQualifiedName(builder: SyntaxTreeBuilder) {
    if (!builder.expect(JavaSyntaxTokenType.IDENTIFIER)) return
    while (builder.tokenType === JavaSyntaxTokenType.DOT && builder.lookAhead(1) === JavaSyntaxTokenType.IDENTIFIER) {
      builder.advance(2)
    }
  }

  private fun getLastToken(builder: SyntaxTreeBuilder): SyntaxElementType? {
    var token: SyntaxElementType?
    var offset = -1
    while (SyntaxElementTypes.JAVA_COMMENT_OR_WHITESPACE_BIT_SET.contains((builder.rawLookup(offset).also { token = it }))) offset--
    return token
  }
}

private val YIELD_STMT_INDICATOR_TOKENS: SyntaxElementTypeSet = syntaxElementTypeSetOf(
  JavaSyntaxTokenType.PLUS, JavaSyntaxTokenType.MINUS, JavaSyntaxTokenType.EXCL, JavaSyntaxTokenType.TILDE,
  JavaSyntaxTokenType.SUPER_KEYWORD, JavaSyntaxTokenType.THIS_KEYWORD,

  JavaSyntaxTokenType.TRUE_KEYWORD, JavaSyntaxTokenType.FALSE_KEYWORD, JavaSyntaxTokenType.NULL_KEYWORD,

  JavaSyntaxTokenType.STRING_LITERAL, JavaSyntaxTokenType.INTEGER_LITERAL, JavaSyntaxTokenType.DOUBLE_LITERAL,
  JavaSyntaxTokenType.FLOAT_LITERAL, JavaSyntaxTokenType.LONG_LITERAL, JavaSyntaxTokenType.CHARACTER_LITERAL,
  JavaSyntaxTokenType.TEXT_BLOCK_LITERAL,

  JavaSyntaxTokenType.IDENTIFIER, JavaSyntaxTokenType.SWITCH_KEYWORD, JavaSyntaxTokenType.NEW_KEYWORD,

  JavaSyntaxTokenType.LPARENTH,  // recovery

  JavaSyntaxTokenType.RBRACE, JavaSyntaxTokenType.SEMICOLON, JavaSyntaxTokenType.CASE_KEYWORD
)

private val TRY_CLOSERS_SET: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaSyntaxTokenType.CATCH_KEYWORD, JavaSyntaxTokenType.FINALLY_KEYWORD)
