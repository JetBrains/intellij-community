// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesBinders;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static com.intellij.lang.PsiBuilderUtil.*;
import static com.intellij.lang.PsiBuilderUtil.rollbackTo;
import static com.intellij.lang.java.parser.JavaParserUtil.*;

public final class PrattExpressionParser {
  static final int FORBID_LAMBDA_MASK = 0x1;
  private static final Map<IElementType, ParserData> ourInfixParsers;
  private static final TokenSet THIS_OR_SUPER = TokenSet.create(JavaTokenType.THIS_KEYWORD, JavaTokenType.SUPER_KEYWORD);
  private static final TokenSet ID_OR_SUPER = TokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.SUPER_KEYWORD);
  private static final TokenSet ARGS_LIST_CONTINUE = TokenSet.create(
    JavaTokenType.IDENTIFIER, TokenType.BAD_CHARACTER, JavaTokenType.COMMA, JavaTokenType.INTEGER_LITERAL, JavaTokenType.STRING_LITERAL);
  private static final TokenSet ARGS_LIST_END = TokenSet.create(JavaTokenType.RPARENTH, JavaTokenType.RBRACE, JavaTokenType.RBRACKET);

  private static final TokenSet TYPE_START = TokenSet.orSet(
    ElementType.PRIMITIVE_TYPE_BIT_SET, TokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.AT));
  private static final TokenSet POSTFIX_OPS = TokenSet.create(JavaTokenType.PLUSPLUS, JavaTokenType.MINUSMINUS);
  private static final TokenSet PREF_ARITHMETIC_OPS = TokenSet.orSet(POSTFIX_OPS, TokenSet.create(JavaTokenType.PLUS, JavaTokenType.MINUS));
  private static final TokenSet PREFIX_OPS = TokenSet.orSet(PREF_ARITHMETIC_OPS, TokenSet.create(JavaTokenType.TILDE, JavaTokenType.EXCL));


  private static final int MULTIPLICATION_PRECEDENCE = 2;
  private static final int ADDITIVE_PRECEDENCE = 3;
  private static final int SHIFT_PRECEDENCE = 4;
  private static final int COMPARISON_AND_INSTANCEOF_PRECEDENCE = 5;
  private static final int EQUALITY_PRECEDENCE = 6;
  private static final int BITWISE_AND_PRECEDENCE = 7;
  private static final int BITWISE_XOR_PRECEDENCE = 8;
  private static final int BITWISE_OR_PRECEDENCE = 9;
  private static final int LOGICAL_AND_PRECEDENCE = 10;
  private static final int LOGICAL_OR_PRECEDENCE = 11;
  static final int CONDITIONAL_EXPR_PRECEDENCE = 12;
  private static final int ASSIGNMENT_PRECEDENCE = 13;

  static {
    ourInfixParsers = new HashMap<>();
    for (IElementType type : Arrays.asList(JavaTokenType.EQ, JavaTokenType.ASTERISKEQ, JavaTokenType.DIVEQ, JavaTokenType.PERCEQ,
                                           JavaTokenType.PLUSEQ, JavaTokenType.MINUSEQ,
                                           JavaTokenType.LTLTEQ, JavaTokenType.GTGTEQ, JavaTokenType.GTGTGTEQ, JavaTokenType.ANDEQ,
                                           JavaTokenType.OREQ, JavaTokenType.XOREQ)) {
      ourInfixParsers.put(type, new ParserData(ASSIGNMENT_PRECEDENCE, AssignmentParser.INSTANCE));
    }
    for (IElementType type : Arrays.asList(JavaTokenType.PLUS, JavaTokenType.MINUS)) {
      ourInfixParsers.put(type, new ParserData(ADDITIVE_PRECEDENCE, PolyExprParser.INSTANCE));
    }
    for (IElementType type : Arrays.asList(JavaTokenType.DIV, JavaTokenType.ASTERISK, JavaTokenType.PERC)) {
      ourInfixParsers.put(type, new ParserData(MULTIPLICATION_PRECEDENCE, PolyExprParser.INSTANCE));
    }
    for (IElementType type : Arrays.asList(JavaTokenType.LTLT, JavaTokenType.GTGT, JavaTokenType.GTGTGT)) {
      ourInfixParsers.put(type, new ParserData(SHIFT_PRECEDENCE, PolyExprParser.INSTANCE));
    }
    for (IElementType type : Arrays.asList(JavaTokenType.LT, JavaTokenType.GT, JavaTokenType.LE, JavaTokenType.GE)) {
      ourInfixParsers.put(type, new ParserData(COMPARISON_AND_INSTANCEOF_PRECEDENCE, PolyExprParser.INSTANCE));
    }
    ourInfixParsers.put(JavaTokenType.INSTANCEOF_KEYWORD, new ParserData(COMPARISON_AND_INSTANCEOF_PRECEDENCE, InstanceofParser.INSTANCE));
    for (IElementType type : Arrays.asList(JavaTokenType.EQEQ, JavaTokenType.NE)) {
      ourInfixParsers.put(type, new ParserData(EQUALITY_PRECEDENCE, PolyExprParser.INSTANCE));
    }
    ourInfixParsers.put(JavaTokenType.OR, new ParserData(BITWISE_OR_PRECEDENCE, PolyExprParser.INSTANCE));
    ourInfixParsers.put(JavaTokenType.AND, new ParserData(BITWISE_AND_PRECEDENCE, PolyExprParser.INSTANCE));
    ourInfixParsers.put(JavaTokenType.XOR, new ParserData(BITWISE_XOR_PRECEDENCE, PolyExprParser.INSTANCE));
    ourInfixParsers.put(JavaTokenType.ANDAND, new ParserData(LOGICAL_AND_PRECEDENCE, PolyExprParser.INSTANCE));
    ourInfixParsers.put(JavaTokenType.OROR, new ParserData(LOGICAL_OR_PRECEDENCE, PolyExprParser.INSTANCE));
    ourInfixParsers.put(JavaTokenType.QUEST, new ParserData(CONDITIONAL_EXPR_PRECEDENCE, ConditionalExprParser.INSTANCE));
  }

  private final JavaParser myParser;

  public PrattExpressionParser(JavaParser parser) { myParser = parser; }

  public @Nullable PsiBuilder.Marker parse(@NotNull PsiBuilder builder) {
    return tryParseWithPrecedenceAtMost(builder, ASSIGNMENT_PRECEDENCE, 0);
  }

  public @Nullable PsiBuilder.Marker parse(@NotNull PsiBuilder builder, int mode) {
    return tryParseWithPrecedenceAtMost(builder, ASSIGNMENT_PRECEDENCE, mode);
  }

  /**
   * Traditional Pratt parser for infix expressions.
   * If marker is null it is guaranteed that nothing is parsed
   */
  public @Nullable PsiBuilder.Marker tryParseWithPrecedenceAtMost(@NotNull PsiBuilder builder, int maxPrecedence, int mode) {
    PsiBuilder.Marker lhs = parseUnary(builder, mode);
    if (lhs == null) return null;

    while (true) {
      IElementType type = getBinOpToken(builder);
      if (type == null) {
        break;
      }
      ParserData data = ourInfixParsers.get(type);
      if (data == null) {
        break;
      }
      int opPrecedence = data.myPrecedence;
      if (maxPrecedence < opPrecedence) {
        break;
      }
      PsiBuilder.Marker beforeLhs = lhs.precede();
      data.myParser.parse(this, builder, beforeLhs, type, opPrecedence, mode);
      lhs = beforeLhs;
    }
    return lhs;
  }

  @Nullable
  private PsiBuilder.Marker parseUnary(final PsiBuilder builder, final int mode) {
    final IElementType tokenType = builder.getTokenType();

    if (PREFIX_OPS.contains(tokenType)) {
      final PsiBuilder.Marker unary = builder.mark();
      builder.advanceLexer();

      final PsiBuilder.Marker operand = parseUnary(builder, mode);
      if (operand == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }

      unary.done(JavaElementType.PREFIX_EXPRESSION);
      return unary;
    }
    else if (tokenType == JavaTokenType.LPARENTH) {
      final PsiBuilder.Marker typeCast = builder.mark();
      builder.advanceLexer();

      ReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(
        builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD | ReferenceParser.CONJUNCTIONS | ReferenceParser.INCOMPLETE_ANNO);
      if (typeInfo == null || !expect(builder, JavaTokenType.RPARENTH)) {
        typeCast.rollbackTo();
        return parsePostfix(builder, mode);
      }

      if (PREF_ARITHMETIC_OPS.contains(builder.getTokenType()) && !typeInfo.isPrimitive) {
        typeCast.rollbackTo();
        return parsePostfix(builder, mode);
      }

      final PsiBuilder.Marker expr = parseUnary(builder, mode);
      if (expr == null) {
        if (!typeInfo.isParameterized) {  // cannot parse correct parenthesized expression after correct parameterized type
          typeCast.rollbackTo();
          return parsePostfix(builder, mode);
        }
        else {
          error(builder, JavaPsiBundle.message("expected.expression"));
        }
      }

      typeCast.done(JavaElementType.TYPE_CAST_EXPRESSION);
      return typeCast;
    }
    else if (tokenType == JavaTokenType.SWITCH_KEYWORD) {
      return myParser.getStatementParser().parseExprInParenthWithBlock(builder, JavaElementType.SWITCH_EXPRESSION, true);
    }
    else {
      return parsePostfix(builder, mode);
    }
  }

  @Nullable
  private PsiBuilder.Marker parsePostfix(final PsiBuilder builder, final int mode) {
    PsiBuilder.Marker operand = parsePrimary(builder, null, -1, mode);
    if (operand == null) return null;

    while (POSTFIX_OPS.contains(builder.getTokenType())) {
      final PsiBuilder.Marker postfix = operand.precede();
      builder.advanceLexer();
      postfix.done(JavaElementType.POSTFIX_EXPRESSION);
      operand = postfix;
    }

    return operand;
  }

  @Nullable
  private PsiBuilder.Marker parsePrimary(PsiBuilder builder, @Nullable BreakPoint breakPoint, int breakOffset, final int mode) {
    PsiBuilder.Marker startMarker = builder.mark();

    PsiBuilder.Marker expr = parsePrimaryExpressionStart(builder, mode);
    if (expr == null) {
      startMarker.drop();
      return null;
    }

    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.DOT) {
        final PsiBuilder.Marker dotPos = builder.mark();
        final int dotOffset = builder.getCurrentOffset();
        builder.advanceLexer();

        IElementType dotTokenType = builder.getTokenType();
        if (dotTokenType == JavaTokenType.AT) {
          myParser.getDeclarationParser().parseAnnotations(builder);
          dotTokenType = builder.getTokenType();
        }

        if (dotTokenType == JavaTokenType.CLASS_KEYWORD && exprType(expr) == JavaElementType.REFERENCE_EXPRESSION) {
          if (breakPoint == BreakPoint.P1 && builder.getCurrentOffset() == breakOffset) {
            error(builder, JavaPsiBundle.message("expected.identifier"));
            drop(startMarker, dotPos);
            return expr;
          }

          final PsiBuilder.Marker copy = startMarker.precede();
          final int offset = builder.getCurrentOffset();
          startMarker.rollbackTo();

          final PsiBuilder.Marker classObjAccess = parseClassAccessOrMethodReference(builder);
          if (classObjAccess == null || builder.getCurrentOffset() < offset) {
            copy.rollbackTo();
            return parsePrimary(builder, BreakPoint.P1, offset, mode);
          }

          startMarker = copy;
          expr = classObjAccess;
        }
        else if (dotTokenType == JavaTokenType.NEW_KEYWORD) {
          dotPos.drop();
          expr = parseNew(builder, expr);
        }
        else if (dotTokenType == JavaTokenType.SUPER_KEYWORD && builder.lookAhead(1) == JavaTokenType.LPARENTH) {
          dotPos.drop();
          PsiBuilder.Marker refExpr = expr.precede();
          builder.mark().done(JavaElementType.REFERENCE_PARAMETER_LIST);
          builder.advanceLexer();
          refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
          expr = refExpr;
        }
        else if (THIS_OR_SUPER.contains(dotTokenType) && exprType(expr) == JavaElementType.REFERENCE_EXPRESSION) {
          if (breakPoint == BreakPoint.P2 && builder.getCurrentOffset() == breakOffset) {
            dotPos.rollbackTo();
            startMarker.drop();
            return expr;
          }

          PsiBuilder.Marker copy = startMarker.precede();
          int offset = builder.getCurrentOffset();
          startMarker.rollbackTo();

          PsiBuilder.Marker ref = myParser.getReferenceParser().parseJavaCodeReference(builder, false, true, false, false);
          if (ref == null || builder.getTokenType() != JavaTokenType.DOT || builder.getCurrentOffset() != dotOffset) {
            copy.rollbackTo();
            return parsePrimary(builder, BreakPoint.P2, offset, mode);
          }
          builder.advanceLexer();

          if (builder.getTokenType() != dotTokenType) {
            copy.rollbackTo();
            return parsePrimary(builder, BreakPoint.P2, offset, mode);
          }
          builder.advanceLexer();

          startMarker = copy;
          expr = ref.precede();
          expr.done(dotTokenType == JavaTokenType.THIS_KEYWORD ? JavaElementType.THIS_EXPRESSION : JavaElementType.SUPER_EXPRESSION);
        }
        else {
          PsiBuilder.Marker refExpr = expr.precede();

          myParser.getReferenceParser().parseReferenceParameterList(builder, false, false);

          if (!expect(builder, ID_OR_SUPER)) {
            dotPos.rollbackTo();
            builder.advanceLexer();
            myParser.getReferenceParser().parseReferenceParameterList(builder, false, false);
            error(builder, JavaPsiBundle.message("expected.identifier"));
            refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
            startMarker.drop();
            return refExpr;
          }

          dotPos.drop();
          refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
          expr = refExpr;
        }
      }
      else if (tokenType == JavaTokenType.LPARENTH) {
        if (exprType(expr) != JavaElementType.REFERENCE_EXPRESSION) {
          startMarker.drop();
          return expr;
        }

        PsiBuilder.Marker callExpr = expr.precede();
        parseArgumentList(builder);
        callExpr.done(JavaElementType.METHOD_CALL_EXPRESSION);
        expr = callExpr;
      }
      else if (tokenType == JavaTokenType.LBRACKET) {
        if (breakPoint == BreakPoint.P4) {
          startMarker.drop();
          return expr;
        }

        builder.advanceLexer();

        if (builder.getTokenType() == JavaTokenType.RBRACKET && exprType(expr) == JavaElementType.REFERENCE_EXPRESSION) {
          final int pos = builder.getCurrentOffset();
          final PsiBuilder.Marker copy = startMarker.precede();
          startMarker.rollbackTo();

          final PsiBuilder.Marker classObjAccess = parseClassAccessOrMethodReference(builder);
          if (classObjAccess == null || builder.getCurrentOffset() <= pos) {
            copy.rollbackTo();
            return parsePrimary(builder, BreakPoint.P4, -1, mode);
          }

          startMarker = copy;
          expr = classObjAccess;
        }
        else {
          final PsiBuilder.Marker arrayAccess = expr.precede();

          final PsiBuilder.Marker index = parse(builder, mode);
          if (index == null) {
            error(builder, JavaPsiBundle.message("expected.expression"));
            arrayAccess.done(JavaElementType.ARRAY_ACCESS_EXPRESSION);
            startMarker.drop();
            return arrayAccess;
          }

          if (builder.getTokenType() != JavaTokenType.RBRACKET) {
            error(builder, JavaPsiBundle.message("expected.rbracket"));
            arrayAccess.done(JavaElementType.ARRAY_ACCESS_EXPRESSION);
            startMarker.drop();
            return arrayAccess;
          }
          builder.advanceLexer();

          arrayAccess.done(JavaElementType.ARRAY_ACCESS_EXPRESSION);
          expr = arrayAccess;
        }
      }
      else if (tokenType == JavaTokenType.DOUBLE_COLON) {
        return parseMethodReference(builder, startMarker);
      }
      else {
        startMarker.drop();
        return expr;
      }
    }
  }

  @Nullable
  private PsiBuilder.Marker parsePrimaryExpressionStart(final PsiBuilder builder, final int mode) {
    IElementType tokenType = builder.getTokenType();

    if (ElementType.ALL_LITERALS.contains(tokenType)) {
      final PsiBuilder.Marker literal = builder.mark();
      builder.advanceLexer();
      literal.done(JavaElementType.LITERAL_EXPRESSION);
      return literal;
    }

    if (tokenType == JavaTokenType.LBRACE) {
      return parseArrayInitializer(builder);
    }

    if (tokenType == JavaTokenType.NEW_KEYWORD) {
      return parseNew(builder, null);
    }

    if (tokenType == JavaTokenType.LPARENTH) {
      if (!BitUtil.isSet(mode, FORBID_LAMBDA_MASK)) {
        final PsiBuilder.Marker lambda = parseLambdaAfterParenth(builder);
        if (lambda != null) {
          return lambda;
        }
      }

      final PsiBuilder.Marker parenth = builder.mark();
      builder.advanceLexer();

      final PsiBuilder.Marker inner = parse(builder, mode);
      if (inner == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }

      if (!expect(builder, JavaTokenType.RPARENTH)) {
        if (inner != null) {
          error(builder, JavaPsiBundle.message("expected.rparen"));
        }
      }

      parenth.done(JavaElementType.PARENTH_EXPRESSION);
      return parenth;
    }

    if (TYPE_START.contains(tokenType)) {
      final PsiBuilder.Marker mark = builder.mark();

      final ReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(builder, 0);
      if (typeInfo != null) {
        boolean optionalClassKeyword = typeInfo.isPrimitive || typeInfo.isArray;
        if (optionalClassKeyword || !typeInfo.hasErrors && typeInfo.isParameterized) {
          final PsiBuilder.Marker result = parseClassAccessOrMethodReference(builder, mark, optionalClassKeyword);
          if (result != null) {
            return result;
          }
        }
      }

      mark.rollbackTo();
    }

    PsiBuilder.Marker annotation = null;
    if (tokenType == JavaTokenType.AT) {
      annotation = myParser.getDeclarationParser().parseAnnotations(builder);
      tokenType = builder.getTokenType();
    }

    if (tokenType == JavaTokenType.VAR_KEYWORD) {
      builder.remapCurrentToken(tokenType = JavaTokenType.IDENTIFIER);
    }
    if (tokenType == JavaTokenType.IDENTIFIER) {
      if (!BitUtil.isSet(mode, FORBID_LAMBDA_MASK) && builder.lookAhead(1) == JavaTokenType.ARROW) {
        return parseLambdaExpression(builder, false);
      }

      final PsiBuilder.Marker refExpr;
      if (annotation != null) {
        final PsiBuilder.Marker refParam = annotation.precede();
        refParam.doneBefore(JavaElementType.REFERENCE_PARAMETER_LIST, annotation);
        refExpr = refParam.precede();
      }
      else {
        refExpr = builder.mark();
        builder.mark().done(JavaElementType.REFERENCE_PARAMETER_LIST);
      }

      builder.advanceLexer();
      refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
      return refExpr;
    }

    if (annotation != null) {
      annotation.rollbackTo();
      tokenType = builder.getTokenType();
    }

    PsiBuilder.Marker expr = null;
    if (tokenType == JavaTokenType.LT) {
      expr = builder.mark();

      if (!myParser.getReferenceParser().parseReferenceParameterList(builder, false, false)) {
        expr.rollbackTo();
        return null;
      }

      tokenType = builder.getTokenType();
      if (!THIS_OR_SUPER.contains(tokenType)) {
        expr.rollbackTo();
        return null;
      }
    }

    if (THIS_OR_SUPER.contains(tokenType)) {
      if (expr == null) {
        expr = builder.mark();
        builder.mark().done(JavaElementType.REFERENCE_PARAMETER_LIST);
      }
      builder.advanceLexer();
      expr.done(builder.getTokenType() == JavaTokenType.LPARENTH
                ? JavaElementType.REFERENCE_EXPRESSION
                : tokenType == JavaTokenType.THIS_KEYWORD
                  ? JavaElementType.THIS_EXPRESSION
                  : JavaElementType.SUPER_EXPRESSION);
      return expr;
    }

    return null;
  }

  @Nullable
  private PsiBuilder.Marker parseClassAccessOrMethodReference(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();

    boolean primitive = ElementType.PRIMITIVE_TYPE_BIT_SET.contains(builder.getTokenType());
    if (myParser.getReferenceParser().parseType(builder, 0) == null) {
      expr.drop();
      return null;
    }

    PsiBuilder.Marker result = parseClassAccessOrMethodReference(builder, expr, primitive);
    if (result == null) expr.rollbackTo();
    return result;
  }

  @Nullable
  private PsiBuilder.Marker parseClassAccessOrMethodReference(PsiBuilder builder, PsiBuilder.Marker expr, boolean optionalClassKeyword) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.DOT) {
      return parseClassObjectAccess(builder, expr, optionalClassKeyword);
    }
    else if (tokenType == JavaTokenType.DOUBLE_COLON) {
      return parseMethodReference(builder, expr);
    }

    return null;
  }

  @NotNull
  private PsiBuilder.Marker parseMethodReference(final PsiBuilder builder, final PsiBuilder.Marker start) {
    builder.advanceLexer();

    myParser.getReferenceParser().parseReferenceParameterList(builder, false, false);

    if (!expect(builder, JavaTokenType.IDENTIFIER) && !expect(builder, JavaTokenType.NEW_KEYWORD)) {
      error(builder, JavaPsiBundle.message("expected.identifier"));
    }

    start.done(JavaElementType.METHOD_REF_EXPRESSION);
    return start;
  }

  @NotNull
  private PsiBuilder.Marker parseNew(PsiBuilder builder, @Nullable PsiBuilder.Marker start) {
    PsiBuilder.Marker newExpr = (start != null ? start.precede() : builder.mark());
    builder.advanceLexer();

    myParser.getReferenceParser().parseReferenceParameterList(builder, false, true);

    PsiBuilder.Marker refOrType;
    PsiBuilder.Marker anno = myParser.getDeclarationParser().parseAnnotations(builder);
    IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.IDENTIFIER) {
      rollbackTo(anno);
      refOrType = myParser.getReferenceParser().parseJavaCodeReference(builder, true, true, true, true);
      if (refOrType == null) {
        error(builder, JavaPsiBundle.message("expected.identifier"));
        newExpr.done(JavaElementType.NEW_EXPRESSION);
        return newExpr;
      }
    }
    else if (ElementType.PRIMITIVE_TYPE_BIT_SET.contains(tokenType)) {
      refOrType = null;
      builder.advanceLexer();
    }
    else {
      error(builder, JavaPsiBundle.message("expected.identifier"));
      newExpr.done(JavaElementType.NEW_EXPRESSION);
      return newExpr;
    }

    if (refOrType != null && builder.getTokenType() == JavaTokenType.LPARENTH) {
      parseArgumentList(builder);
      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        final PsiBuilder.Marker classElement = refOrType.precede();
        myParser.getDeclarationParser().parseClassBodyWithBraces(builder, false, false);
        classElement.done(JavaElementType.ANONYMOUS_CLASS);
      }
      newExpr.done(JavaElementType.NEW_EXPRESSION);
      return newExpr;
    }

    anno = myParser.getDeclarationParser().parseAnnotations(builder);

    if (builder.getTokenType() != JavaTokenType.LBRACKET) {
      rollbackTo(anno);
      error(builder, JavaPsiBundle.message(refOrType == null ? "expected.lbracket" : "expected.lparen.or.lbracket"));
      newExpr.done(JavaElementType.NEW_EXPRESSION);
      return newExpr;
    }

    int bracketCount = 0;
    int dimCount = 0;
    while (true) {
      anno = myParser.getDeclarationParser().parseAnnotations(builder);

      if (builder.getTokenType() != JavaTokenType.LBRACKET) {
        rollbackTo(anno);
        break;
      }
      builder.advanceLexer();

      if (bracketCount == dimCount) {
        final PsiBuilder.Marker dimExpr = parse(builder, 0);
        if (dimExpr != null) {
          dimCount++;
        }
      }
      bracketCount++;

      if (!expectOrError(builder, JavaTokenType.RBRACKET, "expected.rbracket")) {
        newExpr.done(JavaElementType.NEW_EXPRESSION);
        return newExpr;
      }
    }

    if (dimCount == 0) {
      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        parseArrayInitializer(builder);
      }
      else {
        error(builder, JavaPsiBundle.message("expected.array.initializer"));
      }
    }

    newExpr.done(JavaElementType.NEW_EXPRESSION);
    return newExpr;
  }

  @NotNull
  private PsiBuilder.Marker parseArrayInitializer(PsiBuilder builder) {
    return parseArrayInitializer(builder, JavaElementType.ARRAY_INITIALIZER_EXPRESSION, this::parse, "expected.expression");
  }

  @NotNull
  public PsiBuilder.Marker parseArrayInitializer(@NotNull PsiBuilder builder,
                                                 @NotNull IElementType type,
                                                 @NotNull Function<? super PsiBuilder, PsiBuilder.Marker> elementParser,
                                                 @NotNull @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String missingElementKey) {
    PsiBuilder.Marker arrayInit = builder.mark();
    builder.advanceLexer();

    boolean first = true;
    while (true) {
      if (builder.getTokenType() == JavaTokenType.RBRACE) {
        builder.advanceLexer();
        break;
      }

      if (builder.getTokenType() == null) {
        error(builder, JavaPsiBundle.message("expected.rbrace"));
        break;
      }

      if (elementParser.apply(builder) == null) {
        if (builder.getTokenType() == JavaTokenType.COMMA) {
          if (first && builder.lookAhead(1) == JavaTokenType.RBRACE) {
            advance(builder, 2);
            break;
          }
          builder.error(JavaPsiBundle.message(missingElementKey));
        }
        else if (builder.getTokenType() != JavaTokenType.RBRACE) {
          error(builder, JavaPsiBundle.message("expected.rbrace"));
          break;
        }
      }

      first = false;

      IElementType tokenType = builder.getTokenType();
      if (!expect(builder, JavaTokenType.COMMA) && tokenType != JavaTokenType.RBRACE) {
        error(builder, JavaPsiBundle.message("expected.comma"));
      }
    }

    arrayInit.done(type);
    return arrayInit;
  }

  @NotNull
  public PsiBuilder.Marker parseArgumentList(final PsiBuilder builder) {
    final PsiBuilder.Marker list = builder.mark();
    builder.advanceLexer();

    boolean first = true;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (first && (ARGS_LIST_END.contains(tokenType) || builder.eof())) break;
      if (!first && !ARGS_LIST_CONTINUE.contains(tokenType)) break;

      boolean hasError = false;
      if (!first) {
        if (builder.getTokenType() == JavaTokenType.COMMA) {
          builder.advanceLexer();
        }
        else {
          hasError = true;
          error(builder, JavaPsiBundle.message("expected.comma.or.rparen"));
          emptyExpression(builder);
        }
      }
      first = false;

      final PsiBuilder.Marker arg = parse(builder, 0);
      if (arg == null) {
        if (!hasError) {
          error(builder, JavaPsiBundle.message("expected.expression"));
          emptyExpression(builder);
        }
        if (!ARGS_LIST_CONTINUE.contains(builder.getTokenType())) break;
        if (builder.getTokenType() != JavaTokenType.COMMA && !builder.eof()) {
          builder.advanceLexer();
        }
      }
    }

    boolean closed = true;
    if (!expect(builder, JavaTokenType.RPARENTH)) {
      if (first) {
        error(builder, JavaPsiBundle.message("expected.rparen"));
      }
      else {
        error(builder, JavaPsiBundle.message("expected.comma.or.rparen"));
      }
      closed = false;
    }

    list.done(JavaElementType.EXPRESSION_LIST);
    if (!closed) {
      list.setCustomEdgeTokenBinders(null, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    }
    return list;
  }

  @Nullable
  private PsiBuilder.Marker parseLambdaAfterParenth(final PsiBuilder builder) {
    final boolean isLambda;
    final boolean isTyped;

    final IElementType nextToken1 = builder.lookAhead(1);
    final IElementType nextToken2 = builder.lookAhead(2);
    if (nextToken1 == JavaTokenType.RPARENTH && nextToken2 == JavaTokenType.ARROW) {
      isLambda = true;
      isTyped = false;
    }
    else if (nextToken1 == JavaTokenType.AT ||
             ElementType.MODIFIER_BIT_SET.contains(nextToken1) || ElementType.PRIMITIVE_TYPE_BIT_SET.contains(nextToken1)) {
      isLambda = true;
      isTyped = true;
    }
    else if (nextToken1 == JavaTokenType.IDENTIFIER) {
      if (nextToken2 == JavaTokenType.COMMA || nextToken2 == JavaTokenType.RPARENTH && builder.lookAhead(3) == JavaTokenType.ARROW) {
        isLambda = true;
        isTyped = false;
      }
      else if (nextToken2 == JavaTokenType.ARROW) {
        isLambda = false;
        isTyped = false;
      }
      else {
        boolean lambda = false;

        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer();
        ReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(
          builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.ELLIPSIS | ReferenceParser.WILDCARD);
        if (typeInfo != null) {
          IElementType t = builder.getTokenType();
          if (t == JavaTokenType.IDENTIFIER ||
              t == JavaTokenType.THIS_KEYWORD ||
              t == JavaTokenType.RPARENTH && builder.lookAhead(1) == JavaTokenType.ARROW) {
            lambda = true;
          }
        }
        marker.rollbackTo();

        isLambda = lambda;
        isTyped = true;
      }
    }
    else {
      isLambda = false;
      isTyped = false;
    }

    return isLambda ? parseLambdaExpression(builder, isTyped) : null;
  }

  private PsiBuilder.Marker parseLambdaExpression(final PsiBuilder builder, final boolean typed) {
    final PsiBuilder.Marker start = builder.mark();

    myParser.getDeclarationParser().parseLambdaParameterList(builder, typed);

    if (!expect(builder, JavaTokenType.ARROW)) {
      start.rollbackTo();
      return null;
    }

    final PsiBuilder.Marker body;
    if (builder.getTokenType() == JavaTokenType.LBRACE) {
      body = myParser.getStatementParser().parseCodeBlock(builder);
    }
    else {
      body = parse(builder, 0);
    }

    if (body == null) {
      builder.error(JavaPsiBundle.message("expected.lbrace"));
    }

    start.done(JavaElementType.LAMBDA_EXPRESSION);
    return start;
  }

  private static IElementType getBinOpToken(PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType != JavaTokenType.GT) return tokenType;

    if (builder.rawLookup(1) == JavaTokenType.GT) {
      if (builder.rawLookup(2) == JavaTokenType.GT) {
        if (builder.rawLookup(3) == JavaTokenType.EQ) {
          tokenType = JavaTokenType.GTGTGTEQ;
        }
        else {
          tokenType = JavaTokenType.GTGTGT;
        }
      }
      else if (builder.rawLookup(2) == JavaTokenType.EQ) {
        tokenType = JavaTokenType.GTGTEQ;
      }
      else {
        tokenType = JavaTokenType.GTGT;
      }
    }
    else if (builder.rawLookup(1) == JavaTokenType.EQ) {
      tokenType = JavaTokenType.GE;
    }

    return tokenType;
  }

  private static void advanceBinOpToken(final PsiBuilder builder, final IElementType type) {
    final PsiBuilder.Marker gtToken = builder.mark();

    if (type == JavaTokenType.GTGTGTEQ) {
      advance(builder, 4);
    }
    else if (type == JavaTokenType.GTGTGT || type == JavaTokenType.GTGTEQ) {
      advance(builder, 3);
    }
    else if (type == JavaTokenType.GTGT || type == JavaTokenType.GE) {
      advance(builder, 2);
    }
    else {
      gtToken.drop();
      builder.advanceLexer();
      return;
    }

    gtToken.collapse(type);
  }

  @Nullable
  private static PsiBuilder.Marker parseClassObjectAccess(PsiBuilder builder, PsiBuilder.Marker expr, boolean optionalClassKeyword) {
    final PsiBuilder.Marker mark = builder.mark();
    builder.advanceLexer();

    if (builder.getTokenType() == JavaTokenType.CLASS_KEYWORD) {
      mark.drop();
      builder.advanceLexer();
    }
    else {
      if (!optionalClassKeyword) return null;
      mark.rollbackTo();
      builder.error(JavaPsiBundle.message("class.literal.expected"));
    }

    expr.done(JavaElementType.CLASS_OBJECT_ACCESS_EXPRESSION);
    return expr;
  }

  private static void emptyExpression(final PsiBuilder builder) {
    emptyElement(builder, JavaElementType.EMPTY_EXPRESSION);
  }

  private enum BreakPoint {P1, P2, P4}

  private interface InfixParser {
    /**
     * Starts to parse before the token with binOpType.
     */
    void parse(PrattExpressionParser parser,
               PsiBuilder builder,
               PsiBuilder.Marker beforeLhs,
               IElementType binOpType,
               int currentPrecedence,
               int mode);
  }

  private final static class ParserData {
    private final int myPrecedence;
    private final InfixParser myParser;

    private ParserData(int precedence, InfixParser parser) {
      this.myPrecedence = precedence;
      myParser = parser;
    }
  }

  private static final class AssignmentParser implements InfixParser {
    static final AssignmentParser INSTANCE = new AssignmentParser();

    @Override
    public void parse(PrattExpressionParser parser,
                      PsiBuilder builder,
                      PsiBuilder.Marker beforeLhs,
                      IElementType binOpType,
                      int currentPrecedence,
                      int mode) {
      advanceBinOpToken(builder, binOpType);
      PsiBuilder.Marker right = parser.tryParseWithPrecedenceAtMost(builder, ASSIGNMENT_PRECEDENCE, mode);
      if (right == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }
      done(beforeLhs, JavaElementType.ASSIGNMENT_EXPRESSION);
    }
  }

  private static final class PolyExprParser implements InfixParser {
    static final PolyExprParser INSTANCE = new PolyExprParser();

    @Override
    public void parse(PrattExpressionParser parser,
                      PsiBuilder builder,
                      PsiBuilder.Marker beforeLhs,
                      IElementType binOpType,
                      int currentPrecedence,
                      int mode) {

      int operandCount = 1;
      while (true) {
        advanceBinOpToken(builder, binOpType);
        PsiBuilder.Marker rhs = parser.tryParseWithPrecedenceAtMost(builder, currentPrecedence - 1, mode);
        IElementType nextToken = getBinOpToken(builder);
        if (rhs != null) {
          operandCount++;
        }
        if (rhs == null || nextToken != binOpType) {
          if (rhs == null) {
            error(builder, JavaPsiBundle.message("expected.expression"));
          }
          break;
        }
      }
      done(beforeLhs, operandCount > 2 ? JavaElementType.POLYADIC_EXPRESSION : JavaElementType.BINARY_EXPRESSION);
    }
  }

  private static final class ConditionalExprParser implements InfixParser {
    static final ConditionalExprParser INSTANCE = new ConditionalExprParser();

    @Override
    public void parse(PrattExpressionParser parser,
                      PsiBuilder builder,
                      PsiBuilder.Marker beforeLhs,
                      IElementType binOpType,
                      int currentPrecedence,
                      int mode) {
      builder.advanceLexer(); // skipping ?

      final PsiBuilder.Marker truePart = parser.parse(builder, mode);
      if (truePart == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
        beforeLhs.done(JavaElementType.CONDITIONAL_EXPRESSION);
        return;
      }

      if (builder.getTokenType() != JavaTokenType.COLON) {
        error(builder, JavaPsiBundle.message("expected.colon"));
        beforeLhs.done(JavaElementType.CONDITIONAL_EXPRESSION);
        return;
      }
      builder.advanceLexer();

      final PsiBuilder.Marker falsePart = parser.tryParseWithPrecedenceAtMost(builder, CONDITIONAL_EXPR_PRECEDENCE, mode);
      if (falsePart == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }

      beforeLhs.done(JavaElementType.CONDITIONAL_EXPRESSION);
    }
  }

  private static final class InstanceofParser implements InfixParser {
    static final InstanceofParser INSTANCE = new InstanceofParser();

    @Override
    public void parse(PrattExpressionParser parser,
                      PsiBuilder builder,
                      PsiBuilder.Marker beforeLhs,
                      IElementType binOpType,
                      int currentPrecedence,
                      int mode) {
      builder.advanceLexer(); // skipping 'instanceof'

      JavaParser javaParser = parser.myParser;
      if (!javaParser.getPatternParser().isPattern(builder)) {
        PsiBuilder.Marker type =
          javaParser.getReferenceParser().parseType(builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD);
        if (type == null) {
          error(builder, JavaPsiBundle.message("expected.type"));
        }
      }
      else {
        javaParser.getPatternParser().parsePrimaryPattern(builder, false);
      }
      beforeLhs.done(JavaElementType.INSTANCE_OF_EXPRESSION);
    }
  }
}
