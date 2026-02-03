// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.parser

import com.intellij.java.syntax.JavaSyntaxBundle
import com.intellij.java.syntax.JavaSyntaxBundle.message
import com.intellij.java.syntax.element.JavaSyntaxElementType
import com.intellij.java.syntax.element.JavaSyntaxTokenType
import com.intellij.java.syntax.element.SyntaxElementTypes
import com.intellij.java.syntax.element.SyntaxElementTypes.MODIFIER_BIT_SET
import com.intellij.java.syntax.element.SyntaxElementTypes.PRIMITIVE_TYPE_BIT_SET
import com.intellij.java.syntax.parser.ExpressionParser.Companion.FORBID_LAMBDA_MASK
import com.intellij.java.syntax.parser.JavaParserUtil.done
import com.intellij.java.syntax.parser.JavaParserUtil.emptyElement
import com.intellij.java.syntax.parser.JavaParserUtil.error
import com.intellij.java.syntax.parser.JavaParserUtil.expectOrError
import com.intellij.java.syntax.parser.JavaParserUtil.exprType
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.element.SyntaxTokenTypes.BAD_CHARACTER
import com.intellij.platform.syntax.parser.SyntaxTreeBuilder
import com.intellij.platform.syntax.parser.WhitespacesBinders.greedyRightBinder
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.advance
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.expect
import com.intellij.util.BitUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.PropertyKey

//suppress to be clear, what type is used
@ApiStatus.Experimental
class PrattExpressionParser(private val myParser: JavaParser) {
  private val ourInfixParsers: MutableMap<SyntaxElementType?, ParserData?>

  init {
    ourInfixParsers = HashMap<SyntaxElementType?, ParserData?>()
    val assignmentParser = AssignmentParser()
    val polyExprParser = PolyExprParser()
    val instanceofParser = InstanceofParser()
    val conditionalExprParser = ConditionalExprParser()

    for (type in listOf(JavaSyntaxTokenType.EQ, JavaSyntaxTokenType.ASTERISKEQ, JavaSyntaxTokenType.DIVEQ,
                                           JavaSyntaxTokenType.PERCEQ, JavaSyntaxTokenType.PLUSEQ, JavaSyntaxTokenType.MINUSEQ,
                                           JavaSyntaxTokenType.LTLTEQ, JavaSyntaxTokenType.GTGTEQ, JavaSyntaxTokenType.GTGTGTEQ,
                                           JavaSyntaxTokenType.ANDEQ, JavaSyntaxTokenType.OREQ, JavaSyntaxTokenType.XOREQ)) {
      ourInfixParsers.put(type, ParserData(ASSIGNMENT_PRECEDENCE, assignmentParser))
    }
    for (type in listOf(JavaSyntaxTokenType.PLUS, JavaSyntaxTokenType.MINUS)) {
      ourInfixParsers.put(type, ParserData(ADDITIVE_PRECEDENCE, polyExprParser))
    }
    for (type in listOf(JavaSyntaxTokenType.DIV, JavaSyntaxTokenType.ASTERISK, JavaSyntaxTokenType.PERC)) {
      ourInfixParsers.put(type, ParserData(MULTIPLICATION_PRECEDENCE, polyExprParser))
    }
    for (type in listOf(JavaSyntaxTokenType.LTLT, JavaSyntaxTokenType.GTGT, JavaSyntaxTokenType.GTGTGT)) {
      ourInfixParsers.put(type, ParserData(SHIFT_PRECEDENCE, polyExprParser))
    }
    for (type in listOf(JavaSyntaxTokenType.LT, JavaSyntaxTokenType.GT, JavaSyntaxTokenType.LE, JavaSyntaxTokenType.GE)) {
      ourInfixParsers.put(type, ParserData(COMPARISON_AND_INSTANCEOF_PRECEDENCE, polyExprParser))
    }
    ourInfixParsers.put(JavaSyntaxTokenType.INSTANCEOF_KEYWORD, ParserData(COMPARISON_AND_INSTANCEOF_PRECEDENCE, instanceofParser))
    for (type in listOf(JavaSyntaxTokenType.EQEQ, JavaSyntaxTokenType.NE)) {
      ourInfixParsers.put(type, ParserData(EQUALITY_PRECEDENCE, polyExprParser))
    }
    ourInfixParsers.put(JavaSyntaxTokenType.OR, ParserData(BITWISE_OR_PRECEDENCE, polyExprParser))
    ourInfixParsers.put(JavaSyntaxTokenType.AND, ParserData(BITWISE_AND_PRECEDENCE, polyExprParser))
    ourInfixParsers.put(JavaSyntaxTokenType.XOR, ParserData(BITWISE_XOR_PRECEDENCE, polyExprParser))
    ourInfixParsers.put(JavaSyntaxTokenType.ANDAND, ParserData(LOGICAL_AND_PRECEDENCE, polyExprParser))
    ourInfixParsers.put(JavaSyntaxTokenType.OROR, ParserData(LOGICAL_OR_PRECEDENCE, polyExprParser))
    ourInfixParsers.put(JavaSyntaxTokenType.QUEST, ParserData(CONDITIONAL_EXPR_PRECEDENCE, conditionalExprParser))
  }

  fun parse(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    return tryParseWithPrecedenceAtMost(builder, ASSIGNMENT_PRECEDENCE, 0)
  }

  fun parse(builder: SyntaxTreeBuilder, mode: Int): SyntaxTreeBuilder.Marker? {
    return tryParseWithPrecedenceAtMost(builder, ASSIGNMENT_PRECEDENCE, mode)
  }

  /**
   * Traditional Pratt parser for infix expressions.
   * If marker is null it is guaranteed that nothing is parsed
   */
  fun tryParseWithPrecedenceAtMost(builder: SyntaxTreeBuilder, maxPrecedence: Int, mode: Int): SyntaxTreeBuilder.Marker? {
    var lhs = parseUnary(builder, mode) ?: return null

    while (true) {
      val type = getBinOpToken(builder) ?: break
      val data = ourInfixParsers[type] ?: break
      val opPrecedence = data.myPrecedence
      if (maxPrecedence < opPrecedence) {
        break
      }
      val beforeLhs = lhs.precede()
      data.myParser.parse(this, builder, beforeLhs, type, opPrecedence, mode)
      lhs = beforeLhs
    }
    return lhs
  }

  private fun parseUnary(builder: SyntaxTreeBuilder, mode: Int): SyntaxTreeBuilder.Marker? {
    val tokenType = builder.tokenType

    if (PREFIX_OPS.contains(tokenType)) {
      val unary = builder.mark()
      builder.advanceLexer()

      val operand = parseUnary(builder, mode)
      if (operand == null) {
        error(builder, message("expected.expression"))
      }

      unary.done(JavaSyntaxElementType.PREFIX_EXPRESSION)
      return unary
    }
    else if (tokenType === JavaSyntaxTokenType.LPARENTH) {
      val typeCast = builder.mark()
      builder.advanceLexer()

      val typeInfo = myParser.referenceParser.parseTypeInfo(
        builder, ReferenceParser.EAT_LAST_DOT or
          ReferenceParser.WILDCARD or
          ReferenceParser.CONJUNCTIONS or
          ReferenceParser.INCOMPLETE_ANNO)
      if (typeInfo == null || !builder.expect(JavaSyntaxTokenType.RPARENTH)) {
        typeCast.rollbackTo()
        return parsePostfix(builder, mode)
      }

      if (PREF_ARITHMETIC_OPS.contains(builder.tokenType) && !typeInfo.isPrimitive) {
        typeCast.rollbackTo()
        return parsePostfix(builder, mode)
      }

      val expr = parseUnary(builder, mode)
      if (expr == null) {
        if (!typeInfo.isParameterized) {  // cannot parse correct parenthesized expression after correct parameterized type
          typeCast.rollbackTo()
          return parsePostfix(builder, mode)
        }
        else {
          error(builder, message("expected.expression"))
        }
      }

      typeCast.done(JavaSyntaxElementType.TYPE_CAST_EXPRESSION)
      return typeCast
    }
    else if (tokenType === JavaSyntaxTokenType.SWITCH_KEYWORD) {
      return myParser.statementParser.parseExprInParenthWithBlock(builder, JavaSyntaxElementType.SWITCH_EXPRESSION, true)
    }
    else {
      return parsePostfix(builder, mode)
    }
  }

  private fun parsePostfix(builder: SyntaxTreeBuilder, mode: Int): SyntaxTreeBuilder.Marker? {
    var operand = parsePrimary(builder, null, -1, mode) ?: return null

    while (POSTFIX_OPS.contains(builder.tokenType)) {
      val postfix = operand.precede()
      builder.advanceLexer()
      postfix.done(JavaSyntaxElementType.POSTFIX_EXPRESSION)
      operand = postfix
    }

    return operand
  }

  private fun parsePrimary(builder: SyntaxTreeBuilder, breakPoint: BreakPoint?, breakOffset: Int, mode: Int): SyntaxTreeBuilder.Marker? {
    var startMarker = builder.mark()

    var expr = parsePrimaryExpressionStart(builder, mode) ?: run {
      startMarker.drop()
      return null
    }

    while (true) {
      val tokenType = builder.tokenType
      if (tokenType === JavaSyntaxTokenType.DOT) {
        val dotPos = builder.mark()
        val dotOffset = builder.currentOffset
        builder.advanceLexer()

        var dotTokenType = builder.tokenType
        if (dotTokenType === JavaSyntaxTokenType.AT) {
          myParser.declarationParser.parseAnnotations(builder)
          dotTokenType = builder.tokenType
        }

        if (dotTokenType === JavaSyntaxTokenType.CLASS_KEYWORD && exprType(expr) === JavaSyntaxElementType.REFERENCE_EXPRESSION) {
          if (breakPoint == BreakPoint.P1 && builder.currentOffset == breakOffset) {
            error(builder, message("expected.identifier"))
            SyntaxBuilderUtil.drop(startMarker, dotPos)
            return expr
          }

          val copy = startMarker.precede()
          val offset = builder.currentOffset
          startMarker.rollbackTo()

          val classObjAccess = parseClassAccessOrMethodReference(builder)
          if (classObjAccess == null || builder.currentOffset < offset) {
            copy.rollbackTo()
            return parsePrimary(builder, BreakPoint.P1, offset, mode)
          }

          startMarker = copy
          expr = classObjAccess
        }
        else if (dotTokenType === JavaSyntaxTokenType.NEW_KEYWORD) {
          dotPos.drop()
          expr = parseNew(builder, expr)
        }
        else if (dotTokenType === JavaSyntaxTokenType.SUPER_KEYWORD && builder.lookAhead(1) === JavaSyntaxTokenType.LPARENTH) {
          dotPos.drop()
          val refExpr = expr.precede()
          builder.mark().done(JavaSyntaxElementType.REFERENCE_PARAMETER_LIST)
          builder.advanceLexer()
          refExpr.done(JavaSyntaxElementType.REFERENCE_EXPRESSION)
          expr = refExpr
        }
        else if (dotTokenType === JavaSyntaxTokenType.STRING_TEMPLATE_BEGIN || dotTokenType === JavaSyntaxTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) {
          dotPos.drop()
          expr = parseStringTemplate(builder, expr, dotTokenType === JavaSyntaxTokenType.TEXT_BLOCK_TEMPLATE_BEGIN)
        }
        else if (dotTokenType === JavaSyntaxTokenType.STRING_LITERAL || dotTokenType === JavaSyntaxTokenType.TEXT_BLOCK_LITERAL) {
          dotPos.drop()
          val templateExpression = expr.precede()
          val literal = builder.mark()
          builder.advanceLexer()
          literal.done(JavaSyntaxElementType.LITERAL_EXPRESSION)
          templateExpression.done(JavaSyntaxElementType.TEMPLATE_EXPRESSION)
          expr = templateExpression
        }
        else if (THIS_OR_SUPER.contains(dotTokenType) && exprType(expr) === JavaSyntaxElementType.REFERENCE_EXPRESSION) {
          if (breakPoint == BreakPoint.P2 && builder.currentOffset == breakOffset) {
            dotPos.rollbackTo()
            startMarker.drop()
            return expr
          }

          val copy = startMarker.precede()
          val offset = builder.currentOffset
          startMarker.rollbackTo()

          val ref = myParser.referenceParser.parseJavaCodeReference(builder, false, true, false, false)
          if (ref == null || builder.tokenType !== JavaSyntaxTokenType.DOT || builder.currentOffset != dotOffset) {
            copy.rollbackTo()
            return parsePrimary(builder, BreakPoint.P2, offset, mode)
          }
          builder.advanceLexer()

          if (builder.tokenType !== dotTokenType) {
            copy.rollbackTo()
            return parsePrimary(builder, BreakPoint.P2, offset, mode)
          }
          builder.advanceLexer()

          startMarker = copy
          expr = ref.precede()
          expr.done(if (dotTokenType === JavaSyntaxTokenType.THIS_KEYWORD)
                      JavaSyntaxElementType.THIS_EXPRESSION
                    else
                      JavaSyntaxElementType.SUPER_EXPRESSION)
        }
        else {
          val refExpr = expr.precede()

          myParser.referenceParser.parseReferenceParameterList(builder, false, false)

          if (!builder.expect(ID_OR_SUPER)) {
            dotPos.rollbackTo()
            builder.advanceLexer()
            myParser.referenceParser.parseReferenceParameterList(builder, false, false)
            error(builder, message("expected.identifier"))
            refExpr.done(JavaSyntaxElementType.REFERENCE_EXPRESSION)
            startMarker.drop()
            return refExpr
          }

          dotPos.drop()
          refExpr.done(JavaSyntaxElementType.REFERENCE_EXPRESSION)
          expr = refExpr
        }
      }
      else if (tokenType === JavaSyntaxTokenType.LPARENTH) {
        if (exprType(expr) !== JavaSyntaxElementType.REFERENCE_EXPRESSION) {
          startMarker.drop()
          return expr
        }

        val callExpr = expr.precede()
        parseArgumentList(builder)
        callExpr.done(JavaSyntaxElementType.METHOD_CALL_EXPRESSION)
        expr = callExpr
      }
      else if (tokenType === JavaSyntaxTokenType.LBRACKET) {
        if (breakPoint == BreakPoint.P4) {
          startMarker.drop()
          return expr
        }

        builder.advanceLexer()

        if (builder.tokenType === JavaSyntaxTokenType.RBRACKET &&
            exprType(expr) === JavaSyntaxElementType.REFERENCE_EXPRESSION
        ) {
          val pos = builder.currentOffset
          val copy = startMarker.precede()
          startMarker.rollbackTo()

          val classObjAccess = parseClassAccessOrMethodReference(builder)
          if (classObjAccess == null || builder.currentOffset <= pos) {
            copy.rollbackTo()
            return parsePrimary(builder, BreakPoint.P4, -1, mode)
          }

          startMarker = copy
          expr = classObjAccess
        }
        else {
          val arrayAccess = expr.precede()

          val index = parse(builder, mode)
          if (index == null) {
            error(builder, message("expected.expression"))
            arrayAccess.done(JavaSyntaxElementType.ARRAY_ACCESS_EXPRESSION)
            startMarker.drop()
            return arrayAccess
          }

          if (builder.tokenType !== JavaSyntaxTokenType.RBRACKET) {
            error(builder, message("expected.rbracket"))
            arrayAccess.done(JavaSyntaxElementType.ARRAY_ACCESS_EXPRESSION)
            startMarker.drop()
            return arrayAccess
          }
          builder.advanceLexer()

          arrayAccess.done(JavaSyntaxElementType.ARRAY_ACCESS_EXPRESSION)
          expr = arrayAccess
        }
      }
      else if (tokenType === JavaSyntaxTokenType.DOUBLE_COLON) {
        return parseMethodReference(builder, startMarker)
      }
      else {
        startMarker.drop()
        return expr
      }
    }
  }

  private fun parsePrimaryExpressionStart(builder: SyntaxTreeBuilder, mode: Int): SyntaxTreeBuilder.Marker? {
    var tokenType = builder.tokenType

    if (tokenType === JavaSyntaxTokenType.TEXT_BLOCK_TEMPLATE_BEGIN || tokenType === JavaSyntaxTokenType.STRING_TEMPLATE_BEGIN) {
      return parseStringTemplate(builder, null, tokenType === JavaSyntaxTokenType.TEXT_BLOCK_TEMPLATE_BEGIN)
    }

    if (SyntaxElementTypes.ALL_LITERALS.contains(tokenType)) {
      val literal = builder.mark()
      builder.advanceLexer()
      literal.done(JavaSyntaxElementType.LITERAL_EXPRESSION)
      return literal
    }

    if (tokenType === JavaSyntaxTokenType.LBRACE) {
      return parseArrayInitializer(builder)
    }

    if (tokenType === JavaSyntaxTokenType.NEW_KEYWORD) {
      return parseNew(builder, null)
    }

    if (tokenType === JavaSyntaxTokenType.LPARENTH) {
      if (!BitUtil.isSet(mode, FORBID_LAMBDA_MASK)) {
        val lambda = parseLambdaAfterParenth(builder)
        if (lambda != null) {
          return lambda
        }
      }

      val parenth = builder.mark()
      builder.advanceLexer()

      val inner = parse(builder, mode)
      if (inner == null) {
        error(builder, message("expected.expression"))
      }

      if (!builder.expect(JavaSyntaxTokenType.RPARENTH) && inner != null) {
        error(builder, message("expected.rparen"))
      }

      parenth.done(JavaSyntaxElementType.PARENTH_EXPRESSION)
      return parenth
    }

    if (TYPE_START.contains(tokenType)) {
      val mark = builder.mark()

      val typeInfo = myParser.referenceParser.parseTypeInfo(builder, 0)
      if (typeInfo != null) {
        val optionalClassKeyword = typeInfo.isPrimitive || typeInfo.isArray
        if (optionalClassKeyword || !typeInfo.hasErrors && typeInfo.isParameterized) {
          val result = parseClassAccessOrMethodReference(builder, mark, optionalClassKeyword)
          if (result != null) {
            return result
          }
        }
      }

      mark.rollbackTo()
    }

    var annotation: SyntaxTreeBuilder.Marker? = null
    if (tokenType === JavaSyntaxTokenType.AT) {
      annotation = myParser.declarationParser.parseAnnotations(builder)
      tokenType = builder.tokenType
    }

    if (tokenType === JavaSyntaxTokenType.VAR_KEYWORD) {
      builder.remapCurrentToken(JavaSyntaxTokenType.IDENTIFIER.also { tokenType = it })
    }
    if (tokenType === JavaSyntaxTokenType.IDENTIFIER) {
      if (!BitUtil.isSet(mode, FORBID_LAMBDA_MASK) && builder.lookAhead(1) === JavaSyntaxTokenType.ARROW) {
        return parseLambdaExpression(builder, false)
      }

      val refExpr: SyntaxTreeBuilder.Marker
      if (annotation != null) {
        val refParam = annotation.precede()
        refParam.doneBefore(JavaSyntaxElementType.REFERENCE_PARAMETER_LIST, annotation)
        refExpr = refParam.precede()
      }
      else {
        refExpr = builder.mark()
        builder.mark().done(JavaSyntaxElementType.REFERENCE_PARAMETER_LIST)
      }

      builder.advanceLexer()
      refExpr.done(JavaSyntaxElementType.REFERENCE_EXPRESSION)
      return refExpr
    }

    if (annotation != null) {
      annotation.rollbackTo()
      tokenType = builder.tokenType
    }

    var expr: SyntaxTreeBuilder.Marker? = null
    if (tokenType === JavaSyntaxTokenType.LT) {
      expr = builder.mark()

      if (!myParser.referenceParser.parseReferenceParameterList(builder, false, false)) {
        expr.rollbackTo()
        return null
      }

      tokenType = builder.tokenType
      if (!THIS_OR_SUPER.contains(tokenType)) {
        expr.rollbackTo()
        return null
      }
    }

    if (THIS_OR_SUPER.contains(tokenType)) {
      if (expr == null) {
        expr = builder.mark()
        builder.mark().done(JavaSyntaxElementType.REFERENCE_PARAMETER_LIST)
      }
      builder.advanceLexer()
      expr.done(if (builder.tokenType === JavaSyntaxTokenType.LPARENTH)
                  JavaSyntaxElementType.REFERENCE_EXPRESSION
                else
                  if (tokenType === JavaSyntaxTokenType.THIS_KEYWORD)
                    JavaSyntaxElementType.THIS_EXPRESSION
                  else
                    JavaSyntaxElementType.SUPER_EXPRESSION)
      return expr
    }

    return null
  }

  private fun parseClassAccessOrMethodReference(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val expr = builder.mark()

    val primitive: Boolean = PRIMITIVE_TYPE_BIT_SET.contains(builder.tokenType)
    if (myParser.referenceParser.parseType(builder, 0) === null) {
      expr.drop()
      return null
    }

    val result = parseClassAccessOrMethodReference(builder, expr, primitive)
    if (result == null) expr.rollbackTo()
    return result
  }

  private fun parseClassAccessOrMethodReference(
    builder: SyntaxTreeBuilder,
    expr: SyntaxTreeBuilder.Marker,
    optionalClassKeyword: Boolean
  ): SyntaxTreeBuilder.Marker? {
    val tokenType = builder.tokenType
    if (tokenType === JavaSyntaxTokenType.DOT) {
      return parseClassObjectAccess(builder, expr, optionalClassKeyword)
    }
    else if (tokenType === JavaSyntaxTokenType.DOUBLE_COLON) {
      return parseMethodReference(builder, expr)
    }

    return null
  }

  private fun parseMethodReference(builder: SyntaxTreeBuilder, start: SyntaxTreeBuilder.Marker): SyntaxTreeBuilder.Marker {
    builder.advanceLexer()

    myParser.referenceParser.parseReferenceParameterList(builder, false, false)

    if (!builder.expect(JavaSyntaxTokenType.IDENTIFIER) && !builder.expect(JavaSyntaxTokenType.NEW_KEYWORD)
    ) {
      error(builder, message("expected.identifier"))
    }

    start.done(JavaSyntaxElementType.METHOD_REF_EXPRESSION)
    return start
  }

  private fun parseStringTemplate(
    builder: SyntaxTreeBuilder,
    start: SyntaxTreeBuilder.Marker?,
    textBlock: Boolean
  ): SyntaxTreeBuilder.Marker {
    val templateExpression = start?.precede() ?: builder.mark()
    val template = builder.mark()
    var tokenType: SyntaxElementType?
    do {
      builder.advanceLexer()
      tokenType = builder.tokenType
      if (if (textBlock)
          tokenType === JavaSyntaxTokenType.TEXT_BLOCK_TEMPLATE_MID || tokenType === JavaSyntaxTokenType.TEXT_BLOCK_TEMPLATE_END
        else
          tokenType === JavaSyntaxTokenType.STRING_TEMPLATE_MID || tokenType === JavaSyntaxTokenType.STRING_TEMPLATE_END
      ) {
        emptyExpression(builder)
      }
      else {
        parse(builder)
        tokenType = builder.tokenType
      }
    }
    while (if (textBlock) tokenType === JavaSyntaxTokenType.TEXT_BLOCK_TEMPLATE_MID else tokenType === JavaSyntaxTokenType.STRING_TEMPLATE_MID)
    if (if (textBlock) tokenType !== JavaSyntaxTokenType.TEXT_BLOCK_TEMPLATE_END else tokenType !== JavaSyntaxTokenType.STRING_TEMPLATE_END) {
      builder.error(message("expected.template.fragment"))
    }
    else {
      builder.advanceLexer()
    }
    template.done(JavaSyntaxElementType.TEMPLATE)
    templateExpression.done(JavaSyntaxElementType.TEMPLATE_EXPRESSION)
    return templateExpression
  }

  private fun parseNew(builder: SyntaxTreeBuilder, start: SyntaxTreeBuilder.Marker?): SyntaxTreeBuilder.Marker {
    val newExpr = (start?.precede() ?: builder.mark())
    builder.advanceLexer()

    myParser.referenceParser.parseReferenceParameterList(builder, false, true)

    val refOrType: SyntaxTreeBuilder.Marker?
    var anno = myParser.declarationParser.parseAnnotations(builder)
    val tokenType = builder.tokenType
    if (tokenType === JavaSyntaxTokenType.IDENTIFIER) {
      anno?.rollbackTo()
      refOrType = myParser.referenceParser.parseJavaCodeReference(builder, true, true, true, true)
      if (refOrType == null) {
        error(builder, message("expected.identifier"))
        newExpr.done(JavaSyntaxElementType.NEW_EXPRESSION)
        return newExpr
      }
    }
    else if (PRIMITIVE_TYPE_BIT_SET.contains(tokenType)) {
      refOrType = null
      builder.advanceLexer()
    }
    else {
      error(builder, message("expected.identifier"))
      newExpr.done(JavaSyntaxElementType.NEW_EXPRESSION)
      return newExpr
    }

    if (refOrType != null && builder.tokenType === JavaSyntaxTokenType.LPARENTH) {
      parseArgumentList(builder)
      if (builder.tokenType === JavaSyntaxTokenType.LBRACE) {
        val classElement = refOrType.precede()
        myParser.declarationParser.parseClassBodyWithBraces(builder, false, false)
        classElement.done(JavaSyntaxElementType.ANONYMOUS_CLASS)
      }
      newExpr.done(JavaSyntaxElementType.NEW_EXPRESSION)
      return newExpr
    }

    anno = myParser.declarationParser.parseAnnotations(builder)

    if (builder.tokenType !== JavaSyntaxTokenType.LBRACKET) {
      anno?.rollbackTo()
      error(builder, message(if (refOrType == null) "expected.lbracket" else "expected.lparen.or.lbracket"))
      newExpr.done(JavaSyntaxElementType.NEW_EXPRESSION)
      return newExpr
    }

    var bracketCount = 0
    var dimCount = 0
    while (true) {
      anno = myParser.declarationParser.parseAnnotations(builder)

      if (builder.tokenType !== JavaSyntaxTokenType.LBRACKET) {
        anno?.rollbackTo()
        break
      }
      builder.advanceLexer()

      if (bracketCount == dimCount) {
        val dimExpr = parse(builder, 0)
        if (dimExpr != null) {
          dimCount++
        }
      }
      bracketCount++

      if (!expectOrError(builder, JavaSyntaxTokenType.RBRACKET, "expected.rbracket")) {
        newExpr.done(JavaSyntaxElementType.NEW_EXPRESSION)
        return newExpr
      }
    }

    if (dimCount == 0) {
      if (builder.tokenType === JavaSyntaxTokenType.LBRACE) {
        parseArrayInitializer(builder)
      }
      else {
        error(builder, message("expected.array.initializer"))
      }
    }

    newExpr.done(JavaSyntaxElementType.NEW_EXPRESSION)
    return newExpr
  }

  private fun parseArrayInitializer(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    return parseArrayInitializer(builder = builder,
                                 type = JavaSyntaxElementType.ARRAY_INITIALIZER_EXPRESSION,
                                 elementParser = { builder -> this.parse(builder) },
                                 missingElementKey = "expected.expression")
  }

  fun parseArrayInitializer(
    builder: SyntaxTreeBuilder,
    type: SyntaxElementType,
    elementParser: (SyntaxTreeBuilder) -> SyntaxTreeBuilder.Marker?,
    missingElementKey: @PropertyKey(resourceBundle = JavaSyntaxBundle.BUNDLE) String
  ): SyntaxTreeBuilder.Marker {
    val arrayInit = builder.mark()
    builder.advanceLexer()

    var first = true
    while (true) {
      if (builder.tokenType === JavaSyntaxTokenType.RBRACE) {
        builder.advanceLexer()
        break
      }

      if (builder.tokenType == null) {
        error(builder, message("expected.rbrace"))
        break
      }

      if (elementParser(builder) == null) {
        if (builder.tokenType === JavaSyntaxTokenType.COMMA) {
          if (first && builder.lookAhead(1) === JavaSyntaxTokenType.RBRACE) {
            builder.advance(2)
            break
          }
          builder.error(message(missingElementKey))
        }
        else if (builder.tokenType !== JavaSyntaxTokenType.RBRACE) {
          error(builder, message("expected.rbrace"))
          break
        }
      }

      first = false

      val tokenType = builder.tokenType
      if (!builder.expect(JavaSyntaxTokenType.COMMA) && tokenType !== JavaSyntaxTokenType.RBRACE) {
        error(builder, message("expected.comma"))
      }
    }

    arrayInit.done(type)
    return arrayInit
  }

  fun parseArgumentList(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker {
    val list = builder.mark()
    builder.advanceLexer()

    var first = true
    while (true) {
      val tokenType = builder.tokenType
      if (first && (ARGS_LIST_END.contains(tokenType) || builder.eof())) break
      if (!first && !ARGS_LIST_CONTINUE.contains(tokenType)) break

      var hasError = false
      if (!first) {
        if (builder.tokenType === JavaSyntaxTokenType.COMMA) {
          builder.advanceLexer()
        }
        else {
          hasError = true
          error(builder, message("expected.comma.or.rparen"))
          emptyExpression(builder)
        }
      }
      first = false

      val arg = parse(builder, 0)
      if (arg == null) {
        if (!hasError) {
          error(builder, message("expected.expression"))
          emptyExpression(builder)
        }
        if (!ARGS_LIST_CONTINUE.contains(builder.tokenType)) break
        if (builder.tokenType !== JavaSyntaxTokenType.COMMA && !builder.eof()) {
          builder.advanceLexer()
        }
      }
    }

    var closed = true
    if (!builder.expect(JavaSyntaxTokenType.RPARENTH)) {
      if (first) {
        error(builder, message("expected.rparen"))
      }
      else {
        error(builder, message("expected.comma.or.rparen"))
      }
      closed = false
    }

    list.done(JavaSyntaxElementType.EXPRESSION_LIST)
    if (!closed) {
      list.setCustomEdgeTokenBinders(null, greedyRightBinder())
    }
    return list
  }

  private fun parseLambdaAfterParenth(builder: SyntaxTreeBuilder): SyntaxTreeBuilder.Marker? {
    val isLambda: Boolean
    val isTyped: Boolean

    val nextToken1 = builder.lookAhead(1)
    val nextToken2 = builder.lookAhead(2)
    if (nextToken1 === JavaSyntaxTokenType.RPARENTH && nextToken2 === JavaSyntaxTokenType.ARROW) {
      isLambda = true
      isTyped = false
    }
    else if (nextToken1 === JavaSyntaxTokenType.AT ||
             MODIFIER_BIT_SET.contains(nextToken1) ||
             PRIMITIVE_TYPE_BIT_SET.contains(nextToken1)
    ) {
      isLambda = true
      isTyped = true
    }
    else if (nextToken1 === JavaSyntaxTokenType.IDENTIFIER) {
      if (nextToken2 === JavaSyntaxTokenType.COMMA ||
          nextToken2 === JavaSyntaxTokenType.RPARENTH && builder.lookAhead(3) === JavaSyntaxTokenType.ARROW
      ) {
        isLambda = true
        isTyped = false
      }
      else if (nextToken2 === JavaSyntaxTokenType.ARROW) {
        isLambda = false
        isTyped = false
      }
      else {
        var lambda = false

        val marker = builder.mark()
        builder.advanceLexer()
        val typeInfo = myParser.referenceParser.parseTypeInfo(
          builder, ReferenceParser.ELLIPSIS or ReferenceParser.WILDCARD)
        if (typeInfo != null) {
          val t = builder.tokenType
          lambda = t === JavaSyntaxTokenType.IDENTIFIER ||
                   t === JavaSyntaxTokenType.THIS_KEYWORD ||
                   t === JavaSyntaxTokenType.RPARENTH && builder.lookAhead(1) === JavaSyntaxTokenType.ARROW
        }
        marker.rollbackTo()

        isLambda = lambda
        isTyped = true
      }
    }
    else {
      isLambda = false
      isTyped = false
    }

    return if (isLambda) parseLambdaExpression(builder, isTyped) else null
  }

  private fun parseLambdaExpression(builder: SyntaxTreeBuilder, typed: Boolean): SyntaxTreeBuilder.Marker? {
    val start = builder.mark()

    myParser.declarationParser.parseLambdaParameterList(builder, typed)

    if (!builder.expect(JavaSyntaxTokenType.ARROW)) {
      start.rollbackTo()
      return null
    }

    val body: SyntaxTreeBuilder.Marker?
    if (builder.tokenType === JavaSyntaxTokenType.LBRACE) {
      body = myParser.statementParser.parseCodeBlock(builder)
    }
    else {
      body = parse(builder, 0)
    }

    if (body == null) {
      builder.error(message("expected.lbrace"))
    }

    start.done(JavaSyntaxElementType.LAMBDA_EXPRESSION)
    return start
  }

  private enum class BreakPoint {
    P1, P2, P4
  }

  private interface InfixParser {
    /**
     * Starts to parse before the token with binOpType.
     */
    fun parse(
      parser: PrattExpressionParser,
      builder: SyntaxTreeBuilder,
      beforeLhs: SyntaxTreeBuilder.Marker,
      binOpType: SyntaxElementType?,
      currentPrecedence: Int,
      mode: Int
    )
  }

  private class ParserData(val myPrecedence: Int, val myParser: InfixParser)

  private inner class AssignmentParser : InfixParser {
    override fun parse(
      parser: PrattExpressionParser,
      builder: SyntaxTreeBuilder,
      beforeLhs: SyntaxTreeBuilder.Marker,
      binOpType: SyntaxElementType?,
      currentPrecedence: Int,
      mode: Int
    ) {
      advanceBinOpToken(builder, binOpType)
      val right = parser.tryParseWithPrecedenceAtMost(builder, ASSIGNMENT_PRECEDENCE, mode)
      if (right == null) {
        error(builder, message("expected.expression"))
      }
      done(beforeLhs, JavaSyntaxElementType.ASSIGNMENT_EXPRESSION, myParser.languageLevel)
    }
  }

  private inner class PolyExprParser : InfixParser {
    override fun parse(
      parser: PrattExpressionParser,
      builder: SyntaxTreeBuilder,
      beforeLhs: SyntaxTreeBuilder.Marker,
      binOpType: SyntaxElementType?,
      currentPrecedence: Int,
      mode: Int
    ) {
      var operandCount = 1
      while (true) {
        advanceBinOpToken(builder, binOpType)
        val rhs = parser.tryParseWithPrecedenceAtMost(builder, currentPrecedence - 1, mode)
        if (rhs == null) {
          error(builder, message("expected.expression"))
        }
        operandCount++
        val nextToken: SyntaxElementType? = getBinOpToken(builder)
        if (nextToken !== binOpType) {
          break
        }
      }
      done(beforeLhs, if (operandCount > 2)
        JavaSyntaxElementType.POLYADIC_EXPRESSION
      else
        JavaSyntaxElementType.BINARY_EXPRESSION, myParser.languageLevel)
    }
  }

  private class ConditionalExprParser : InfixParser {
    override fun parse(
      parser: PrattExpressionParser,
      builder: SyntaxTreeBuilder,
      beforeLhs: SyntaxTreeBuilder.Marker,
      binOpType: SyntaxElementType?,
      currentPrecedence: Int,
      mode: Int
    ) {
      builder.advanceLexer() // skipping ?

      val truePart = parser.parse(builder, mode)
      if (truePart == null) {
        error(builder, message("expected.expression"))
        beforeLhs.done(JavaSyntaxElementType.CONDITIONAL_EXPRESSION)
        return
      }

      if (builder.tokenType !== JavaSyntaxTokenType.COLON) {
        error(builder, message("expected.colon"))
        beforeLhs.done(JavaSyntaxElementType.CONDITIONAL_EXPRESSION)
        return
      }
      builder.advanceLexer()

      val falsePart = parser.tryParseWithPrecedenceAtMost(builder, CONDITIONAL_EXPR_PRECEDENCE, mode)
      if (falsePart == null) {
        error(builder, message("expected.expression"))
      }

      beforeLhs.done(JavaSyntaxElementType.CONDITIONAL_EXPRESSION)
    }
  }

  private class InstanceofParser : InfixParser {
    override fun parse(
      parser: PrattExpressionParser,
      builder: SyntaxTreeBuilder,
      beforeLhs: SyntaxTreeBuilder.Marker,
      binOpType: SyntaxElementType?,
      currentPrecedence: Int,
      mode: Int
    ) {
      builder.advanceLexer() // skipping 'instanceof'

      val javaParser = parser.myParser
      if (!javaParser.patternParser.isPattern(builder)) {
        val type =
          javaParser.referenceParser.parseType(builder, ReferenceParser.EAT_LAST_DOT or ReferenceParser.WILDCARD)
        if (type == null) {
          error(builder, message("expected.type"))
        }
      }
      else {
        javaParser.patternParser.parsePrimaryPattern(builder, false)
      }
      beforeLhs.done(JavaSyntaxElementType.INSTANCE_OF_EXPRESSION)
    }
  }

  private fun getBinOpToken(builder: SyntaxTreeBuilder): SyntaxElementType? {
    val tokenType = builder.tokenType
    if (tokenType !== JavaSyntaxTokenType.GT) return tokenType

    if (builder.rawLookup(1) === JavaSyntaxTokenType.GT) {
      if (builder.rawLookup(2) === JavaSyntaxTokenType.GT) {
        if (builder.rawLookup(3) === JavaSyntaxTokenType.EQ) {
          return JavaSyntaxTokenType.GTGTGTEQ
        }
        return JavaSyntaxTokenType.GTGTGT
      }
      if (builder.rawLookup(2) === JavaSyntaxTokenType.EQ) {
        return JavaSyntaxTokenType.GTGTEQ
      }
      return JavaSyntaxTokenType.GTGT
    }
    else if (builder.rawLookup(1) === JavaSyntaxTokenType.EQ) {
      return JavaSyntaxTokenType.GE
    }

    return tokenType
  }

  private fun advanceBinOpToken(builder: SyntaxTreeBuilder, type: SyntaxElementType?) {
    val gtToken = builder.mark()

    when (type) {
      JavaSyntaxTokenType.GTGTGTEQ -> builder.advance(4)
      JavaSyntaxTokenType.GTGTGT, JavaSyntaxTokenType.GTGTEQ -> builder.advance(3)
      JavaSyntaxTokenType.GTGT, JavaSyntaxTokenType.GE -> builder.advance(2)
      else -> {
        gtToken.drop()
        builder.advanceLexer()
        return
      }
    }

    gtToken.collapse(type)
  }

  private fun parseClassObjectAccess(
    builder: SyntaxTreeBuilder,
    expr: SyntaxTreeBuilder.Marker,
    optionalClassKeyword: Boolean
  ): SyntaxTreeBuilder.Marker? {
    val mark = builder.mark()
    builder.advanceLexer()

    if (builder.tokenType === JavaSyntaxTokenType.CLASS_KEYWORD) {
      mark.drop()
      builder.advanceLexer()
    }
    else {
      if (!optionalClassKeyword) return null
      mark.rollbackTo()
      builder.error(message("class.literal.expected"))
    }

    expr.done(JavaSyntaxElementType.CLASS_OBJECT_ACCESS_EXPRESSION)
    return expr
  }

  private fun emptyExpression(builder: SyntaxTreeBuilder) {
    emptyElement(builder, JavaSyntaxElementType.EMPTY_EXPRESSION)
  }

  companion object {
    private val THIS_OR_SUPER: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaSyntaxTokenType.THIS_KEYWORD, JavaSyntaxTokenType.SUPER_KEYWORD)
    private val ID_OR_SUPER: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaSyntaxTokenType.IDENTIFIER, JavaSyntaxTokenType.SUPER_KEYWORD)
    private val ARGS_LIST_CONTINUE: SyntaxElementTypeSet = syntaxElementTypeSetOf(
      JavaSyntaxTokenType.IDENTIFIER, BAD_CHARACTER, JavaSyntaxTokenType.COMMA, JavaSyntaxTokenType.INTEGER_LITERAL,
      JavaSyntaxTokenType.STRING_LITERAL
    )
    private val ARGS_LIST_END: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaSyntaxTokenType.RPARENTH, JavaSyntaxTokenType.RBRACE, JavaSyntaxTokenType.RBRACKET)

    private val TYPE_START: SyntaxElementTypeSet = PRIMITIVE_TYPE_BIT_SET + JavaSyntaxTokenType.IDENTIFIER + JavaSyntaxTokenType.AT
    private val POSTFIX_OPS: SyntaxElementTypeSet = syntaxElementTypeSetOf(JavaSyntaxTokenType.PLUSPLUS, JavaSyntaxTokenType.MINUSMINUS)
    private val PREF_ARITHMETIC_OPS: SyntaxElementTypeSet = POSTFIX_OPS + JavaSyntaxTokenType.PLUS + JavaSyntaxTokenType.MINUS
    private val PREFIX_OPS: SyntaxElementTypeSet = PREF_ARITHMETIC_OPS + JavaSyntaxTokenType.TILDE + JavaSyntaxTokenType.EXCL
  }
}


private const val MULTIPLICATION_PRECEDENCE = 2
private const val ADDITIVE_PRECEDENCE = 3
private const val SHIFT_PRECEDENCE = 4
private const val COMPARISON_AND_INSTANCEOF_PRECEDENCE = 5
private const val EQUALITY_PRECEDENCE = 6
private const val BITWISE_AND_PRECEDENCE = 7
private const val BITWISE_XOR_PRECEDENCE = 8
private const val BITWISE_OR_PRECEDENCE = 9
private const val LOGICAL_AND_PRECEDENCE = 10
private const val LOGICAL_OR_PRECEDENCE = 11
const val CONDITIONAL_EXPR_PRECEDENCE: Int = 12
private const val ASSIGNMENT_PRECEDENCE = 13
