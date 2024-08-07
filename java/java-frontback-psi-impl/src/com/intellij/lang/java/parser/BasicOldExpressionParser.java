// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesBinders;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.AbstractBasicJavaElementTypeFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Function;

import static com.intellij.lang.PsiBuilderUtil.*;
import static com.intellij.lang.java.parser.BasicJavaParserUtil.*;
import static com.intellij.psi.impl.source.BasicElementTypes.*;

//suppress to be clear, what type is used
@SuppressWarnings("UnnecessarilyQualifiedStaticUsage")
public class BasicOldExpressionParser {
  private enum ExprType {
    CONDITIONAL_OR, CONDITIONAL_AND, OR, XOR, AND, EQUALITY, RELATIONAL, SHIFT, ADDITIVE, MULTIPLICATIVE, UNARY, TYPE
  }

  static final TokenSet RELATIONAL_OPS = TokenSet.create(JavaTokenType.LT, JavaTokenType.GT, JavaTokenType.LE, JavaTokenType.GE);
  static final TokenSet POSTFIX_OPS = TokenSet.create(JavaTokenType.PLUSPLUS, JavaTokenType.MINUSMINUS);
  static final TokenSet PREF_ARITHMETIC_OPS = TokenSet.orSet(POSTFIX_OPS, TokenSet.create(JavaTokenType.PLUS, JavaTokenType.MINUS));
  static final TokenSet PREFIX_OPS = TokenSet.orSet(PREF_ARITHMETIC_OPS, TokenSet.create(JavaTokenType.TILDE, JavaTokenType.EXCL));
  static final TokenSet CONDITIONAL_OR_OPS = TokenSet.create(JavaTokenType.OROR);
  static final TokenSet CONDITIONAL_AND_OPS = TokenSet.create(JavaTokenType.ANDAND);
  static final TokenSet OR_OPS = TokenSet.create(JavaTokenType.OR);
  static final TokenSet XOR_OPS = TokenSet.create(JavaTokenType.XOR);
  static final TokenSet AND_OPS = TokenSet.create(JavaTokenType.AND);
  static final TokenSet EQUALITY_OPS = TokenSet.create(JavaTokenType.EQEQ, JavaTokenType.NE);
  static final TokenSet ARGS_LIST_END = TokenSet.create(JavaTokenType.RPARENTH, JavaTokenType.RBRACE, JavaTokenType.RBRACKET);
  static final TokenSet ARGS_LIST_CONTINUE = TokenSet.create(
    JavaTokenType.IDENTIFIER, TokenType.BAD_CHARACTER, JavaTokenType.COMMA, JavaTokenType.INTEGER_LITERAL, JavaTokenType.STRING_LITERAL);
  static final TokenSet THIS_OR_SUPER = TokenSet.create(JavaTokenType.THIS_KEYWORD, JavaTokenType.SUPER_KEYWORD);
  static final TokenSet ID_OR_SUPER = TokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.SUPER_KEYWORD);
  final TokenSet TYPE_START;

  private final BasicJavaParser myParser;
  private final AbstractBasicJavaElementTypeFactory.JavaElementTypeContainer myJavaElementTypeContainer;

  public BasicOldExpressionParser(@NotNull BasicJavaParser javaParser) {
    myParser = javaParser;
    myJavaElementTypeContainer = javaParser.getJavaElementTypeFactory().getContainer();
    TYPE_START = TokenSet.orSet(
      BASIC_PRIMITIVE_TYPE_BIT_SET, TokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.AT));
  }

  @Nullable
  public PsiBuilder.Marker parse(@NotNull PsiBuilder builder) {
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
  @Nullable
  public PsiBuilder.Marker parseCaseLabel(@NotNull PsiBuilder builder) {
    return myParser.getStatementParser().parseCaseLabel(builder).first;
  }

  @Nullable PsiBuilder.Marker parseAssignment(final PsiBuilder builder) {
    return parseAssignment(builder, 0);
  }

  @Nullable PsiBuilder.Marker parseAssignment(final PsiBuilder builder, final int mode) {
    final PsiBuilder.Marker left = parseConditional(builder, mode);
    if (left == null) return null;

    final IElementType tokenType = getGtTokenType(builder);
    if (tokenType != null && BasicExpressionParser.ASSIGNMENT_OPS.contains(tokenType)) {
      final PsiBuilder.Marker assignment = left.precede();
      advanceGtToken(builder, tokenType);

      final PsiBuilder.Marker right = parse(builder);
      if (right == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }

      assignment.done(myJavaElementTypeContainer.ASSIGNMENT_EXPRESSION);
      return assignment;
    }

    return left;
  }

  @Nullable
  PsiBuilder.Marker parseConditionalAnd(final PsiBuilder builder, final int mode) {
    return parseExpression(builder, ExprType.CONDITIONAL_AND, mode);
  }

  @Nullable
  public PsiBuilder.Marker parseConditional(final PsiBuilder builder, final int mode) {
    final PsiBuilder.Marker condition = parseExpression(builder, ExprType.CONDITIONAL_OR, mode);
    if (condition == null) return null;

    if (builder.getTokenType() != JavaTokenType.QUEST) return condition;
    final PsiBuilder.Marker ternary = condition.precede();
    builder.advanceLexer();

    final PsiBuilder.Marker truePart = parse(builder);
    if (truePart == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
      ternary.done(myJavaElementTypeContainer.CONDITIONAL_EXPRESSION);
      return ternary;
    }

    if (builder.getTokenType() != JavaTokenType.COLON) {
      error(builder, JavaPsiBundle.message("expected.colon"));
      ternary.done(myJavaElementTypeContainer.CONDITIONAL_EXPRESSION);
      return ternary;
    }
    builder.advanceLexer();

    final PsiBuilder.Marker falsePart = parseConditional(builder, mode);
    if (falsePart == null) {
      error(builder, JavaPsiBundle.message("expected.expression"));
      ternary.done(myJavaElementTypeContainer.CONDITIONAL_EXPRESSION);
      return ternary;
    }

    ternary.done(myJavaElementTypeContainer.CONDITIONAL_EXPRESSION);
    return ternary;
  }

  @Nullable
  private PsiBuilder.Marker parseExpression(final PsiBuilder builder, final ExprType type, final int mode) {
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
        return parseBinary(builder, ExprType.ADDITIVE, BasicExpressionParser.SHIFT_OPS, mode);

      case ADDITIVE:
        return parseBinary(builder, ExprType.MULTIPLICATIVE, BasicExpressionParser.ADDITIVE_OPS, mode);

      case MULTIPLICATIVE:
        return parseBinary(builder, ExprType.UNARY, BasicExpressionParser.MULTIPLICATIVE_OPS, mode);

      case UNARY:
        return parseUnary(builder, mode);

      case TYPE:
        return myParser.getReferenceParser().parseType(builder, BasicReferenceParser.EAT_LAST_DOT | BasicReferenceParser.WILDCARD);

      default:
        assert false : "Unexpected type: " + type;
        return null;
    }
  }

  @Nullable
  private PsiBuilder.Marker parseBinary(final PsiBuilder builder, final ExprType type, final TokenSet ops, final int mode) {
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
        result.done(operandCount > 2 ? myJavaElementTypeContainer.POLYADIC_EXPRESSION : myJavaElementTypeContainer.BINARY_EXPRESSION);
        if (right == null) break;
        currentExprTokenType = tokenType;
        operandCount = 1;
      }
    }

    return result;
  }

  @Nullable
  private PsiBuilder.Marker parseRelational(final PsiBuilder builder, final int mode) {
    PsiBuilder.Marker left = parseExpression(builder, ExprType.SHIFT, mode);
    if (left == null) return null;

    IElementType tokenType;
    while ((tokenType = getGtTokenType(builder)) != null) {
      final IElementType toCreate;
      final boolean patternExpected; // Otherwise ExprType.SHIFT is expected
      if (RELATIONAL_OPS.contains(tokenType)) {
        toCreate = myJavaElementTypeContainer.BINARY_EXPRESSION;
        patternExpected = false;
      }
      else if (tokenType == JavaTokenType.INSTANCEOF_KEYWORD) {
        toCreate = myJavaElementTypeContainer.INSTANCE_OF_EXPRESSION;
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

      unary.done(myJavaElementTypeContainer.PREFIX_EXPRESSION);
      return unary;
    }
    else if (tokenType == JavaTokenType.LPARENTH) {
      final PsiBuilder.Marker typeCast = builder.mark();
      builder.advanceLexer();

      BasicReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(
        builder, BasicReferenceParser.EAT_LAST_DOT |
                 BasicReferenceParser.WILDCARD |
                 BasicReferenceParser.CONJUNCTIONS |
                 BasicReferenceParser.INCOMPLETE_ANNO);
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

      typeCast.done(myJavaElementTypeContainer.TYPE_CAST_EXPRESSION);
      return typeCast;
    }
    else if (tokenType == JavaTokenType.SWITCH_KEYWORD) {
      return myParser.getStatementParser().parseExprInParenthWithBlock(builder, myJavaElementTypeContainer.SWITCH_EXPRESSION, true);
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
      postfix.done(myJavaElementTypeContainer.POSTFIX_EXPRESSION);
      operand = postfix;
    }

    return operand;
  }

  private enum BreakPoint {P1, P2, P4}

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

        if (dotTokenType == JavaTokenType.CLASS_KEYWORD && exprType(expr) == myJavaElementTypeContainer.REFERENCE_EXPRESSION) {
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
          builder.mark().done(myJavaElementTypeContainer.REFERENCE_PARAMETER_LIST);
          builder.advanceLexer();
          refExpr.done(myJavaElementTypeContainer.REFERENCE_EXPRESSION);
          expr = refExpr;
        }
        else if (dotTokenType == JavaTokenType.STRING_TEMPLATE_BEGIN || dotTokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN) {
          dotPos.drop();
          expr = parseStringTemplate(builder, expr, dotTokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN);
        }
        else if (dotTokenType == JavaTokenType.STRING_LITERAL || dotTokenType == JavaTokenType.TEXT_BLOCK_LITERAL) {
          dotPos.drop();
          final PsiBuilder.Marker templateExpression = expr.precede();
          final PsiBuilder.Marker literal = builder.mark();
          builder.advanceLexer();
          literal.done(myJavaElementTypeContainer.LITERAL_EXPRESSION);
          templateExpression.done(myJavaElementTypeContainer.TEMPLATE_EXPRESSION);
          expr = templateExpression;
        }
        else if (THIS_OR_SUPER.contains(dotTokenType) && exprType(expr) == myJavaElementTypeContainer.REFERENCE_EXPRESSION) {
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
          expr.done(dotTokenType == JavaTokenType.THIS_KEYWORD
                    ? myJavaElementTypeContainer.THIS_EXPRESSION
                    : myJavaElementTypeContainer.SUPER_EXPRESSION);
        }
        else {
          PsiBuilder.Marker refExpr = expr.precede();

          myParser.getReferenceParser().parseReferenceParameterList(builder, false, false);

          if (!expect(builder, ID_OR_SUPER)) {
            dotPos.rollbackTo();
            builder.advanceLexer();
            myParser.getReferenceParser().parseReferenceParameterList(builder, false, false);
            error(builder, JavaPsiBundle.message("expected.identifier"));
            refExpr.done(myJavaElementTypeContainer.REFERENCE_EXPRESSION);
            startMarker.drop();
            return refExpr;
          }

          dotPos.drop();
          refExpr.done(myJavaElementTypeContainer.REFERENCE_EXPRESSION);
          expr = refExpr;
        }
      }
      else if (tokenType == JavaTokenType.LPARENTH) {
        if (exprType(expr) != myJavaElementTypeContainer.REFERENCE_EXPRESSION) {
          startMarker.drop();
          return expr;
        }

        PsiBuilder.Marker callExpr = expr.precede();
        parseArgumentList(builder);
        callExpr.done(myJavaElementTypeContainer.METHOD_CALL_EXPRESSION);
        expr = callExpr;
      }
      else if (tokenType == JavaTokenType.LBRACKET) {
        if (breakPoint == BreakPoint.P4) {
          startMarker.drop();
          return expr;
        }

        builder.advanceLexer();

        if (builder.getTokenType() == JavaTokenType.RBRACKET && exprType(expr) == myJavaElementTypeContainer.REFERENCE_EXPRESSION) {
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
            arrayAccess.done(myJavaElementTypeContainer.ARRAY_ACCESS_EXPRESSION);
            startMarker.drop();
            return arrayAccess;
          }

          if (builder.getTokenType() != JavaTokenType.RBRACKET) {
            error(builder, JavaPsiBundle.message("expected.rbracket"));
            arrayAccess.done(myJavaElementTypeContainer.ARRAY_ACCESS_EXPRESSION);
            startMarker.drop();
            return arrayAccess;
          }
          builder.advanceLexer();

          arrayAccess.done(myJavaElementTypeContainer.ARRAY_ACCESS_EXPRESSION);
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

    if (tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN || tokenType == JavaTokenType.STRING_TEMPLATE_BEGIN) {
      return parseStringTemplate(builder, null, tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN);
    }

    if (BASIC_ALL_LITERALS.contains(tokenType)) {
      final PsiBuilder.Marker literal = builder.mark();
      builder.advanceLexer();
      literal.done(myJavaElementTypeContainer.LITERAL_EXPRESSION);
      return literal;
    }

    if (tokenType == JavaTokenType.LBRACE) {
      return parseArrayInitializer(builder);
    }

    if (tokenType == JavaTokenType.NEW_KEYWORD) {
      return parseNew(builder, null);
    }

    if (tokenType == JavaTokenType.LPARENTH) {
      if (!BitUtil.isSet(mode, BasicExpressionParser.FORBID_LAMBDA_MASK)) {
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

      if (!expect(builder, JavaTokenType.RPARENTH)) {
        if (inner != null) {
          error(builder, JavaPsiBundle.message("expected.rparen"));
        }
      }

      parenth.done(myJavaElementTypeContainer.PARENTH_EXPRESSION);
      return parenth;
    }

    if (TYPE_START.contains(tokenType)) {
      final PsiBuilder.Marker mark = builder.mark();

      final BasicReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(builder, 0);
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
      if (!BitUtil.isSet(mode, BasicExpressionParser.FORBID_LAMBDA_MASK) && builder.lookAhead(1) == JavaTokenType.ARROW) {
        return parseLambdaExpression(builder, false);
      }

      final PsiBuilder.Marker refExpr;
      if (annotation != null) {
        final PsiBuilder.Marker refParam = annotation.precede();
        refParam.doneBefore(myJavaElementTypeContainer.REFERENCE_PARAMETER_LIST, annotation);
        refExpr = refParam.precede();
      }
      else {
        refExpr = builder.mark();
        builder.mark().done(myJavaElementTypeContainer.REFERENCE_PARAMETER_LIST);
      }

      builder.advanceLexer();
      refExpr.done(myJavaElementTypeContainer.REFERENCE_EXPRESSION);
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
        builder.mark().done(myJavaElementTypeContainer.REFERENCE_PARAMETER_LIST);
      }
      builder.advanceLexer();
      expr.done(builder.getTokenType() == JavaTokenType.LPARENTH
                ? myJavaElementTypeContainer.REFERENCE_EXPRESSION
                : tokenType == JavaTokenType.THIS_KEYWORD
                  ? myJavaElementTypeContainer.THIS_EXPRESSION
                  : myJavaElementTypeContainer.SUPER_EXPRESSION);
      return expr;
    }

    return null;
  }

  @NotNull
  private PsiBuilder.Marker parseArrayInitializer(PsiBuilder builder) {
    return parseArrayInitializer(builder, myJavaElementTypeContainer.ARRAY_INITIALIZER_EXPRESSION, this::parse, "expected.expression");
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

  private PsiBuilder.Marker parseStringTemplate(PsiBuilder builder, PsiBuilder.Marker start, boolean textBlock) {
    final PsiBuilder.Marker templateExpression = start == null ? builder.mark() : start.precede();
    final PsiBuilder.Marker template = builder.mark();
    IElementType tokenType;
    do {
      builder.advanceLexer();
      tokenType = builder.getTokenType();
      if (textBlock
          ? tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_MID || tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_END
          : tokenType == JavaTokenType.STRING_TEMPLATE_MID || tokenType == JavaTokenType.STRING_TEMPLATE_END) {
        emptyExpression(builder);
      }
      else {
        parse(builder);
        tokenType = builder.getTokenType();
      }
    }
    while (textBlock ? tokenType == JavaTokenType.TEXT_BLOCK_TEMPLATE_MID : tokenType == JavaTokenType.STRING_TEMPLATE_MID);
    if (textBlock ? tokenType != JavaTokenType.TEXT_BLOCK_TEMPLATE_END : tokenType != JavaTokenType.STRING_TEMPLATE_END) {
      builder.error(JavaPsiBundle.message("expected.template.fragment"));
    }
    else {
      builder.advanceLexer();
    }
    template.done(myJavaElementTypeContainer.TEMPLATE);
    templateExpression.done(myJavaElementTypeContainer.TEMPLATE_EXPRESSION);
    return templateExpression;
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
        newExpr.done(myJavaElementTypeContainer.NEW_EXPRESSION);
        return newExpr;
      }
    }
    else if (BASIC_PRIMITIVE_TYPE_BIT_SET.contains(tokenType)) {
      refOrType = null;
      builder.advanceLexer();
    }
    else {
      error(builder, JavaPsiBundle.message("expected.identifier"));
      newExpr.done(myJavaElementTypeContainer.NEW_EXPRESSION);
      return newExpr;
    }

    if (refOrType != null && builder.getTokenType() == JavaTokenType.LPARENTH) {
      parseArgumentList(builder);
      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        final PsiBuilder.Marker classElement = refOrType.precede();
        myParser.getDeclarationParser().parseClassBodyWithBraces(builder, false, false);
        classElement.done(myJavaElementTypeContainer.ANONYMOUS_CLASS);
      }
      newExpr.done(myJavaElementTypeContainer.NEW_EXPRESSION);
      return newExpr;
    }

    anno = myParser.getDeclarationParser().parseAnnotations(builder);

    if (builder.getTokenType() != JavaTokenType.LBRACKET) {
      rollbackTo(anno);
      error(builder, JavaPsiBundle.message(refOrType == null ? "expected.lbracket" : "expected.lparen.or.lbracket"));
      newExpr.done(myJavaElementTypeContainer.NEW_EXPRESSION);
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
        final PsiBuilder.Marker dimExpr = parse(builder);
        if (dimExpr != null) {
          dimCount++;
        }
      }
      bracketCount++;

      if (!expectOrError(builder, JavaTokenType.RBRACKET, "expected.rbracket")) {
        newExpr.done(myJavaElementTypeContainer.NEW_EXPRESSION);
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

    newExpr.done(myJavaElementTypeContainer.NEW_EXPRESSION);
    return newExpr;
  }

  @Nullable
  private PsiBuilder.Marker parseClassAccessOrMethodReference(PsiBuilder builder) {
    PsiBuilder.Marker expr = builder.mark();

    boolean primitive = BASIC_PRIMITIVE_TYPE_BIT_SET.contains(builder.getTokenType());
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

  @Nullable
  private PsiBuilder.Marker parseClassObjectAccess(PsiBuilder builder, PsiBuilder.Marker expr, boolean optionalClassKeyword) {
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

    expr.done(myJavaElementTypeContainer.CLASS_OBJECT_ACCESS_EXPRESSION);
    return expr;
  }

  @NotNull
  private PsiBuilder.Marker parseMethodReference(final PsiBuilder builder, final PsiBuilder.Marker start) {
    builder.advanceLexer();

    myParser.getReferenceParser().parseReferenceParameterList(builder, false, false);

    if (!expect(builder, JavaTokenType.IDENTIFIER) && !expect(builder, JavaTokenType.NEW_KEYWORD)) {
      error(builder, JavaPsiBundle.message("expected.identifier"));
    }

    start.done(myJavaElementTypeContainer.METHOD_REF_EXPRESSION);
    return start;
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
             BASIC_MODIFIER_BIT_SET.contains(nextToken1) ||
             BASIC_PRIMITIVE_TYPE_BIT_SET.contains(nextToken1)) {
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
        BasicReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(
          builder, BasicReferenceParser.ELLIPSIS | BasicReferenceParser.WILDCARD);
        if (typeInfo != null) {
          IElementType t = builder.getTokenType();
          lambda = t == JavaTokenType.IDENTIFIER ||
                   t == JavaTokenType.THIS_KEYWORD ||
                   t == JavaTokenType.RPARENTH && builder.lookAhead(1) == JavaTokenType.ARROW;
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

  @Nullable
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
      body = parse(builder);
    }

    if (body == null) {
      builder.error(JavaPsiBundle.message("expected.lbrace"));
    }

    start.done(myJavaElementTypeContainer.LAMBDA_EXPRESSION);
    return start;
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

      final PsiBuilder.Marker arg = parse(builder);
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

    list.done(myJavaElementTypeContainer.EXPRESSION_LIST);
    if (!closed) {
      list.setCustomEdgeTokenBinders(null, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    }
    return list;
  }

  private void emptyExpression(final PsiBuilder builder) {
    emptyElement(builder, myJavaElementTypeContainer.EMPTY_EXPRESSION);
  }

  @Nullable
  private static IElementType getGtTokenType(final PsiBuilder builder) {
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

  private static void advanceGtToken(final PsiBuilder builder, final IElementType type) {
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
}
