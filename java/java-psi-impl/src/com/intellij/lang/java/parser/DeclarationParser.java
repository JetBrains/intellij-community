// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.impl.source.OldParserWhiteSpaceAndCommentSetHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.braceMatchingBuilder;
import static com.intellij.lang.java.parser.JavaParserUtil.done;
import static com.intellij.lang.java.parser.JavaParserUtil.emptyElement;
import static com.intellij.lang.java.parser.JavaParserUtil.error;
import static com.intellij.lang.java.parser.JavaParserUtil.expectOrError;
import static com.intellij.lang.java.parser.JavaParserUtil.exprType;
import static com.intellij.lang.java.parser.JavaParserUtil.getLanguageLevel;
import static com.intellij.lang.java.parser.JavaParserUtil.stoppingBuilder;
import static com.intellij.psi.impl.source.tree.ElementType.ANNOTATION;
import static com.intellij.psi.impl.source.tree.ElementType.ANNOTATION_ARRAY_INITIALIZER;
import static com.intellij.psi.impl.source.tree.ElementType.ANNOTATION_METHOD;
import static com.intellij.psi.impl.source.tree.ElementType.ANNOTATION_PARAMETER_LIST;
import static com.intellij.psi.impl.source.tree.ElementType.ARROW;
import static com.intellij.psi.impl.source.tree.ElementType.AT;
import static com.intellij.psi.impl.source.tree.ElementType.CLASS;
import static com.intellij.psi.impl.source.tree.ElementType.CLASS_INITIALIZER;
import static com.intellij.psi.impl.source.tree.ElementType.CLASS_KEYWORD_BIT_SET;
import static com.intellij.psi.impl.source.tree.ElementType.COMMA;
import static com.intellij.psi.impl.source.tree.ElementType.DEFAULT_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.DOT;
import static com.intellij.psi.impl.source.tree.ElementType.ENUM_CONSTANT;
import static com.intellij.psi.impl.source.tree.ElementType.ENUM_CONSTANT_INITIALIZER;
import static com.intellij.psi.impl.source.tree.ElementType.ENUM_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.EQ;
import static com.intellij.psi.impl.source.tree.ElementType.EXPRESSION_LIST;
import static com.intellij.psi.impl.source.tree.ElementType.EXTENDS_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.EXTENDS_LIST;
import static com.intellij.psi.impl.source.tree.ElementType.FIELD;
import static com.intellij.psi.impl.source.tree.ElementType.IDENTIFIER;
import static com.intellij.psi.impl.source.tree.ElementType.IMPLEMENTS_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.IMPLEMENTS_LIST;
import static com.intellij.psi.impl.source.tree.ElementType.INTERFACE_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.KEYWORD_BIT_SET;
import static com.intellij.psi.impl.source.tree.ElementType.LBRACE;
import static com.intellij.psi.impl.source.tree.ElementType.LBRACKET;
import static com.intellij.psi.impl.source.tree.ElementType.LOCAL_VARIABLE;
import static com.intellij.psi.impl.source.tree.ElementType.LPARENTH;
import static com.intellij.psi.impl.source.tree.ElementType.LT;
import static com.intellij.psi.impl.source.tree.ElementType.METHOD;
import static com.intellij.psi.impl.source.tree.ElementType.METHOD_CALL_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.MINUS;
import static com.intellij.psi.impl.source.tree.ElementType.MODIFIER_BIT_SET;
import static com.intellij.psi.impl.source.tree.ElementType.MODIFIER_LIST;
import static com.intellij.psi.impl.source.tree.ElementType.NAME_VALUE_PAIR;
import static com.intellij.psi.impl.source.tree.ElementType.NEW_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.NON_SEALED_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.PARAMETER;
import static com.intellij.psi.impl.source.tree.ElementType.PARAMETER_LIST;
import static com.intellij.psi.impl.source.tree.ElementType.PERMITS_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.PERMITS_LIST;
import static com.intellij.psi.impl.source.tree.ElementType.PRIMITIVE_TYPE_BIT_SET;
import static com.intellij.psi.impl.source.tree.ElementType.PRIVATE_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.PROTECTED_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.RBRACE;
import static com.intellij.psi.impl.source.tree.ElementType.RBRACKET;
import static com.intellij.psi.impl.source.tree.ElementType.RECEIVER_PARAMETER;
import static com.intellij.psi.impl.source.tree.ElementType.RECORD_COMPONENT;
import static com.intellij.psi.impl.source.tree.ElementType.RECORD_HEADER;
import static com.intellij.psi.impl.source.tree.ElementType.RECORD_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.REFERENCE_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.RESOURCE_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.RESOURCE_LIST;
import static com.intellij.psi.impl.source.tree.ElementType.RESOURCE_VARIABLE;
import static com.intellij.psi.impl.source.tree.ElementType.RPARENTH;
import static com.intellij.psi.impl.source.tree.ElementType.SEALED_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.SEMICOLON;
import static com.intellij.psi.impl.source.tree.ElementType.THIS_EXPRESSION;
import static com.intellij.psi.impl.source.tree.ElementType.THIS_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.THROWS_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.THROWS_LIST;
import static com.intellij.psi.impl.source.tree.ElementType.TYPE;
import static com.intellij.psi.impl.source.tree.ElementType.TYPE_PARAMETER_LIST;
import static com.intellij.psi.impl.source.tree.ElementType.VALUE_KEYWORD;
import static com.intellij.psi.impl.source.tree.ElementType.VAR_KEYWORD;

/**
 * @deprecated Use the new Java syntax library instead.
 * See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
public class DeclarationParser {
  private static final TokenSet BEFORE_LBRACE_ELEMENTS_SET = TokenSet.create(
    IDENTIFIER, COMMA, EXTENDS_KEYWORD, IMPLEMENTS_KEYWORD, LPARENTH);
  private static final TokenSet APPEND_TO_METHOD_SET = TokenSet.create(
    IDENTIFIER, COMMA, THROWS_KEYWORD);
  private static final TokenSet PARAM_LIST_STOPPERS = TokenSet.create(
    RPARENTH, LBRACE, ARROW);
  private static final TokenSet METHOD_PARAM_LIST_STOPPERS = TokenSet.create(
    RPARENTH, LBRACE, ARROW, SEMICOLON);
  private static final TokenSet TYPE_START = TokenSet.orSet(
    PRIMITIVE_TYPE_BIT_SET, TokenSet.create(IDENTIFIER, AT, VAR_KEYWORD));
  private static final String WHITESPACES = "\n\r \t";
  private static final String LINE_ENDS = "\n\r";
  private final TokenSet RESOURCE_EXPRESSIONS;
  private final JavaParser myParser;

  public void parseClassBodyWithBraces(PsiBuilder builder, boolean isAnnotation, boolean isEnum) {
    assert builder.getTokenType() == LBRACE : builder.getTokenType();
    builder.advanceLexer();

    final PsiBuilder builderWrapper = braceMatchingBuilder(builder);
    if (isEnum) {
      parseEnumConstants(builderWrapper);
    }
    parseClassBodyDeclarations(builderWrapper, isAnnotation);

    expectOrError(builder, RBRACE, "expected.rbrace");
  }

  private @Nullable PsiBuilder.Marker parseClassFromKeyword(PsiBuilder builder,
                                                            PsiBuilder.Marker declaration,
                                                            boolean isAnnotation,
                                                            Context context) {
    IElementType keywordTokenType = builder.getTokenType();
    final boolean isRecord = isRecordToken(builder, keywordTokenType);
    if (isRecord) {
      builder.remapCurrentToken(RECORD_KEYWORD);
      if (builder.lookAhead(1) != IDENTIFIER) {
        builder.advanceLexer();
        error(builder, JavaPsiBundle.message("expected.identifier"));
        declaration.drop();
        return null;
      }
      final IElementType afterIdent = builder.lookAhead(2);
      // No parser recovery for local records without < or ( to support light stubs
      // (look at com.intellij.psi.impl.source.JavaLightStubBuilder.CodeBlockVisitor.visit)
      if (context == Context.CODE_BLOCK && afterIdent != LPARENTH && afterIdent != LT) {
        // skipping record kw and identifier
        PsiBuilderUtil.advance(builder, 2);
        error(builder, JavaPsiBundle.message("expected.lt.or.lparen"));
        declaration.drop();
        return null;
      }
      keywordTokenType = RECORD_KEYWORD;
    }
    assert CLASS_KEYWORD_BIT_SET.contains(keywordTokenType) : keywordTokenType;
    builder.advanceLexer();
    final boolean isEnum = (keywordTokenType == ENUM_KEYWORD);

    if (!expect(builder, IDENTIFIER)) {
      error(builder, JavaPsiBundle.message("expected.identifier"));
      declaration.drop();
      return null;
    }

    final ReferenceParser refParser = myParser.getReferenceParser();
    refParser.parseTypeParameters(builder);

    if (builder.getTokenType() == LPARENTH) {
      parseElementList(builder, ListType.RECORD_COMPONENTS);
    }

    refParser.parseReferenceList(builder, EXTENDS_KEYWORD, EXTENDS_LIST, COMMA);
    refParser.parseReferenceList(builder, IMPLEMENTS_KEYWORD, IMPLEMENTS_LIST,
                                 COMMA);
    if (builder.getTokenType() == IDENTIFIER &&
        JavaKeywords.PERMITS.equals(builder.getTokenText())) {
      builder.remapCurrentToken(PERMITS_KEYWORD);
    }
    if (builder.getTokenType() == PERMITS_KEYWORD) {
      refParser.parseReferenceList(builder, PERMITS_KEYWORD, PERMITS_LIST, COMMA);
    }

    if (builder.getTokenType() != LBRACE) {
      final PsiBuilder.Marker error = builder.mark();
      while (BEFORE_LBRACE_ELEMENTS_SET.contains(builder.getTokenType())) {
        builder.advanceLexer();
      }
      error.error(JavaPsiBundle.message("expected.lbrace"));
    }

    if (builder.getTokenType() == LBRACE) {
      parseClassBodyWithBraces(builder, isAnnotation, isEnum);
    }

    done(declaration, CLASS, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
    return declaration;
  }

  private void parseEnumConstants(PsiBuilder builder) {
    boolean first = true;
    while (builder.getTokenType() != null) {
      if (expect(builder, SEMICOLON)) {
        return;
      }

      if (builder.getTokenType() == PRIVATE_KEYWORD || builder.getTokenType() == PROTECTED_KEYWORD) {
        error(builder, JavaPsiBundle.message("expected.semicolon"));
        return;
      }

      PsiBuilder.Marker enumConstant = parseEnumConstant(builder);
      if (enumConstant == null && builder.getTokenType() == COMMA && first) {
        IElementType next = builder.lookAhead(1);
        if (next != SEMICOLON && next != RBRACE) {
          error(builder, JavaPsiBundle.message("expected.identifier"));
        }
      }

      first = false;

      int commaCount = 0;
      while (builder.getTokenType() == COMMA) {
        if (commaCount > 0) {
          error(builder, JavaPsiBundle.message("expected.identifier"));
        }
        builder.advanceLexer();
        commaCount++;
      }
      if (commaCount == 0 &&
          builder.getTokenType() != null &&
          builder.getTokenType() != SEMICOLON) {
        error(builder, JavaPsiBundle.message("expected.comma.or.semicolon"));
        return;
      }
    }
  }

  public @Nullable PsiBuilder.Marker parseEnumConstant(PsiBuilder builder) {
    final PsiBuilder.Marker constant = builder.mark();

    parseModifierList(builder);

    if (expect(builder, IDENTIFIER)) {
      if (builder.getTokenType() == LPARENTH) {
        myParser.getExpressionParser().parseArgumentList(builder);
      }
      else {
        emptyElement(builder, EXPRESSION_LIST);
      }

      if (builder.getTokenType() == LBRACE) {
        final PsiBuilder.Marker constantInit = builder.mark();
        parseClassBodyWithBraces(builder, false, false);
        done(constantInit, ENUM_CONSTANT_INITIALIZER, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
      }

      done(constant, ENUM_CONSTANT, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
      return constant;
    }
    else {
      constant.rollbackTo();
      return null;
    }
  }

  public void parseClassBodyDeclarations(PsiBuilder builder, boolean isAnnotation) {
    final Context context = isAnnotation ? Context.ANNOTATION_INTERFACE : Context.CLASS;

    PsiBuilder.Marker invalidElements = null;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == null || tokenType == RBRACE) break;

      if (tokenType == SEMICOLON) {
        if (invalidElements != null) {
          invalidElements.error(JavaPsiBundle.message("unexpected.token"));
          invalidElements = null;
        }
        builder.advanceLexer();
        continue;
      }

      final PsiBuilder.Marker declaration = parse(builder, context);
      if (declaration != null) {
        if (invalidElements != null) {
          invalidElements.errorBefore(JavaPsiBundle.message("unexpected.token"), declaration);
          invalidElements = null;
        }
        continue;
      }

      if (invalidElements == null) {
        invalidElements = builder.mark();
      }

      // adding a reference, not simple tokens allows "Browse ..." to work well
      final PsiBuilder.Marker ref = myParser.getReferenceParser().parseJavaCodeReference(builder, true, true, false, false);
      if (ref == null) {
        builder.advanceLexer();
      }
    }

    if (invalidElements != null) {
      invalidElements.error(JavaPsiBundle.message("unexpected.token"));
    }
  }

  public @Nullable PsiBuilder.Marker parse(@NotNull PsiBuilder builder, Context context) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType == null) return null;

    if (tokenType == LBRACE) {
      if (context == Context.FILE || context == Context.CODE_BLOCK) return null;
    }
    else if (!isRecordToken(builder, tokenType) && !isSealedToken(builder, tokenType) && !isNonSealedToken(builder, tokenType)) {
      if (!TYPE_START.contains(tokenType) || tokenType == AT) {
        if (tokenType instanceof ILazyParseableElementType) {
          builder.advanceLexer();
          return null;
        }
        else if (!MODIFIER_BIT_SET.contains(tokenType) &&
                 !CLASS_KEYWORD_BIT_SET.contains(tokenType) &&
                 tokenType != AT &&
                 (context == Context.CODE_BLOCK || tokenType != LT)) {
          return null;
        }
      }
    }

    final PsiBuilder.Marker declaration = builder.mark();
    final int declarationStart = builder.getCurrentOffset();

    final Pair<PsiBuilder.Marker, Boolean> modListInfo = parseModifierList(builder);
    final PsiBuilder.Marker modList = modListInfo.first;

    if (expect(builder, AT)) {
      if (builder.getTokenType() == INTERFACE_KEYWORD) {
        final PsiBuilder.Marker result = parseClassFromKeyword(builder, declaration, true, context);
        return result != null ? result : modList;
      }
      else {
        declaration.rollbackTo();
        return null;
      }
    }
    if (CLASS_KEYWORD_BIT_SET.contains(builder.getTokenType()) || isRecordToken(builder, builder.getTokenType())) {
      final PsiBuilder.Marker result = parseClassFromKeyword(builder, declaration, false, context);
      return result != null ? result : modList;
    }

    PsiBuilder.Marker typeParams = null;
    if (builder.getTokenType() == LT && context != Context.CODE_BLOCK) {
      typeParams = myParser.getReferenceParser().parseTypeParameters(builder);
    }

    if (builder.getTokenType() == LBRACE) {
      if (context == Context.CODE_BLOCK) {
        error(builder, JavaPsiBundle.message("expected.identifier.or.type"), null);
        declaration.drop();
        return modList;
      }

      PsiBuilder.Marker codeBlock = myParser.getStatementParser().parseCodeBlock(builder);
      assert codeBlock != null : builder.getOriginalText();

      if (typeParams != null) {
        PsiBuilder.Marker error = typeParams.precede();
        error.errorBefore(JavaPsiBundle.message("unexpected.token"), codeBlock);
      }

      done(declaration, CLASS_INITIALIZER, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
      return declaration;
    }

    ReferenceParser.TypeInfo type = null;

    if (TYPE_START.contains(builder.getTokenType())) {
      PsiBuilder.Marker pos = builder.mark();

      int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD;
      if (context == Context.CODE_BLOCK || context == Context.JSHELL) flags |= ReferenceParser.VAR_TYPE;
      type = myParser.getReferenceParser().parseTypeInfo(builder, flags);

      if (type == null) {
        pos.rollbackTo();
      }
      else if (builder.getTokenType() == LPARENTH ||
               builder.getTokenType() == LBRACE ||
               builder.getTokenType() == THROWS_KEYWORD) {  // constructor
        if (context == Context.CODE_BLOCK) {
          declaration.rollbackTo();
          return null;
        }

        pos.rollbackTo();
        if (typeParams == null) {
          emptyElement(builder, TYPE_PARAMETER_LIST);
        }
        parseAnnotations(builder);

        if (!expect(builder, IDENTIFIER)) {
          PsiBuilder.Marker primitive = builder.mark();
          builder.advanceLexer();
          primitive.error(JavaPsiBundle.message("expected.identifier"));
        }

        if (builder.getTokenType() == LPARENTH) {
          return parseMethodFromLeftParenth(builder, declaration, false, true);
        }
        else if (builder.getTokenType() == LBRACE) { // compact constructor
          emptyElement(builder, THROWS_LIST);
          return parseMethodBody(builder, declaration, false);
        }
        else if (builder.getTokenType() == THROWS_KEYWORD) {
          myParser.getReferenceParser()
            .parseReferenceList(builder, THROWS_KEYWORD, THROWS_LIST, COMMA);
          return parseMethodBody(builder, declaration, false);
        }
        else {
          declaration.rollbackTo();
          return null;
        }
      }
      else {
        pos.drop();
      }
    }

    if (type == null) {
      PsiBuilder.Marker error = typeParams != null ? typeParams.precede() : builder.mark();
      error.error(JavaPsiBundle.message("expected.identifier.or.type"));
      declaration.drop();
      return modList;
    }

    if (!expect(builder, IDENTIFIER)) {
      if (context != Context.CODE_BLOCK ||
          Boolean.FALSE.equals(modListInfo.second) ||
          (type.isPrimitive && builder.getTokenType() != DOT)) {
        if (typeParams != null) {
          typeParams.precede().errorBefore(JavaPsiBundle.message("unexpected.token"), type.marker);
        }
        builder.error(JavaPsiBundle.message("expected.identifier"));
        declaration.drop();
        return modList;
      }
      else {
        declaration.rollbackTo();
        return null;
      }
    }

    if (builder.getTokenType() == LPARENTH) {
      if (context == Context.CLASS || context == Context.ANNOTATION_INTERFACE || context == Context.FILE
          || context == Context.JSHELL) {  // method
        if (typeParams == null) {
          emptyElement(type.marker, TYPE_PARAMETER_LIST);
        }
        return parseMethodFromLeftParenth(builder, declaration, (context == Context.ANNOTATION_INTERFACE), false);
      }
    }

    if (typeParams != null) {
      typeParams.precede().errorBefore(JavaPsiBundle.message("unexpected.token"), type.marker);
    }
    return parseFieldOrLocalVariable(builder, declaration, declarationStart, context);
  }

  public @NotNull Pair<PsiBuilder.Marker, Boolean> parseModifierList(PsiBuilder builder) {
    return parseModifierList(builder, MODIFIER_BIT_SET);
  }

  public @NotNull Pair<PsiBuilder.Marker, Boolean> parseModifierList(PsiBuilder builder, TokenSet modifiers) {
    final PsiBuilder.Marker modList = builder.mark();
    boolean isEmpty = true;

    while (true) {
      IElementType tokenType = builder.getTokenType();
      if (tokenType == null) break;
      if (isValueToken(builder, tokenType)) {
        builder.remapCurrentToken(VALUE_KEYWORD);
        tokenType = VALUE_KEYWORD;
      }
      else if (isSealedToken(builder, tokenType)) {
        builder.remapCurrentToken(SEALED_KEYWORD);
        tokenType = SEALED_KEYWORD;
      }
      if (isNonSealedToken(builder, tokenType)) {
        PsiBuilder.Marker nonSealed = builder.mark();
        PsiBuilderUtil.advance(builder, 3);
        nonSealed.collapse(NON_SEALED_KEYWORD);
        isEmpty = false;
      }
      else if (modifiers.contains(tokenType)) {
        builder.advanceLexer();
        isEmpty = false;
      }
      else if (tokenType == AT) {
        if (KEYWORD_BIT_SET.contains(builder.lookAhead(1))) {
          break;
        }
        parseAnnotation(builder);
        isEmpty = false;
      }
      else {
        break;
      }
    }

    done(modList, MODIFIER_LIST, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
    return Pair.create(modList, isEmpty);
  }

  private PsiBuilder.Marker parseMethodFromLeftParenth(PsiBuilder builder,
                                                       PsiBuilder.Marker declaration,
                                                       boolean anno,
                                                       boolean constructor) {
    parseParameterList(builder);

    eatBrackets(builder, constructor ? "expected.lbrace" : null);

    myParser.getReferenceParser()
      .parseReferenceList(builder, THROWS_KEYWORD, THROWS_LIST, COMMA);

    if (anno && expect(builder, DEFAULT_KEYWORD) && parseAnnotationValue(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.value"));
    }

    return parseMethodBody(builder, declaration, anno);
  }

  private @NotNull PsiBuilder.Marker parseMethodBody(PsiBuilder builder, PsiBuilder.Marker declaration, boolean anno) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType != SEMICOLON && tokenType != LBRACE) {
      PsiBuilder.Marker error = builder.mark();
      // heuristic: going to next line obviously means method signature is over, starting new method (actually, another one completion hack)
      CharSequence text = builder.getOriginalText();
      Loop:
      while (true) {
        for (int i = builder.getCurrentOffset() - 1; i >= 0; i--) {
          char ch = text.charAt(i);
          if (ch == '\n') {
            break Loop;
          }
          else if (ch != ' ' && ch != '\t') break;
        }
        if (!expect(builder, APPEND_TO_METHOD_SET)) break;
      }
      error.error(JavaPsiBundle.message("expected.lbrace.or.semicolon"));
    }

    if (!expect(builder, SEMICOLON)) {
      if (builder.getTokenType() == LBRACE) {
        myParser.getStatementParser().parseCodeBlock(builder);
      }
    }

    done(declaration, anno ? ANNOTATION_METHOD : METHOD,
         builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
    return declaration;
  }

  public void parseParameterList(PsiBuilder builder) {
    parseElementList(builder, ListType.METHOD);
  }

  public void parseResourceList(PsiBuilder builder) {
    parseElementList(builder, ListType.RESOURCE);
  }

  public void parseLambdaParameterList(PsiBuilder builder, boolean typed) {
    parseElementList(builder, typed ? ListType.LAMBDA_TYPED : ListType.LAMBDA_UNTYPED);
  }

  private void parseElementList(PsiBuilder builder, ListType type) {
    final boolean lambda = (type == ListType.LAMBDA_TYPED || type == ListType.LAMBDA_UNTYPED);
    final boolean resources = (type == ListType.RESOURCE);
    final PsiBuilder.Marker elementList = builder.mark();
    final boolean leftParenth = expect(builder, LPARENTH);
    assert lambda || leftParenth : builder.getTokenType();

    final IElementType delimiter = resources ? SEMICOLON : COMMA;
    final String noDelimiterMsg = resources ? "expected.semicolon" : "expected.comma";
    final String noElementMsg = resources ? "expected.resource" : "expected.parameter";

    PsiBuilder.Marker invalidElements = null;
    String errorMessage = null;
    boolean delimiterExpected = false;
    boolean noElements = true;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == null || type.getStopperTypes().contains(tokenType)) {
        final boolean noLastElement = !delimiterExpected && (!noElements && !resources || noElements && resources);
        if (noLastElement) {
          final String key = lambda ? "expected.parameter" : "expected.identifier.or.type";
          error(builder, JavaPsiBundle.message(key));
        }
        if (tokenType == RPARENTH) {
          if (invalidElements != null) {
            invalidElements.error(errorMessage);
            invalidElements = null;
          }
          builder.advanceLexer();
        }
        else {
          if (!noLastElement || resources) {
            if (invalidElements != null) {
              invalidElements.error(errorMessage);
            }
            invalidElements = null;
            if (leftParenth) {
              error(builder, JavaPsiBundle.message("expected.rparen"));
            }
          }
        }
        break;
      }

      if (delimiterExpected) {
        if (builder.getTokenType() == delimiter) {
          delimiterExpected = false;
          if (invalidElements != null) {
            invalidElements.error(errorMessage);
            invalidElements = null;
          }
          builder.advanceLexer();
          continue;
        }
      }
      else {
        final PsiBuilder.Marker listElement =
          type == ListType.RECORD_COMPONENTS ? parseParameterOrRecordComponent(builder, true, false, false, false) :
          resources ? parseResource(builder) :
          lambda ? parseLambdaParameter(builder, type == ListType.LAMBDA_TYPED) :
          parseParameter(builder, true, false, false);
        if (listElement != null) {
          delimiterExpected = true;
          if (invalidElements != null) {
            invalidElements.errorBefore(errorMessage, listElement);
            invalidElements = null;
          }
          noElements = false;
          continue;
        }
      }

      if (invalidElements == null) {
        if (builder.getTokenType() == delimiter) {
          error(builder, JavaPsiBundle.message(noElementMsg));
          builder.advanceLexer();
          if (noElements && resources) {
            noElements = false;
          }
          continue;
        }
        else {
          invalidElements = builder.mark();
          errorMessage = JavaPsiBundle.message(delimiterExpected ? noDelimiterMsg : noElementMsg);
        }
      }

      // adding a reference, not simple tokens allows "Browse .." to work well
      final PsiBuilder.Marker ref = myParser.getReferenceParser().parseJavaCodeReference(builder, true, true, false, false);
      if (ref == null && builder.getTokenType() != null) {
        builder.advanceLexer();
      }
    }

    if (invalidElements != null) {
      invalidElements.error(errorMessage);
    }

    done(elementList, type.getNodeType(), builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
  }

  public @Nullable PsiBuilder.Marker parseParameter(PsiBuilder builder, boolean ellipsis, boolean disjunctiveType, boolean varType) {
    return parseParameterOrRecordComponent(builder, ellipsis, disjunctiveType, varType, true);
  }

  public @Nullable PsiBuilder.Marker parseParameterOrRecordComponent(PsiBuilder builder,
                                                                     boolean ellipsis,
                                                                     boolean disjunctiveType,
                                                                     boolean varType,
                                                                     boolean isParameter) {
    int typeFlags = 0;
    if (ellipsis) typeFlags |= ReferenceParser.ELLIPSIS;
    if (disjunctiveType) typeFlags |= ReferenceParser.DISJUNCTIONS;
    if (varType) typeFlags |= ReferenceParser.VAR_TYPE;
    return parseListElement(builder, true, typeFlags,
                            isParameter ? PARAMETER : RECORD_COMPONENT);
  }

  public @Nullable PsiBuilder.Marker parseResource(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();

    PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    if (expr != null && RESOURCE_EXPRESSIONS.contains(exprType(expr)) && builder.getTokenType() != IDENTIFIER) {
      marker.done(RESOURCE_EXPRESSION);
      return marker;
    }

    marker.rollbackTo();

    return parseListElement(builder, true, ReferenceParser.VAR_TYPE, RESOURCE_VARIABLE);
  }

  public @Nullable PsiBuilder.Marker parseLambdaParameter(PsiBuilder builder, boolean typed) {
    int flags = ReferenceParser.ELLIPSIS;
    if (JavaFeature.VAR_LAMBDA_PARAMETER.isSufficient(getLanguageLevel(builder))) flags |= ReferenceParser.VAR_TYPE;
    return parseListElement(builder, typed, flags, PARAMETER);
  }

  private @Nullable PsiBuilder.Marker parseListElement(PsiBuilder builder, boolean typed, int typeFlags, IElementType type) {
    PsiBuilder.Marker param = builder.mark();

    Pair<PsiBuilder.Marker, Boolean> modListInfo = parseModifierList(builder);

    if (typed) {
      int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD | typeFlags;
      ReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(builder, flags);

      if (typeInfo == null) {
        if (Boolean.TRUE.equals(modListInfo.second)) {
          param.rollbackTo();
          return null;
        }
        else {
          error(builder, JavaPsiBundle.message("expected.type"));
          emptyElement(builder, TYPE);
        }
      }
    }

    if (typed) {
      IElementType tokenType = builder.getTokenType();
      if (tokenType == THIS_KEYWORD || tokenType == IDENTIFIER && builder.lookAhead(1) == DOT) {
        PsiBuilder.Marker mark = builder.mark();

        PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
        if (expr != null && exprType(expr) == THIS_EXPRESSION) {
          mark.drop();
          done(param, RECEIVER_PARAMETER, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
          return param;
        }

        mark.rollbackTo();
      }
    }

    if (expect(builder, IDENTIFIER)) {
      if (type == PARAMETER || type == RECORD_COMPONENT) {
        eatBrackets(builder, null);
        done(param, type, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
        return param;
      }
    }
    else {
      error(builder, JavaPsiBundle.message("expected.identifier"));
      param.drop();
      return modListInfo.first;
    }

    if (expectOrError(builder, EQ, "expected.eq")) {
      if (myParser.getExpressionParser().parse(builder) == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }
    }

    done(param, RESOURCE_VARIABLE, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
    return param;
  }

  private @Nullable PsiBuilder.Marker parseFieldOrLocalVariable(PsiBuilder builder,
                                                                PsiBuilder.Marker declaration,
                                                                int declarationStart,
                                                                Context context) {
    final IElementType varType;
    if (context == Context.CLASS || context == Context.ANNOTATION_INTERFACE || context == Context.FILE
        || context == Context.JSHELL) {
      varType = FIELD;
    }
    else if (context == Context.CODE_BLOCK) {
      varType = LOCAL_VARIABLE;
    }
    else {
      declaration.drop();
      assert false : "Unexpected context: " + context;
      return null;
    }

    PsiBuilder.Marker variable = declaration;
    boolean unclosed = false;
    boolean eatSemicolon = true;
    boolean shouldRollback;
    boolean openMarker = true;
    while (true) {
      shouldRollback = true;

      if (!eatBrackets(builder, null)) {
        unclosed = true;
      }

      if (expect(builder, EQ)) {
        final PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
        if (expr != null) {
          shouldRollback = false;
        }
        else {
          error(builder, JavaPsiBundle.message("expected.expression"));
          unclosed = true;
          break;
        }
      }

      if (builder.getTokenType() != COMMA) break;
      done(variable, varType, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
      builder.advanceLexer();

      if (builder.getTokenType() != IDENTIFIER) {
        error(builder, JavaPsiBundle.message("expected.identifier"));
        unclosed = true;
        eatSemicolon = false;
        openMarker = false;
        break;
      }

      variable = builder.mark();
      builder.advanceLexer();
    }

    if (builder.getTokenType() == SEMICOLON && eatSemicolon) {
      builder.advanceLexer();
    }
    else {
      // special treatment (see DeclarationParserTest.testMultiLineUnclosed())
      if (!builder.eof() && shouldRollback) {
        final CharSequence text = builder.getOriginalText();
        final int spaceEnd = builder.getCurrentOffset();
        final int spaceStart = CharArrayUtil.shiftBackward(text, spaceEnd - 1, WHITESPACES);
        final int lineStart = CharArrayUtil.shiftBackwardUntil(text, spaceEnd, LINE_ENDS);

        if (declarationStart < lineStart && lineStart < spaceStart) {
          final int newBufferEnd = CharArrayUtil.shiftForward(text, lineStart, WHITESPACES);
          declaration.rollbackTo();
          return parse(stoppingBuilder(builder, newBufferEnd), context);
        }
      }

      if (!unclosed) {
        error(builder, JavaPsiBundle.message("expected.semicolon"));
      }
    }

    if (openMarker) {
      done(variable, varType, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
    }

    return declaration;
  }

  private boolean eatBrackets(PsiBuilder builder, @Nullable @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String errorKey) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType != LBRACKET && tokenType != AT) return true;

    PsiBuilder.Marker marker = builder.mark();

    int count = 0;
    while (true) {
      parseAnnotations(builder);
      if (!expect(builder, LBRACKET)) {
        break;
      }
      ++count;
      if (!expect(builder, RBRACKET)) {
        break;
      }
      ++count;
    }

    if (count == 0) {
      // just annotation, most probably belongs to a next declaration
      marker.rollbackTo();
      return true;
    }

    if (errorKey != null) {
      marker.error(JavaPsiBundle.message(errorKey));
    }
    else {
      marker.drop();
    }

    boolean paired = count % 2 == 0;
    if (!paired) {
      error(builder, JavaPsiBundle.message("expected.rbracket"));
    }
    return paired;
  }

  public @Nullable PsiBuilder.Marker parseAnnotations(PsiBuilder builder) {
    PsiBuilder.Marker firstAnno = null;

    while (builder.getTokenType() == AT) {
      final PsiBuilder.Marker anno = parseAnnotation(builder);
      if (firstAnno == null) firstAnno = anno;
    }

    return firstAnno;
  }

  public @NotNull PsiBuilder.Marker parseAnnotation(PsiBuilder builder) {
    assert builder.getTokenType() == AT : builder.getTokenType();
    final PsiBuilder.Marker anno = builder.mark();
    builder.advanceLexer();

    PsiBuilder.Marker classRef = null;
    if (builder.getTokenType() == IDENTIFIER) {
      classRef = myParser.getReferenceParser().parseJavaCodeReference(builder, true, false, false, false);
    }
    if (classRef == null) {
      error(builder, JavaPsiBundle.message("expected.class.reference"));
    }

    parseAnnotationParameterList(builder);

    done(anno, ANNOTATION, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
    return anno;
  }

  private void parseAnnotationParameterList(PsiBuilder builder) {
    PsiBuilder.Marker list = builder.mark();

    if (!expect(builder, LPARENTH) || expect(builder, RPARENTH)) {
      done(list, ANNOTATION_PARAMETER_LIST, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
      return;
    }

    if (builder.getTokenType() == null) {
      error(builder, JavaPsiBundle.message("expected.parameter.or.rparen"));
      done(list, ANNOTATION_PARAMETER_LIST, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
      return;
    }
    PsiBuilder.Marker elementMarker = parseAnnotationElement(builder);
    while (true) {
      IElementType tokenType = builder.getTokenType();
      if (tokenType == null) {
        error(builder, JavaPsiBundle.message(elementMarker == null ? "expected.parameter.or.rparen" : "expected.comma.or.rparen"));
        break;
      }
      else if (expect(builder, RPARENTH)) {
        break;
      }
      else if (tokenType == COMMA) {
        builder.advanceLexer();
        elementMarker = parseAnnotationElement(builder);
        if (elementMarker == null) {
          error(builder, JavaPsiBundle.message("annotation.name.is.missing"));
          tokenType = builder.getTokenType();
          if (tokenType != COMMA && tokenType != RPARENTH) {
            break;
          }
        }
      }
      else {
        error(builder, JavaPsiBundle.message(elementMarker == null ? "expected.parameter.or.rparen" : "expected.comma.or.rparen"));
        tokenType = builder.lookAhead(1);
        if (tokenType != COMMA && tokenType != RPARENTH) break;
        builder.advanceLexer();
      }
    }

    done(list, ANNOTATION_PARAMETER_LIST, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
  }

  private PsiBuilder.Marker parseAnnotationElement(PsiBuilder builder) {
    PsiBuilder.Marker pair = builder.mark();

    PsiBuilder.Marker valueMarker = parseAnnotationValue(builder);
    if (valueMarker == null && builder.getTokenType() != EQ) {
      pair.drop();
      return null;
    }
    if (builder.getTokenType() != EQ) {
      done(pair, NAME_VALUE_PAIR, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
      return pair;
    }

    pair.rollbackTo();
    pair = builder.mark();

    expectOrError(builder, IDENTIFIER, "expected.identifier");
    expect(builder, EQ);
    valueMarker = parseAnnotationValue(builder);
    if (valueMarker == null) error(builder, JavaPsiBundle.message("expected.value"));

    done(pair, NAME_VALUE_PAIR, builder, OldParserWhiteSpaceAndCommentSetHolder.INSTANCE);
    return pair;
  }

  public @Nullable PsiBuilder.Marker parseAnnotationValue(PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType == AT) {
      return parseAnnotation(builder);
    }
    else if (tokenType == LBRACE) {
      return myParser.getExpressionParser().parseArrayInitializer(
        builder, ANNOTATION_ARRAY_INITIALIZER, this::parseAnnotationValue, "expected.value");
    }
    else {
      return myParser.getExpressionParser().parseConditional(builder);
    }
  }

  static boolean isRecordToken(PsiBuilder builder, IElementType tokenType) {
    if (tokenType == IDENTIFIER && JavaKeywords.RECORD.equals(builder.getTokenText())) {
      IElementType nextToken = builder.lookAhead(1);
      if (nextToken == IDENTIFIER ||
          // The following tokens cannot be part of a valid record declaration,
          // but we assume it to be a malformed record, rather than a malformed type.
          MODIFIER_BIT_SET.contains(nextToken) || CLASS_KEYWORD_BIT_SET.contains(nextToken) || TYPE_START.contains(nextToken) ||
          nextToken == AT || nextToken == LBRACE || nextToken == RBRACE) {
        return JavaFeature.RECORDS.isSufficient(getLanguageLevel(builder));
      }
    }
    return false;
  }

  private static boolean isSealedToken(PsiBuilder builder, IElementType tokenType) {
    return JavaFeature.SEALED_CLASSES.isSufficient(getLanguageLevel(builder)) &&
           tokenType == IDENTIFIER &&
           JavaKeywords.SEALED.equals(builder.getTokenText());
  }

  private static boolean isValueToken(PsiBuilder builder, IElementType tokenType) {
    return JavaFeature.VALHALLA_VALUE_CLASSES.isSufficient(getLanguageLevel(builder)) &&
           tokenType == IDENTIFIER &&
           JavaKeywords.VALUE.equals(builder.getTokenText());
  }

  static boolean isNonSealedToken(PsiBuilder builder, IElementType tokenType) {
    if (!JavaFeature.SEALED_CLASSES.isSufficient(getLanguageLevel(builder)) ||
        tokenType != IDENTIFIER ||
        !"non".equals(builder.getTokenText()) ||
        builder.lookAhead(1) != MINUS ||
        builder.lookAhead(2) != IDENTIFIER) {
      return false;
    }
    PsiBuilder.Marker maybeNonSealed = builder.mark();
    PsiBuilderUtil.advance(builder, 2);
    boolean isNonSealed = JavaKeywords.SEALED.equals(builder.getTokenText());
    maybeNonSealed.rollbackTo();
    return isNonSealed;
  }

  public DeclarationParser(final @NotNull JavaParser javaParser) {
    this.myParser = javaParser;
    this.RESOURCE_EXPRESSIONS = TokenSet.create(
      REFERENCE_EXPRESSION, THIS_EXPRESSION,
      METHOD_CALL_EXPRESSION, NEW_EXPRESSION);
  }

  public enum Context {
    FILE, CLASS, CODE_BLOCK, ANNOTATION_INTERFACE, JSHELL
  }

  private enum ListType {
    METHOD,
    RESOURCE,
    LAMBDA_TYPED,
    LAMBDA_UNTYPED,
    RECORD_COMPONENTS;

    IElementType getNodeType() {
      if (this == RESOURCE) {
        return RESOURCE_LIST;
      }
      if (this == RECORD_COMPONENTS) {
        return RECORD_HEADER;
      }
      return PARAMETER_LIST;
    }

    TokenSet getStopperTypes() {
      if (this == METHOD) {
        return METHOD_PARAM_LIST_STOPPERS;
      }
      return PARAM_LIST_STOPPERS;
    }
  }
}