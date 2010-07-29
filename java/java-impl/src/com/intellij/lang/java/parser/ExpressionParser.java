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
import com.intellij.lang.PsiBuilderUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.lang.java.parser.JavaParserUtil.*;


public class ExpressionParser {
  private enum ExprType {
    CONDITIONAL_OR, CONDITIONAL_AND, OR, XOR, AND, EQUALITY, RELATIONAL, SHIFT, ADDITIVE, MULTIPLICATIVE, UNARY, TYPE
  }

  private static final TokenSet ASSIGNMENT_OPS = TokenSet.create(
    JavaTokenType.EQ, JavaTokenType.ASTERISKEQ, JavaTokenType.DIVEQ, JavaTokenType.PERCEQ, JavaTokenType.PLUSEQ, JavaTokenType.MINUSEQ,
    JavaTokenType.LTLTEQ, JavaTokenType.GTGTEQ, JavaTokenType.GTGTGTEQ, JavaTokenType.ANDEQ, JavaTokenType.OREQ, JavaTokenType.XOREQ);
  private static final TokenSet RELATIONAL_OPS = TokenSet.create(JavaTokenType.LT, JavaTokenType.GT, JavaTokenType.LE, JavaTokenType.GE);
  private static final TokenSet POSTFIX_OPS = TokenSet.create(JavaTokenType.PLUSPLUS, JavaTokenType.MINUSMINUS);
  private static final TokenSet PREF_ARITHMETIC_OPS = TokenSet.orSet(POSTFIX_OPS, TokenSet.create(JavaTokenType.PLUS, JavaTokenType.MINUS));
  private static final TokenSet PREFIX_OPS = TokenSet.orSet(PREF_ARITHMETIC_OPS, TokenSet.create(JavaTokenType.TILDE, JavaTokenType.EXCL));
  private static final TokenSet LITERALS = TokenSet.create(
    JavaTokenType.TRUE_KEYWORD, JavaTokenType.FALSE_KEYWORD, JavaTokenType.NULL_KEYWORD, JavaTokenType.INTEGER_LITERAL,
    JavaTokenType.LONG_LITERAL, JavaTokenType.FLOAT_LITERAL, JavaTokenType.DOUBLE_LITERAL, JavaTokenType.CHARACTER_LITERAL,
    JavaTokenType.STRING_LITERAL);
  private static final TokenSet CONDITIONAL_OR_OPS = TokenSet.create(JavaTokenType.OROR);
  private static final TokenSet CONDITIONAL_AND_OPS = TokenSet.create(JavaTokenType.ANDAND);
  private static final TokenSet OR_OPS = TokenSet.create(JavaTokenType.OR);
  private static final TokenSet XOR_OPS = TokenSet.create(JavaTokenType.XOR);
  private static final TokenSet AND_OPS = TokenSet.create(JavaTokenType.AND);
  private static final TokenSet EQUALITY_OPS = TokenSet.create(JavaTokenType.EQEQ, JavaTokenType.NE);
  private static final TokenSet SHIFT_OPS = TokenSet.create(JavaTokenType.LTLT, JavaTokenType.GTGT, JavaTokenType.GTGTGT);
  private static final TokenSet ADDITIVE_OPS = TokenSet.create(JavaTokenType.PLUS, JavaTokenType.MINUS);
  private static final TokenSet MULTIPLICATIVE_OPS = TokenSet.create(JavaTokenType.ASTERISK, JavaTokenType.DIV, JavaTokenType.PERC);
  private static final TokenSet ARGS_LIST_END = TokenSet.create(JavaTokenType.RPARENTH, JavaTokenType.RBRACE, JavaTokenType.RBRACKET);
  private static final TokenSet ARGS_LIST_CONTINUE = TokenSet.create(
    JavaTokenType.IDENTIFIER, TokenType.BAD_CHARACTER, JavaTokenType.COMMA, JavaTokenType.INTEGER_LITERAL, JavaTokenType.STRING_LITERAL);

  private ExpressionParser() { }

  @Nullable
  public static PsiBuilder.Marker parse(final PsiBuilder builder) {
    return parseAssignment(builder);
  }

  @Nullable
  private static PsiBuilder.Marker parseAssignment(final PsiBuilder builder) {
    final PsiBuilder.Marker left = parseConditional(builder);
    if (left == null) return null;

    final IElementType tokenType = getGtTokenType(builder);
    if (ASSIGNMENT_OPS.contains(tokenType)) {
      final PsiBuilder.Marker assignment = left.precede();
      advanceGtToken(builder, tokenType);

      final PsiBuilder.Marker right = parse(builder);
      if (right == null) {
        error(builder, JavaErrorMessages.message("expected.expression"));
      }

      assignment.done(JavaElementType.ASSIGNMENT_EXPRESSION);
      return assignment;
    }

    return left;
  }

  @Nullable
  public static PsiBuilder.Marker parseConditional(final PsiBuilder builder) {
    final PsiBuilder.Marker condition = parseExpression(builder, ExprType.CONDITIONAL_OR);
    if (condition == null) return null;

    if (builder.getTokenType() != JavaTokenType.QUEST) return condition;
    final PsiBuilder.Marker ternary = condition.precede();
    builder.advanceLexer();

    final PsiBuilder.Marker truePart = parse(builder);
    if (truePart == null) {
      error(builder, JavaErrorMessages.message("expected.expression"));
      ternary.done(JavaElementType.CONDITIONAL_EXPRESSION);
      return ternary;
    }

    if (builder.getTokenType() != JavaTokenType.COLON) {
      error(builder, JavaErrorMessages.message("expected.colon"));
      ternary.done(JavaElementType.CONDITIONAL_EXPRESSION);
      return ternary;
    }
    builder.advanceLexer();

    final PsiBuilder.Marker falsePart = parseConditional(builder);
    if (falsePart == null) {
      error(builder, JavaErrorMessages.message("expected.expression"));
      ternary.done(JavaElementType.CONDITIONAL_EXPRESSION);
      return ternary;
    }

    ternary.done(JavaElementType.CONDITIONAL_EXPRESSION);
    return ternary;
  }

  @Nullable
  private static PsiBuilder.Marker parseExpression(final PsiBuilder builder, final ExprType type) {
    switch (type) {
      case CONDITIONAL_OR:
        return parseBinary(builder, ExprType.CONDITIONAL_AND, CONDITIONAL_OR_OPS);

      case CONDITIONAL_AND:
        return parseBinary(builder, ExprType.OR, CONDITIONAL_AND_OPS);

      case OR:
        return parseBinary(builder, ExprType.XOR, OR_OPS);

      case XOR:
        return parseBinary(builder, ExprType.AND, XOR_OPS);

      case AND:
        return parseBinary(builder, ExprType.EQUALITY, AND_OPS);

      case EQUALITY:
        return parseBinary(builder, ExprType.RELATIONAL, EQUALITY_OPS);

      case RELATIONAL:
        return parseRelational(builder);

      case SHIFT:
        return parseBinary(builder, ExprType.ADDITIVE, SHIFT_OPS);

      case ADDITIVE:
        return parseBinary(builder, ExprType.MULTIPLICATIVE, ADDITIVE_OPS);

      case MULTIPLICATIVE:
        return parseBinary(builder, ExprType.UNARY, MULTIPLICATIVE_OPS);

      case UNARY:
        return parseUnary(builder);

      case TYPE:
        final ReferenceParser.TypeInfo typeInfo = ReferenceParser.parseType(builder);
        return typeInfo != null ? typeInfo.marker : null;

      default:
        assert false : "Unexpected type: " + type;
        return null;
    }
  }

  @Nullable
  private static PsiBuilder.Marker parseBinary(final PsiBuilder builder, final ExprType type, final TokenSet ops) {
    PsiBuilder.Marker left = parseExpression(builder, type);
    if (left == null) return null;

    while (true) {
      final IElementType tokenType = getGtTokenType(builder);
      if (tokenType == null || !ops.contains(tokenType)) break;

      final PsiBuilder.Marker binary = left.precede();
      advanceGtToken(builder, tokenType);

      final PsiBuilder.Marker right = parseExpression(builder, type);
      if (right == null) {
        error(builder, JavaErrorMessages.message("expected.expression"));
        binary.done(JavaElementType.BINARY_EXPRESSION);
        return binary;
      }

      binary.done(JavaElementType.BINARY_EXPRESSION);
      left = binary;
    }

    return left;
  }

  @Nullable
  private static PsiBuilder.Marker parseRelational(final PsiBuilder builder) {
    PsiBuilder.Marker left = parseExpression(builder, ExprType.SHIFT);
    if (left == null) return null;

    while (true) {
      final IElementType toCreate;
      final ExprType toParse;
      final IElementType tokenType = getGtTokenType(builder);
      if (RELATIONAL_OPS.contains(tokenType)) {
        toCreate = JavaElementType.BINARY_EXPRESSION;
        toParse = ExprType.SHIFT;
      }
      else if (tokenType == JavaTokenType.INSTANCEOF_KEYWORD) {
        toCreate = JavaElementType.INSTANCE_OF_EXPRESSION;
        toParse = ExprType.TYPE;
      }
      else {
        break;
      }

      final PsiBuilder.Marker expression = left.precede();
      builder.advanceLexer();

      final PsiBuilder.Marker right = parseExpression(builder, toParse);
      if (right == null) {
        error(builder, toParse == ExprType.TYPE ?
                      JavaErrorMessages.message("expected.type") : JavaErrorMessages.message("expected.expression"));
        expression.done(toCreate);
        return expression;
      }

      expression.done(toCreate);
      left = expression;
    }

    return left;
  }

  @Nullable
  private static PsiBuilder.Marker parseUnary(final PsiBuilder builder) {
    final IElementType tokenType = builder.getTokenType();

    if (PREFIX_OPS.contains(tokenType)) {
      final PsiBuilder.Marker unary = builder.mark();
      builder.advanceLexer();

      final PsiBuilder.Marker operand = parseUnary(builder);
      if (operand == null) {
        error(builder, JavaErrorMessages.message("expected.expression"));
      }

      unary.done(JavaElementType.PREFIX_EXPRESSION);
      return unary;
    }
    else if (tokenType == JavaTokenType.LPARENTH) {
      final PsiBuilder.Marker typeCast = builder.mark();
      builder.advanceLexer();

      final ReferenceParser.TypeInfo typeInfo = ReferenceParser.parseType(builder);

      if (typeInfo == null || builder.getTokenType() != JavaTokenType.RPARENTH) {
        typeCast.rollbackTo();
        return parsePostfix(builder);
      }
      builder.advanceLexer();

      if (PREF_ARITHMETIC_OPS.contains(builder.getTokenType())) {
        if (!typeInfo.isPrimitive) {
          typeCast.rollbackTo();
          return parsePostfix(builder);
        }
      }

      final PsiBuilder.Marker expr = parseUnary(builder);
      if (expr == null) {
        if (!typeInfo.isParameterized) {
          typeCast.rollbackTo();
          return parsePostfix(builder);
        }
        else {
          error(builder, JavaErrorMessages.message("expected.expression"));
        }
      }

      typeCast.done(JavaElementType.TYPE_CAST_EXPRESSION);
      return typeCast;
    }
    else {
      return parsePostfix(builder);
    }
  }

  @Nullable
  private static PsiBuilder.Marker parsePostfix(final PsiBuilder builder) {
    PsiBuilder.Marker operand = parsePrimary(builder, false);
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
  private static PsiBuilder.Marker parsePrimary(final PsiBuilder builder, final boolean planB) {
    PsiBuilder.Marker startMarker = builder.mark();

    PsiBuilder.Marker expr = parsePrimaryExpressionStart(builder);
    if (expr == null) {
      startMarker.drop();
      return null;
    }

    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.DOT) {
        final PsiBuilder.Marker dotPos = builder.mark();
        builder.advanceLexer();

        final IElementType dotTokenType = builder.getTokenType();
        if (dotTokenType == JavaTokenType.CLASS_KEYWORD && exprType(expr) == JavaElementType.REFERENCE_EXPRESSION) {
          if (planB) {
            error(builder, JavaErrorMessages.message("expected.identifier"));
            PsiBuilderUtil.drop(startMarker, dotPos);
            return expr;
          }

          final PsiBuilder.Marker copy = startMarker.precede();
          startMarker.rollbackTo();

          final PsiBuilder.Marker classObjAccess = parseClassObjectAccess(builder);
          if (classObjAccess == null) {
            copy.rollbackTo();
            return parsePrimary(builder, true);
          }

          startMarker = copy;
          expr = classObjAccess;
        }
        else if (dotTokenType == JavaTokenType.NEW_KEYWORD) {
          dotPos.drop();
          expr = parseNew(builder, expr);
        }
        else if ((dotTokenType == JavaTokenType.THIS_KEYWORD || dotTokenType == JavaTokenType.SUPER_KEYWORD) &&
                 exprType(expr) == JavaElementType.REFERENCE_EXPRESSION) {
          if (planB) {
            dotPos.rollbackTo();
            startMarker.drop();
            return expr;
          }

          final PsiBuilder.Marker copy = startMarker.precede();
          startMarker.rollbackTo();

          final PsiBuilder.Marker ref = ReferenceParser.parseJavaCodeReference(builder, false, true, false);
          if (ref == null || builder.getTokenType() != JavaTokenType.DOT) {
            copy.rollbackTo();
            return parsePrimary(builder, true);
          }
          builder.advanceLexer();

          if (builder.getTokenType() != dotTokenType) {
            copy.rollbackTo();
            return parsePrimary(builder, true);
          }
          builder.advanceLexer();

          startMarker = copy;
          expr = ref.precede();
          expr.done(dotTokenType == JavaTokenType.THIS_KEYWORD ? JavaElementType.THIS_EXPRESSION : JavaElementType.SUPER_EXPRESSION);
        }
        else if (dotTokenType == JavaTokenType.SUPER_KEYWORD) {
          dotPos.drop();
          final PsiBuilder.Marker refExpr = expr.precede();
          builder.advanceLexer();
          refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
          expr = refExpr;
        }
        else {
          dotPos.drop();
          final PsiBuilder.Marker refExpr = expr.precede();
          ReferenceParser.parseReferenceParameterList(builder, false);

          if (!JavaParserUtil.expectOrError(builder, JavaTokenType.IDENTIFIER, JavaErrorMessages.message("expected.identifier"))) {
            refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
            startMarker.drop();
            return refExpr;
          }

          refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
          expr = refExpr;
        }
      }
      else if (tokenType == JavaTokenType.LPARENTH) {
        if (exprType(expr) != JavaElementType.REFERENCE_EXPRESSION) {
          if (exprType(expr) == JavaElementType.SUPER_EXPRESSION) {
            if (planB) {
              startMarker.drop();
              return expr;
            }

            final PsiBuilder.Marker copy = startMarker.precede();
            startMarker.rollbackTo();

            final PsiBuilder.Marker qualifier = parsePrimaryExpressionStart(builder);
            if (qualifier != null) {
              final PsiBuilder.Marker refExpr = qualifier.precede();
              if (builder.getTokenType() == JavaTokenType.DOT) {
                builder.advanceLexer();
                if (builder.getTokenType() == JavaTokenType.SUPER_KEYWORD) {
                  builder.advanceLexer();
                  refExpr.done(JavaElementType.REFERENCE_EXPRESSION);
                  expr = refExpr;
                  startMarker = copy;
                  continue;
                }
              }
            }

            copy.rollbackTo();
            return parsePrimary(builder, true);
          }
          else {
            startMarker.drop();
            return expr;
          }
        }

        final PsiBuilder.Marker callExpr = expr.precede();
        parseArgumentList(builder);
        callExpr.done(JavaElementType.METHOD_CALL_EXPRESSION);
        expr = callExpr;
      }
      else if (tokenType == JavaTokenType.LBRACKET) {
        if (planB) {
          startMarker.drop();
          return expr;
        }

        builder.advanceLexer();

        if (builder.getTokenType() == JavaTokenType.RBRACKET && exprType(expr) == JavaElementType.REFERENCE_EXPRESSION) {
          final PsiBuilder.Marker copy = startMarker.precede();
          startMarker.rollbackTo();

          final PsiBuilder.Marker classObjAccess = parseClassObjectAccess(builder);
          if (classObjAccess == null) {
            copy.rollbackTo();
            return parsePrimary(builder, true);
          }

          startMarker = copy;
          expr = classObjAccess;
        }
        else {
          final PsiBuilder.Marker arrayAccess = expr.precede();

          final PsiBuilder.Marker index = parse(builder);
          if (index == null) {
            error(builder, JavaErrorMessages.message("expected.expression"));
            arrayAccess.done(JavaElementType.ARRAY_ACCESS_EXPRESSION);
            startMarker.drop();
            return arrayAccess;
          }

          if (builder.getTokenType() != JavaTokenType.RBRACKET) {
            error(builder, JavaErrorMessages.message("expected.rbracket"));
            arrayAccess.done(JavaElementType.ARRAY_ACCESS_EXPRESSION);
            startMarker.drop();
            return arrayAccess;
          }
          builder.advanceLexer();

          arrayAccess.done(JavaElementType.ARRAY_ACCESS_EXPRESSION);
          expr = arrayAccess;
        }
      }
      else {
        startMarker.drop();
        return expr;
      }
    }
  }

  @Nullable
  private static PsiBuilder.Marker parsePrimaryExpressionStart(final PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();

    if (LITERALS.contains(tokenType)) {
      final PsiBuilder.Marker literal = builder.mark();
      builder.advanceLexer();
      literal.done(JavaElementType.LITERAL_EXPRESSION);
      return literal;
    }
    if (tokenType == JavaTokenType.LPARENTH) {
      final PsiBuilder.Marker parenth = builder.mark();
      builder.advanceLexer();

      final PsiBuilder.Marker inner = parse(builder);
      if (inner == null) {
        error(builder, JavaErrorMessages.message("expected.expression"));
      }

      JavaParserUtil.expectOrError(builder, JavaTokenType.RPARENTH, JavaErrorMessages.message("expected.rparen"));

      parenth.done(JavaElementType.PARENTH_EXPRESSION);
      return parenth;
    }
    if (tokenType == JavaTokenType.LBRACE) {
      return parseArrayInitializer(builder);
    }

    PsiBuilder.Marker annotation = null;
    final PsiBuilder.Marker beforeAnnotation = builder.mark();
    if (tokenType == JavaTokenType.AT) {
      annotation = DeclarationParser.parseAnnotations(builder);
      tokenType = builder.getTokenType();
    }

    if (tokenType == JavaTokenType.IDENTIFIER) {
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
      beforeAnnotation.drop();
      return refExpr;
    }

    if (annotation != null) {
      beforeAnnotation.rollbackTo();
      tokenType = builder.getTokenType();
    }
    else {
      beforeAnnotation.drop();
    }

    if (tokenType == JavaTokenType.THIS_KEYWORD) {
      final PsiBuilder.Marker expr = builder.mark();
      builder.mark().done(JavaElementType.REFERENCE_PARAMETER_LIST);
      builder.advanceLexer();
      expr.done(builder.getTokenType() != JavaTokenType.LPARENTH ? JavaElementType.THIS_EXPRESSION : JavaElementType.REFERENCE_EXPRESSION);
      return expr;
    }
    if (tokenType == JavaTokenType.SUPER_KEYWORD) {
      final PsiBuilder.Marker expr = builder.mark();
      builder.mark().done(JavaElementType.REFERENCE_PARAMETER_LIST);
      builder.advanceLexer();
      expr.done(builder.getTokenType() != JavaTokenType.LPARENTH ? JavaElementType.SUPER_EXPRESSION : JavaElementType.REFERENCE_EXPRESSION);
      return expr;
    }
    if (tokenType == JavaTokenType.NEW_KEYWORD) {
      return parseNew(builder, null);
    }
    if (ElementType.PRIMITIVE_TYPE_BIT_SET.contains(tokenType)) {
      return parseClassObjectAccess(builder);
    }

    return null;
  }

  @Nullable
  private static PsiBuilder.Marker parseArrayInitializer(final PsiBuilder builder) {
    if (builder.getTokenType() != JavaTokenType.LBRACE) return null;

    final PsiBuilder.Marker arrayInit = builder.mark();
    builder.advanceLexer();

    boolean expressionMissed = false;
    PsiBuilder.Marker lastComma = null;
    while (true) {
      if (builder.getTokenType() == JavaTokenType.RBRACE) {
        builder.advanceLexer();
        break;
      }

      if (builder.getTokenType() == null) {
        error(builder, JavaErrorMessages.message("expected.rbrace"));
        break;
      }

      if (expressionMissed && lastComma != null) {
        // before comma must be an expression
        lastComma.precede().errorBefore(JavaErrorMessages.message("expected.expression"), lastComma);
        lastComma.drop();
        lastComma = null;
      }

      final PsiBuilder.Marker arg = parse(builder);
      if (arg == null) {
        if (builder.getTokenType() == JavaTokenType.COMMA) {
          expressionMissed = true;
          lastComma = builder.mark();
        }
        else {
          error(builder, JavaErrorMessages.message("expected.rbrace"));
          break;
        }
      }

      final IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.COMMA) {
        builder.advanceLexer();
      }
      else if (tokenType != JavaTokenType.RBRACE) {
        error(builder, JavaErrorMessages.message("expected.comma"));
      }
    }

    if (lastComma != null) {
      lastComma.drop();
    }

    arrayInit.done(JavaElementType.ARRAY_INITIALIZER_EXPRESSION);
    return arrayInit;
  }

  @NotNull
  private static PsiBuilder.Marker parseNew(final PsiBuilder builder, final PsiBuilder.Marker start) {
    final PsiBuilder.Marker newExpr = (start != null ? start.precede() : builder.mark());
    builder.advanceLexer();

    ReferenceParser.parseReferenceParameterList(builder, false);

    final PsiBuilder.Marker refOrType;
    final boolean parseAnnotations = areTypeAnnotationsSupported(builder) && builder.getTokenType() == JavaTokenType.AT;

    final IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.IDENTIFIER || parseAnnotations) {
      refOrType = ReferenceParser.parseJavaCodeReference(builder, true, true, parseAnnotations);
      if (refOrType == null) {
        error(builder, JavaErrorMessages.message("expected.identifier"));
        newExpr.done(JavaElementType.NEW_EXPRESSION);
        return newExpr;
      }
    }
    else if (ElementType.PRIMITIVE_TYPE_BIT_SET.contains(tokenType)) {
      refOrType = null;
      builder.advanceLexer();
    }
    else {
      error(builder, JavaErrorMessages.message("expected.identifier"));
      newExpr.done(JavaElementType.NEW_EXPRESSION);
      return newExpr;
    }

    if (refOrType != null && builder.getTokenType() == JavaTokenType.LPARENTH) {
      parseArgumentList(builder);
      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        final PsiBuilder.Marker classElement = refOrType.precede();
        DeclarationParser.parseClassBodyWithBraces(builder, false, false);
        classElement.done(JavaElementType.ANONYMOUS_CLASS);
      }
    }
    else {
      if (builder.getTokenType() != JavaTokenType.LBRACKET) {
        error(builder, refOrType == null ?
                      JavaErrorMessages.message("expected.lbracket") : JavaErrorMessages.message("expected.lparen.or.lbracket"));
        newExpr.done(JavaElementType.NEW_EXPRESSION);
        return newExpr;
      }

      int bracketCount = 0;
      int dimCount = 0;
      while (true) {
        if (builder.getTokenType() != JavaTokenType.LBRACKET) break;
        builder.advanceLexer();

        if (bracketCount == dimCount) {
          final PsiBuilder.Marker dimExpr = parse(builder);
          if (dimExpr != null) {
            dimCount++;
          }
        }
        bracketCount++;

        if (!JavaParserUtil.expectOrError(builder, JavaTokenType.RBRACKET, JavaErrorMessages.message("expected.rbracket"))) {
          newExpr.done(JavaElementType.NEW_EXPRESSION);
          return newExpr;
        }
      }

      if (dimCount == 0) {
        if (builder.getTokenType() == JavaTokenType.LBRACE) {
          parseArrayInitializer(builder);
        }
        else {
          error(builder, JavaErrorMessages.message("expected.array.initializer"));
        }
      }
    }

    newExpr.done(JavaElementType.NEW_EXPRESSION);
    return newExpr;
  }

  @Nullable
  private static PsiBuilder.Marker parseClassObjectAccess(final PsiBuilder builder) {
    final PsiBuilder.Marker expr = builder.mark();

    final PsiBuilder.Marker type = ReferenceParser.parseType(builder, false, false);
    if (type == null) {
      expr.drop();
      return null;
    }

    if (builder.getTokenType() != JavaTokenType.DOT) {
      expr.rollbackTo();
      return null;
    }
    builder.advanceLexer();

    if (builder.getTokenType() != JavaTokenType.CLASS_KEYWORD) {
      expr.rollbackTo();
      return null;
    }
    builder.advanceLexer();

    expr.done(JavaElementType.CLASS_OBJECT_ACCESS_EXPRESSION);
    return expr;
  }

  @NotNull
  public static PsiBuilder.Marker parseArgumentList(final PsiBuilder builder) {
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
          error(builder, JavaErrorMessages.message("expected.comma.or.rparen"));
          emptyExpression(builder);
        }
      }
      first = false;

      final PsiBuilder.Marker arg = parse(builder);
      if (arg == null) {
        if (!hasError) {
          error(builder, JavaErrorMessages.message("expected.expression"));
          emptyExpression(builder);
        }
        if (!ARGS_LIST_CONTINUE.contains(builder.getTokenType())) break;
        if (builder.getTokenType() != JavaTokenType.COMMA && !builder.eof()) {
          builder.advanceLexer();
        }
      }
    }

    JavaParserUtil.expectOrError(builder, JavaTokenType.RPARENTH, JavaErrorMessages.message("expected.rparen"));

    list.done(JavaElementType.EXPRESSION_LIST);
    return list;
  }

  private static void emptyExpression(final PsiBuilder builder) {
    emptyElement(builder, JavaElementType.EMPTY_EXPRESSION);
  }

  @Nullable
  private static IElementType getGtTokenType(final PsiBuilder builder) {
    final PsiBuilder.Marker sp = builder.mark();

    IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.GT) {
      builder.advanceLexer();
      if (builder.getTokenType() == JavaTokenType.GT) {
        builder.advanceLexer();
        if (builder.getTokenType() == JavaTokenType.GT) {
          builder.advanceLexer();
          if (builder.getTokenType() == JavaTokenType.EQ) {
            tokenType = JavaTokenType.GTGTGTEQ;
          }
          else {
            tokenType = JavaTokenType.GTGTGT;
          }
        }
        else if (builder.getTokenType() == JavaTokenType.EQ) {
          tokenType = JavaTokenType.GTGTEQ;
        }
        else {
          tokenType = JavaTokenType.GTGT;
        }
      }
    }

    sp.rollbackTo();
    return tokenType;
  }

  private static void advanceGtToken(final PsiBuilder builder, final IElementType type) {
    final PsiBuilder.Marker gtToken = builder.mark();

    if (type == JavaTokenType.GTGTGTEQ) {
      PsiBuilderUtil.advance(builder, 4);
    }
    else if (type == JavaTokenType.GTGTGT) {
      PsiBuilderUtil.advance(builder, 3);
    }
    else if (type == JavaTokenType.GTGTEQ) {
      PsiBuilderUtil.advance(builder, 3);
    }
    else if (type == JavaTokenType.GTGT) {
      PsiBuilderUtil.advance(builder, 2);
    }
    else {
      gtToken.drop();
      builder.advanceLexer();
      return;
    }

    gtToken.collapse(type);
  }
}
