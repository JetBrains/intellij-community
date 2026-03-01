// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesBinders;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Function;

import static com.intellij.lang.PsiBuilderUtil.advance;
import static com.intellij.lang.PsiBuilderUtil.drop;
import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.PsiBuilderUtil.rollbackTo;
import static com.intellij.lang.java.parser.JavaParserUtil.emptyElement;
import static com.intellij.lang.java.parser.JavaParserUtil.error;
import static com.intellij.lang.java.parser.JavaParserUtil.expectOrError;
import static com.intellij.lang.java.parser.JavaParserUtil.exprType;
import static com.intellij.psi.JavaTokenType.AND;
import static com.intellij.psi.JavaTokenType.ANDAND;
import static com.intellij.psi.JavaTokenType.ARROW;
import static com.intellij.psi.JavaTokenType.AT;
import static com.intellij.psi.JavaTokenType.BAD_CHARACTER;
import static com.intellij.psi.JavaTokenType.CLASS_KEYWORD;
import static com.intellij.psi.JavaTokenType.COLON;
import static com.intellij.psi.JavaTokenType.COMMA;
import static com.intellij.psi.JavaTokenType.DOT;
import static com.intellij.psi.JavaTokenType.DOUBLE_COLON;
import static com.intellij.psi.JavaTokenType.EQ;
import static com.intellij.psi.JavaTokenType.EQEQ;
import static com.intellij.psi.JavaTokenType.EXCL;
import static com.intellij.psi.JavaTokenType.GE;
import static com.intellij.psi.JavaTokenType.GT;
import static com.intellij.psi.JavaTokenType.GTGT;
import static com.intellij.psi.JavaTokenType.GTGTEQ;
import static com.intellij.psi.JavaTokenType.GTGTGT;
import static com.intellij.psi.JavaTokenType.GTGTGTEQ;
import static com.intellij.psi.JavaTokenType.IDENTIFIER;
import static com.intellij.psi.JavaTokenType.INSTANCEOF_KEYWORD;
import static com.intellij.psi.JavaTokenType.INTEGER_LITERAL;
import static com.intellij.psi.JavaTokenType.LBRACE;
import static com.intellij.psi.JavaTokenType.LBRACKET;
import static com.intellij.psi.JavaTokenType.LE;
import static com.intellij.psi.JavaTokenType.LPARENTH;
import static com.intellij.psi.JavaTokenType.LT;
import static com.intellij.psi.JavaTokenType.MINUS;
import static com.intellij.psi.JavaTokenType.MINUSMINUS;
import static com.intellij.psi.JavaTokenType.NE;
import static com.intellij.psi.JavaTokenType.NEW_KEYWORD;
import static com.intellij.psi.JavaTokenType.OR;
import static com.intellij.psi.JavaTokenType.OROR;
import static com.intellij.psi.JavaTokenType.PLUS;
import static com.intellij.psi.JavaTokenType.PLUSPLUS;
import static com.intellij.psi.JavaTokenType.QUEST;
import static com.intellij.psi.JavaTokenType.RBRACE;
import static com.intellij.psi.JavaTokenType.RBRACKET;
import static com.intellij.psi.JavaTokenType.RPARENTH;
import static com.intellij.psi.JavaTokenType.STRING_LITERAL;
import static com.intellij.psi.JavaTokenType.STRING_TEMPLATE_BEGIN;
import static com.intellij.psi.JavaTokenType.STRING_TEMPLATE_END;
import static com.intellij.psi.JavaTokenType.STRING_TEMPLATE_MID;
import static com.intellij.psi.JavaTokenType.SUPER_KEYWORD;
import static com.intellij.psi.JavaTokenType.SWITCH_KEYWORD;
import static com.intellij.psi.JavaTokenType.TEXT_BLOCK_LITERAL;
import static com.intellij.psi.JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN;
import static com.intellij.psi.JavaTokenType.TEXT_BLOCK_TEMPLATE_END;
import static com.intellij.psi.JavaTokenType.TEXT_BLOCK_TEMPLATE_MID;
import static com.intellij.psi.JavaTokenType.THIS_KEYWORD;
import static com.intellij.psi.JavaTokenType.TILDE;
import static com.intellij.psi.JavaTokenType.VAR_KEYWORD;
import static com.intellij.psi.JavaTokenType.XOR;
import static com.intellij.psi.impl.source.tree.ElementType.ADDITIVE_OPS;
import static com.intellij.psi.impl.source.tree.ElementType.ALL_LITERALS;
import static com.intellij.psi.impl.source.tree.ElementType.ANONYMOUS_CLASS;
import static com.intellij.psi.impl.source.tree.ElementType.ARRAY_ACCESS_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.ARRAY_INITIALIZER_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.ASSIGNMENT_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.ASSIGNMENT_OPS;
import static com.intellij.psi.impl.source.tree.ElementType.BINARY_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.CLASS_OBJECT_ACCESS_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.CONDITIONAL_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.EMPTY_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.EXPRESSION_LIST;
import static com.intellij.psi.impl.source.tree.ElementType.INSTANCE_OF_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.LAMBDA_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.LITERAL_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.METHOD_CALL_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.METHOD_REF_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.MODIFIER_BIT_SET;
import static com.intellij.psi.impl.source.tree.ElementType.MULTIPLICATIVE_OPS;
import static com.intellij.psi.impl.source.tree.ElementType.NEW_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.PARENTH_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.POLYADIC_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.POSTFIX_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.PREFIX_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.PRIMITIVE_TYPE_BIT_SET;
import static com.intellij.psi.impl.source.tree.ElementType.REFERENCE_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.REFERENCE_PARAMETER_LIST;
import static com.intellij.psi.impl.source.tree.ElementType.SHIFT_OPS;
import static com.intellij.psi.impl.source.tree.ElementType.SUPER_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.SWITCH_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.TEMPLATE;
import static com.intellij.psi.impl.source.tree.ElementType.TEMPLATE_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.THIS_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.TYPE_CAST_EXPRESSION;

/**
 * @deprecated Use the new Java syntax library instead.
 * See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public class OldExpressionParser {
  static final TokenSet RELATIONAL_OPS = TokenSet.create(LT, GT, LE, GE);
  static final TokenSet POSTFIX_OPS = TokenSet.create(PLUSPLUS, MINUSMINUS);
  static final TokenSet PREF_ARITHMETIC_OPS = TokenSet.orSet(POSTFIX_OPS, TokenSet.create(PLUS, MINUS));
  static final TokenSet PREFIX_OPS = TokenSet.orSet(PREF_ARITHMETIC_OPS, TokenSet.create(TILDE, EXCL));
  static final TokenSet CONDITIONAL_OR_OPS = TokenSet.create(OROR);
  static final TokenSet CONDITIONAL_AND_OPS = TokenSet.create(ANDAND);
  static final TokenSet OR_OPS = TokenSet.create(OR);
  static final TokenSet XOR_OPS = TokenSet.create(XOR);
  static final TokenSet AND_OPS = TokenSet.create(AND);
  static final TokenSet EQUALITY_OPS = TokenSet.create(EQEQ, NE);
  static final TokenSet ARGS_LIST_END = TokenSet.create(RPARENTH, RBRACE, RBRACKET);
  static final TokenSet ARGS_LIST_CONTINUE = TokenSet.create(
    IDENTIFIER, BAD_CHARACTER, COMMA, INTEGER_LITERAL, STRING_LITERAL);
  static final TokenSet THIS_OR_SUPER = TokenSet.create(THIS_KEYWORD, SUPER_KEYWORD);
  static final TokenSet ID_OR_SUPER = TokenSet.create(IDENTIFIER, SUPER_KEYWORD);
  final TokenSet TYPE_START;
  private final JavaParser myParser;

  public OldExpressionParser(@NotNull JavaParser javaParser) {
    this.myParser = javaParser;
    this.TYPE_START = TokenSet.orSet(
      PRIMITIVE_TYPE_BIT_SET, TokenSet.create(IDENTIFIER, AT));
  }

  public @Nullable PsiBuilder.Marker parse(@NotNull PsiBuilder builder) {
    return parseAssignment(builder);
  }

  //for compatibility
  @SuppressWarnings("unused")
  @Nullable
  PsiBuilder.Marker parse(@NotNull PsiBuilder builder, final int mode) {
    return parseAssignment(builder, mode);
  }

  //for compatibility
  @SuppressWarnings("unused")
  public @Nullable PsiBuilder.Marker parseCaseLabel(@NotNull PsiBuilder builder) {
    return myParser.getStatementParser().parseCaseLabel(builder).first;
  }

  @Nullable PsiBuilder.Marker parseAssignment(final PsiBuilder builder) {
    return parseAssignment(builder, 0);
  }

  @Nullable PsiBuilder.Marker parseAssignment(final PsiBuilder builder, final int mode) {
    final PsiBuilder.Marker left = parseConditional(builder, mode);
    if (left == null) return null;

    final IElementType tokenType = getGtTokenType(builder);
    if (tokenType != null && ASSIGNMENT_OPS.contains(tokenType)) {
      final PsiBuilder.Marker assignment = left.precede();
      advanceGtToken(builder, tokenType);

      final PsiBuilder.Marker right = parse(builder);
      if (right == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }

      assignment.done(ASSIGNMENT_EXPRESSION);
      return assignment;
    }

    return left;
  }

  @Nullable
  PsiBuilder.Marker parseConditionalAnd(final PsiBuilder builder, final int mode) {
    return parseExpression(builder, ExprType.CONDITIONAL_AND, mode);
  }

  public @Nullable PsiBuilder.Marker parseConditional(final PsiBuilder builder, final int mode) {
    final PsiBuilder.Marker condition = parseExpression(builder, ExprType.CONDITIONAL_OR, mode);
    if (condition == null) return null;

    if (builder.getTokenType() != QUEST) return condition;
    final PsiBuilder.Marker ternary = condition.precede();
    builder.advanceLexer();

    final PsiBuilder.Marker truePart = parse(builder);
    if (truePart == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
      ternary.done(CONDITIONAL_EXPRESSION);
      return ternary;
    }

    if (builder.getTokenType() != COLON) {
      error(builder, JavaPsiBundle.message("expected.colon"));
      ternary.done(CONDITIONAL_EXPRESSION);
      return ternary;
    }
    builder.advanceLexer();

    final PsiBuilder.Marker falsePart = parseConditional(builder, mode);
    if (falsePart == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
      ternary.done(CONDITIONAL_EXPRESSION);
      return ternary;
    }

    ternary.done(CONDITIONAL_EXPRESSION);
    return ternary;
  }

  private @Nullable PsiBuilder.Marker parseExpression(final PsiBuilder builder, final ExprType type, final int mode) {
    switch (type) {
      case CONDITIONAL_OR:
        return parseBinary(builder, ExprType.CONDITIONAL_AND, CONDITIONAL_OR_OPS, mode);

      case CONDITIONAL_AND:
        return parseBinary(builder, ExprType.OR, CONDITIONAL_AND_OPS, mode);

      case OR:
        return parseBinary(builder, ExprType.XOR, OR_OPS, mode);

      case XOR:
        return parseBinary(builder, ExprType.AND, XOR_OPS, mode);

      case AND:
        return parseBinary(builder, ExprType.EQUALITY, AND_OPS, mode);

      case EQUALITY:
        return parseBinary(builder, ExprType.RELATIONAL, EQUALITY_OPS, mode);

      case RELATIONAL:
        return parseRelational(builder, mode);

      case SHIFT:
        return parseBinary(builder, ExprType.ADDITIVE, SHIFT_OPS, mode);

      case ADDITIVE:
        return parseBinary(builder, ExprType.MULTIPLICATIVE, ADDITIVE_OPS, mode);

      case MULTIPLICATIVE:
        return parseBinary(builder, ExprType.UNARY, MULTIPLICATIVE_OPS, mode);

      case UNARY:
        return parseUnary(builder, mode);

      case TYPE:
        return myParser.getReferenceParser().parseType(builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD);

      default:
        assert false : "Unexpected type: " + type;
        return null;
    }
  }

  private @Nullable PsiBuilder.Marker parseBinary(final PsiBuilder builder, final ExprType type, final TokenSet ops, final int mode) {
    PsiBuilder.Marker result = parseExpression(builder, type, mode);
    if (result == null) return null;
    int operandCount = 1;

    IElementType tokenType = getGtTokenType(builder);
    IElementType currentExprTokenType = tokenType;
    while (true) {
      if (tokenType == null || !ops.contains(tokenType)) break;

      advanceGtToken(builder, tokenType);

      final PsiBuilder.Marker right = parseExpression(builder, type, mode);
      if (right == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }
      operandCount++;
      tokenType = getGtTokenType(builder);
      if (tokenType == null || !ops.contains(tokenType) || tokenType != currentExprTokenType) {
        // save
        result = result.precede();
        result.done(operandCount > 2 ? POLYADIC_EXPRESSION : BINARY_EXPRESSION);
        if (right == null) break;
        currentExprTokenType = tokenType;
        operandCount = 1;
      }
    }

    return result;
  }

  private @Nullable PsiBuilder.Marker parseRelational(final PsiBuilder builder, final int mode) {
    PsiBuilder.Marker left = parseExpression(builder, ExprType.SHIFT, mode);
    if (left == null) return null;

    IElementType tokenType;
    while ((tokenType = getGtTokenType(builder)) != null) {
      final IElementType toCreate;
      final boolean patternExpected; // Otherwise ExprType.SHIFT is expected
      if (RELATIONAL_OPS.contains(tokenType)) {
        toCreate = BINARY_EXPRESSION;
        patternExpected = false;
      }
      else if (tokenType == INSTANCEOF_KEYWORD) {
        toCreate = INSTANCE_OF_EXPRESSION;
        patternExpected = true;
      }
      else {
        break;
      }

      final PsiBuilder.Marker expression = left.precede();
      advanceGtToken(builder, tokenType);
      if (patternExpected) {
        if (!myParser.getPatternParser().isPattern(builder)) {
          PsiBuilder.Marker type = parseExpression(builder, ExprType.TYPE, mode);
          if (type == null) {
            error(builder, JavaPsiBundle.message("expected.type"));
          }
          expression.done(toCreate);
          return expression;
        }
        myParser.getPatternParser().parsePrimaryPattern(builder, false);
      }
      else {
        final PsiBuilder.Marker right = parseExpression(builder, ExprType.SHIFT, mode);
        if (right == null) {
          error(builder, JavaPsiBundle.message("expected.expression"));
          expression.done(toCreate);
          return expression;
        }
      }

      expression.done(toCreate);
      left = expression;
    }

    return left;
  }

  private @Nullable PsiBuilder.Marker parseUnary(final PsiBuilder builder, final int mode) {
    final IElementType tokenType = builder.getTokenType();

    if (PREFIX_OPS.contains(tokenType)) {
      final PsiBuilder.Marker unary = builder.mark();
      builder.advanceLexer();

      final PsiBuilder.Marker operand = parseUnary(builder, mode);
      if (operand == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }

      unary.done(PREFIX_EXPRESSION);
      return unary;
    }
    else if (tokenType == LPARENTH) {
      final PsiBuilder.Marker typeCast = builder.mark();
      builder.advanceLexer();

      ReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(
        builder, ReferenceParser.EAT_LAST_DOT |
                 ReferenceParser.WILDCARD |
                 ReferenceParser.CONJUNCTIONS |
                 ReferenceParser.INCOMPLETE_ANNO);
      if (typeInfo == null || !expect(builder, RPARENTH)) {
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

      typeCast.done(TYPE_CAST_EXPRESSION);
      return typeCast;
    }
    else if (tokenType == SWITCH_KEYWORD) {
      return myParser.getStatementParser().parseExprInParenthWithBlock(builder, SWITCH_EXPRESSION, true);
    }
    else {
      return parsePostfix(builder, mode);
    }
  }

  private @Nullable PsiBuilder.Marker parsePostfix(final PsiBuilder builder, final int mode) {
    PsiBuilder.Marker operand = parsePrimary(builder, null, -1, mode);
    if (operand == null) return null;

    while (POSTFIX_OPS.contains(builder.getTokenType())) {
      final PsiBuilder.Marker postfix = operand.precede();
      builder.advanceLexer();
      postfix.done(POSTFIX_EXPRESSION);
      operand = postfix;
    }

    return operand;
  }

  private @Nullable PsiBuilder.Marker parsePrimary(PsiBuilder builder, @Nullable BreakPoint breakPoint, int breakOffset, final int mode) {
    PsiBuilder.Marker startMarker = builder.mark();

    PsiBuilder.Marker expr = parsePrimaryExpressionStart(builder, mode);
    if (expr == null) {
      startMarker.drop();
      return null;
    }

    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == DOT) {
        final PsiBuilder.Marker dotPos = builder.mark();
        final int dotOffset = builder.getCurrentOffset();
        builder.advanceLexer();

        IElementType dotTokenType = builder.getTokenType();
        if (dotTokenType == AT) {
          myParser.getDeclarationParser().parseAnnotations(builder);
          dotTokenType = builder.getTokenType();
        }

        if (dotTokenType == CLASS_KEYWORD && exprType(expr) == REFERENCE_EXPRESSION) {
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
        else if (dotTokenType == NEW_KEYWORD) {
          dotPos.drop();
          expr = parseNew(builder, expr);
        }
        else if (dotTokenType == SUPER_KEYWORD && builder.lookAhead(1) == LPARENTH) {
          dotPos.drop();
          PsiBuilder.Marker refExpr = expr.precede();
          builder.mark().done(REFERENCE_PARAMETER_LIST);
          builder.advanceLexer();
          refExpr.done(REFERENCE_EXPRESSION);
          expr = refExpr;
        }
        else if (dotTokenType == STRING_TEMPLATE_BEGIN || dotTokenType == TEXT_BLOCK_TEMPLATE_BEGIN) {
          dotPos.drop();
          expr = parseStringTemplate(builder, expr, dotTokenType == TEXT_BLOCK_TEMPLATE_BEGIN);
        }
        else if (dotTokenType == STRING_LITERAL || dotTokenType == TEXT_BLOCK_LITERAL) {
          dotPos.drop();
          final PsiBuilder.Marker templateExpression = expr.precede();
          final PsiBuilder.Marker literal = builder.mark();
          builder.advanceLexer();
          literal.done(LITERAL_EXPRESSION);
          templateExpression.done(TEMPLATE_EXPRESSION);
          expr = templateExpression;
        }
        else if (THIS_OR_SUPER.contains(dotTokenType) && exprType(expr) == REFERENCE_EXPRESSION) {
          if (breakPoint == BreakPoint.P2 && builder.getCurrentOffset() == breakOffset) {
            dotPos.rollbackTo();
            startMarker.drop();
            return expr;
          }

          PsiBuilder.Marker copy = startMarker.precede();
          int offset = builder.getCurrentOffset();
          startMarker.rollbackTo();

          PsiBuilder.Marker ref = myParser.getReferenceParser().parseJavaCodeReference(builder, false, true, false, false);
          if (ref == null || builder.getTokenType() != DOT || builder.getCurrentOffset() != dotOffset) {
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
          expr.done(dotTokenType == THIS_KEYWORD
                    ? THIS_EXPRESSION
                    : SUPER_EXPRESSION);
        }
        else {
          PsiBuilder.Marker refExpr = expr.precede();

          myParser.getReferenceParser().parseReferenceParameterList(builder, false, false);

          if (!expect(builder, ID_OR_SUPER)) {
            dotPos.rollbackTo();
            builder.advanceLexer();
            myParser.getReferenceParser().parseReferenceParameterList(builder, false, false);
            error(builder, JavaPsiBundle.message("expected.identifier"));
            refExpr.done(REFERENCE_EXPRESSION);
            startMarker.drop();
            return refExpr;
          }

          dotPos.drop();
          refExpr.done(REFERENCE_EXPRESSION);
          expr = refExpr;
        }
      }
      else if (tokenType == LPARENTH) {
        if (exprType(expr) != REFERENCE_EXPRESSION) {
          startMarker.drop();
          return expr;
        }

        PsiBuilder.Marker callExpr = expr.precede();
        parseArgumentList(builder);
        callExpr.done(METHOD_CALL_EXPRESSION);
        expr = callExpr;
      }
      else if (tokenType == LBRACKET) {
        if (breakPoint == BreakPoint.P4) {
          startMarker.drop();
          return expr;
        }

        builder.advanceLexer();

        if (builder.getTokenType() == RBRACKET && exprType(expr) == REFERENCE_EXPRESSION) {
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

          final PsiBuilder.Marker index = parse(builder);
          if (index == null) {
            error(builder, JavaPsiBundle.message("expected.expression"));
            arrayAccess.done(ARRAY_ACCESS_EXPRESSION);
            startMarker.drop();
            return arrayAccess;
          }

          if (builder.getTokenType() != RBRACKET) {
            error(builder, JavaPsiBundle.message("expected.rbracket"));
            arrayAccess.done(ARRAY_ACCESS_EXPRESSION);
            startMarker.drop();
            return arrayAccess;
          }
          builder.advanceLexer();

          arrayAccess.done(ARRAY_ACCESS_EXPRESSION);
          expr = arrayAccess;
        }
      }
      else if (tokenType == DOUBLE_COLON) {
        return parseMethodReference(builder, startMarker);
      }
      else {
        startMarker.drop();
        return expr;
      }
    }
  }

  private @Nullable PsiBuilder.Marker parsePrimaryExpressionStart(final PsiBuilder builder, final int mode) {
    IElementType tokenType = builder.getTokenType();

    if (tokenType == TEXT_BLOCK_TEMPLATE_BEGIN || tokenType == STRING_TEMPLATE_BEGIN) {
      return parseStringTemplate(builder, null, tokenType == TEXT_BLOCK_TEMPLATE_BEGIN);
    }

    if (ALL_LITERALS.contains(tokenType)) {
      final PsiBuilder.Marker literal = builder.mark();
      builder.advanceLexer();
      literal.done(LITERAL_EXPRESSION);
      return literal;
    }

    if (tokenType == LBRACE) {
      return parseArrayInitializer(builder);
    }

    if (tokenType == NEW_KEYWORD) {
      return parseNew(builder, null);
    }

    if (tokenType == LPARENTH) {
      if (!BitUtil.isSet(mode, ExpressionParser.FORBID_LAMBDA_MASK)) {
        final PsiBuilder.Marker lambda = parseLambdaAfterParenth(builder);
        if (lambda != null) {
          return lambda;
        }
      }

      final PsiBuilder.Marker parenth = builder.mark();
      builder.advanceLexer();

      final PsiBuilder.Marker inner = parse(builder);
      if (inner == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }

      if (!expect(builder, RPARENTH)) {
        if (inner != null) {
          error(builder, JavaPsiBundle.message("expected.rparen"));
        }
      }

      parenth.done(PARENTH_EXPRESSION);
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
    if (tokenType == AT) {
      annotation = myParser.getDeclarationParser().parseAnnotations(builder);
      tokenType = builder.getTokenType();
    }

    if (tokenType == VAR_KEYWORD) {
      builder.remapCurrentToken(tokenType = IDENTIFIER);
    }
    if (tokenType == IDENTIFIER) {
      if (!BitUtil.isSet(mode, ExpressionParser.FORBID_LAMBDA_MASK) && builder.lookAhead(1) == ARROW) {
        return parseLambdaExpression(builder, false);
      }

      final PsiBuilder.Marker refExpr;
      if (annotation != null) {
        final PsiBuilder.Marker refParam = annotation.precede();
        refParam.doneBefore(REFERENCE_PARAMETER_LIST, annotation);
        refExpr = refParam.precede();
      }
      else {
        refExpr = builder.mark();
        builder.mark().done(REFERENCE_PARAMETER_LIST);
      }

      builder.advanceLexer();
      refExpr.done(REFERENCE_EXPRESSION);
      return refExpr;
    }

    if (annotation != null) {
      annotation.rollbackTo();
      tokenType = builder.getTokenType();
    }

    PsiBuilder.Marker expr = null;
    if (tokenType == LT) {
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
        builder.mark().done(REFERENCE_PARAMETER_LIST);
      }
      builder.advanceLexer();
      expr.done(builder.getTokenType() == LPARENTH
                ? REFERENCE_EXPRESSION
                : tokenType == THIS_KEYWORD
                  ? THIS_EXPRESSION
                  : SUPER_EXPRESSION);
      return expr;
    }

    return null;
  }

  private @NotNull PsiBuilder.Marker parseArrayInitializer(PsiBuilder builder) {
    return parseArrayInitializer(builder, ARRAY_INITIALIZER_EXPRESSION, this::parse, "expected.expression");
  }

  public @NotNull PsiBuilder.Marker parseArrayInitializer(@NotNull PsiBuilder builder,
                                                          @NotNull IElementType type,
                                                          @NotNull Function<? super PsiBuilder, PsiBuilder.Marker> elementParser,
                                                          @NotNull @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String missingElementKey) {
    PsiBuilder.Marker arrayInit = builder.mark();
    builder.advanceLexer();

    boolean first = true;
    while (true) {
      if (builder.getTokenType() == RBRACE) {
        builder.advanceLexer();
        break;
      }

      if (builder.getTokenType() == null) {
        error(builder, JavaPsiBundle.message("expected.rbrace"));
        break;
      }

      if (elementParser.apply(builder) == null) {
        if (builder.getTokenType() == COMMA) {
          if (first && builder.lookAhead(1) == RBRACE) {
            advance(builder, 2);
            break;
          }
          builder.error(JavaPsiBundle.message(missingElementKey));
        }
        else if (builder.getTokenType() != RBRACE) {
          error(builder, JavaPsiBundle.message("expected.rbrace"));
          break;
        }
      }

      first = false;

      IElementType tokenType = builder.getTokenType();
      if (!expect(builder, COMMA) && tokenType != RBRACE) {
        error(builder, JavaPsiBundle.message("expected.comma"));
      }
    }

    arrayInit.done(type);
    return arrayInit;
  }

  private PsiBuilder.Marker parseStringTemplate(PsiBuilder builder, PsiBuilder.Marker start, boolean textBlock) {
    final PsiBuilder.Marker templateExpression = start == null ? builder.mark() : start.precede();
    final PsiBuilder.Marker template = builder.mark();
    IElementType tokenType;
    do {
      builder.advanceLexer();
      tokenType = builder.getTokenType();
      if (textBlock
          ? tokenType == TEXT_BLOCK_TEMPLATE_MID || tokenType == TEXT_BLOCK_TEMPLATE_END
          : tokenType == STRING_TEMPLATE_MID || tokenType == STRING_TEMPLATE_END) {
        emptyExpression(builder);
      }
      else {
        parse(builder);
        tokenType = builder.getTokenType();
      }
    }
    while (textBlock ? tokenType == TEXT_BLOCK_TEMPLATE_MID : tokenType == STRING_TEMPLATE_MID);
    if (textBlock ? tokenType != TEXT_BLOCK_TEMPLATE_END : tokenType != STRING_TEMPLATE_END) {
      builder.error(JavaPsiBundle.message("expected.template.fragment"));
    }
    else {
      builder.advanceLexer();
    }
    template.done(TEMPLATE);
    templateExpression.done(TEMPLATE_EXPRESSION);
    return templateExpression;
  }

  private @NotNull PsiBuilder.Marker parseNew(PsiBuilder builder, @Nullable PsiBuilder.Marker start) {
    PsiBuilder.Marker newExpr = (start != null ? start.precede() : builder.mark());
    builder.advanceLexer();

    myParser.getReferenceParser().parseReferenceParameterList(builder, false, true);

    PsiBuilder.Marker refOrType;
    PsiBuilder.Marker anno = myParser.getDeclarationParser().parseAnnotations(builder);
    IElementType tokenType = builder.getTokenType();
    if (tokenType == IDENTIFIER) {
      rollbackTo(anno);
      refOrType = myParser.getReferenceParser().parseJavaCodeReference(builder, true, true, true, true);
      if (refOrType == null) {
        error(builder, JavaPsiBundle.message("expected.identifier"));
        newExpr.done(NEW_EXPRESSION);
        return newExpr;
      }
    }
    else if (PRIMITIVE_TYPE_BIT_SET.contains(tokenType)) {
      refOrType = null;
      builder.advanceLexer();
    }
    else {
      error(builder, JavaPsiBundle.message("expected.identifier"));
      newExpr.done(NEW_EXPRESSION);
      return newExpr;
    }

    if (refOrType != null && builder.getTokenType() == LPARENTH) {
      parseArgumentList(builder);
      if (builder.getTokenType() == LBRACE) {
        final PsiBuilder.Marker classElement = refOrType.precede();
        myParser.getDeclarationParser().parseClassBodyWithBraces(builder, false, false);
        classElement.done(ANONYMOUS_CLASS);
      }
      newExpr.done(NEW_EXPRESSION);
      return newExpr;
    }

    anno = myParser.getDeclarationParser().parseAnnotations(builder);

    if (builder.getTokenType() != LBRACKET) {
      rollbackTo(anno);
      error(builder, JavaPsiBundle.message(refOrType == null ? "expected.lbracket" : "expected.lparen.or.lbracket"));
      newExpr.done(NEW_EXPRESSION);
      return newExpr;
    }

    int bracketCount = 0;
    int dimCount = 0;
    while (true) {
      anno = myParser.getDeclarationParser().parseAnnotations(builder);

      if (builder.getTokenType() != LBRACKET) {
        rollbackTo(anno);
        break;
      }
      builder.advanceLexer();

      if (bracketCount == dimCount) {
        final PsiBuilder.Marker dimExpr = parse(builder);
        if (dimExpr != null) {
          dimCount++;
        }
      }
      bracketCount++;

      if (!expectOrError(builder, RBRACKET, "expected.rbracket")) {
        newExpr.done(NEW_EXPRESSION);
        return newExpr;
      }
    }

    if (dimCount == 0) {
      if (builder.getTokenType() == LBRACE) {
        parseArrayInitializer(builder);
      }
      else {
        error(builder, JavaPsiBundle.message("expected.array.initializer"));
      }
    }

    newExpr.done(NEW_EXPRESSION);
    return newExpr;
  }

  private @Nullable PsiBuilder.Marker parseClassAccessOrMethodReference(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();

    boolean primitive = PRIMITIVE_TYPE_BIT_SET.contains(builder.getTokenType());
    if (myParser.getReferenceParser().parseType(builder, 0) == null) {
      expr.drop();
      return null;
    }

    PsiBuilder.Marker result = parseClassAccessOrMethodReference(builder, expr, primitive);
    if (result == null) expr.rollbackTo();
    return result;
  }

  private @Nullable PsiBuilder.Marker parseClassAccessOrMethodReference(PsiBuilder builder,
                                                                        PsiBuilder.Marker expr,
                                                                        boolean optionalClassKeyword) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType == DOT) {
      return parseClassObjectAccess(builder, expr, optionalClassKeyword);
    }
    else if (tokenType == DOUBLE_COLON) {
      return parseMethodReference(builder, expr);
    }

    return null;
  }

  private @Nullable PsiBuilder.Marker parseClassObjectAccess(PsiBuilder builder, PsiBuilder.Marker expr, boolean optionalClassKeyword) {
    final PsiBuilder.Marker mark = builder.mark();
    builder.advanceLexer();

    if (builder.getTokenType() == CLASS_KEYWORD) {
      mark.drop();
      builder.advanceLexer();
    }
    else {
      if (!optionalClassKeyword) return null;
      mark.rollbackTo();
      builder.error(JavaPsiBundle.message("class.literal.expected"));
    }

    expr.done(CLASS_OBJECT_ACCESS_EXPRESSION);
    return expr;
  }

  private @NotNull PsiBuilder.Marker parseMethodReference(final PsiBuilder builder, final PsiBuilder.Marker start) {
    builder.advanceLexer();

    myParser.getReferenceParser().parseReferenceParameterList(builder, false, false);

    if (!expect(builder, IDENTIFIER) && !expect(builder, NEW_KEYWORD)) {
      error(builder, JavaPsiBundle.message("expected.identifier"));
    }

    start.done(METHOD_REF_EXPRESSION);
    return start;
  }

  private @Nullable PsiBuilder.Marker parseLambdaAfterParenth(final PsiBuilder builder) {
    final boolean isLambda;
    final boolean isTyped;

    final IElementType nextToken1 = builder.lookAhead(1);
    final IElementType nextToken2 = builder.lookAhead(2);
    if (nextToken1 == RPARENTH && nextToken2 == ARROW) {
      isLambda = true;
      isTyped = false;
    }
    else if (nextToken1 == AT ||
             MODIFIER_BIT_SET.contains(nextToken1) ||
             PRIMITIVE_TYPE_BIT_SET.contains(nextToken1)) {
      isLambda = true;
      isTyped = true;
    }
    else if (nextToken1 == IDENTIFIER) {
      if (nextToken2 == COMMA || nextToken2 == RPARENTH && builder.lookAhead(3) == ARROW) {
        isLambda = true;
        isTyped = false;
      }
      else if (nextToken2 == ARROW) {
        isLambda = false;
        isTyped = false;
      }
      else {
        boolean lambda = false;

        PsiBuilder.Marker marker = builder.mark();
        builder.advanceLexer();
        ReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(
          builder, ReferenceParser.ELLIPSIS | ReferenceParser.WILDCARD);
        if (typeInfo != null) {
          IElementType t = builder.getTokenType();
          lambda = t == IDENTIFIER ||
                   t == THIS_KEYWORD ||
                   t == RPARENTH && builder.lookAhead(1) == ARROW;
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

  private @Nullable PsiBuilder.Marker parseLambdaExpression(final PsiBuilder builder, final boolean typed) {
    final PsiBuilder.Marker start = builder.mark();

    myParser.getDeclarationParser().parseLambdaParameterList(builder, typed);

    if (!expect(builder, ARROW)) {
      start.rollbackTo();
      return null;
    }

    final PsiBuilder.Marker body;
    if (builder.getTokenType() == LBRACE) {
      body = myParser.getStatementParser().parseCodeBlock(builder);
    }
    else {
      body = parse(builder);
    }

    if (body == null) {
      builder.error(JavaPsiBundle.message("expected.lbrace"));
    }

    start.done(LAMBDA_EXPRESSION);
    return start;
  }

  public @NotNull PsiBuilder.Marker parseArgumentList(final PsiBuilder builder) {
    final PsiBuilder.Marker list = builder.mark();
    builder.advanceLexer();

    boolean first = true;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (first && (ARGS_LIST_END.contains(tokenType) || builder.eof())) break;
      if (!first && !ARGS_LIST_CONTINUE.contains(tokenType)) break;

      boolean hasError = false;
      if (!first) {
        if (builder.getTokenType() == COMMA) {
          builder.advanceLexer();
        }
        else {
          hasError = true;
          error(builder, JavaPsiBundle.message("expected.comma.or.rparen"));
          emptyExpression(builder);
        }
      }
      first = false;

      final PsiBuilder.Marker arg = parse(builder);
      if (arg == null) {
        if (!hasError) {
          error(builder, JavaPsiBundle.message("expected.expression"));
          emptyExpression(builder);
        }
        if (!ARGS_LIST_CONTINUE.contains(builder.getTokenType())) break;
        if (builder.getTokenType() != COMMA && !builder.eof()) {
          builder.advanceLexer();
        }
      }
    }

    boolean closed = true;
    if (!expect(builder, RPARENTH)) {
      if (first) {
        error(builder, JavaPsiBundle.message("expected.rparen"));
      }
      else {
        error(builder, JavaPsiBundle.message("expected.comma.or.rparen"));
      }
      closed = false;
    }

    list.done(EXPRESSION_LIST);
    if (!closed) {
      list.setCustomEdgeTokenBinders(null, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    }
    return list;
  }

  private void emptyExpression(final PsiBuilder builder) {
    emptyElement(builder, EMPTY_EXPRESSION);
  }

  private static @Nullable IElementType getGtTokenType(final PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType != GT) return tokenType;

    if (builder.rawLookup(1) == GT) {
      if (builder.rawLookup(2) == GT) {
        if (builder.rawLookup(3) == EQ) {
          tokenType = GTGTGTEQ;
        }
        else {
          tokenType = GTGTGT;
        }
      }
      else if (builder.rawLookup(2) == EQ) {
        tokenType = GTGTEQ;
      }
      else {
        tokenType = GTGT;
      }
    }
    else if (builder.rawLookup(1) == EQ) {
      tokenType = GE;
    }

    return tokenType;
  }

  private static void advanceGtToken(final PsiBuilder builder, final IElementType type) {
    final PsiBuilder.Marker gtToken = builder.mark();

    if (type == GTGTGTEQ) {
      advance(builder, 4);
    }
    else if (type == GTGTGT || type == GTGTEQ) {
      advance(builder, 3);
    }
    else if (type == GTGT || type == GE) {
      advance(builder, 2);
    }
    else {
      gtToken.drop();
      builder.advanceLexer();
      return;
    }

    gtToken.collapse(type);
  }

  private enum ExprType {
    CONDITIONAL_OR, CONDITIONAL_AND, OR, XOR, AND, EQUALITY, RELATIONAL, SHIFT, ADDITIVE, MULTIPLICATIVE, UNARY, TYPE
  }

  private enum BreakPoint {P1, P2, P4}
}
