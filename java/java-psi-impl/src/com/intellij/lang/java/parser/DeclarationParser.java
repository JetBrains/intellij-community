// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.PsiBuilder;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.JavaParserUtil.*;

public class DeclarationParser {
  public enum Context {
    FILE, CLASS, CODE_BLOCK, ANNOTATION_INTERFACE
  }

  private static final TokenSet AFTER_END_DECLARATION_SET = TokenSet.create(
    JavaElementType.FIELD, JavaElementType.METHOD);
  private static final TokenSet BEFORE_LBRACE_ELEMENTS_SET = TokenSet.create(
    JavaTokenType.IDENTIFIER, JavaTokenType.COMMA, JavaTokenType.EXTENDS_KEYWORD, JavaTokenType.IMPLEMENTS_KEYWORD, JavaTokenType.LPARENTH);
  private static final TokenSet APPEND_TO_METHOD_SET = TokenSet.create(
    JavaTokenType.IDENTIFIER, JavaTokenType.COMMA, JavaTokenType.THROWS_KEYWORD);
  private static final TokenSet PARAM_LIST_STOPPERS = TokenSet.create(
    JavaTokenType.RPARENTH, JavaTokenType.LBRACE, JavaTokenType.ARROW);
  private static final TokenSet TYPE_START = TokenSet.orSet(
    ElementType.PRIMITIVE_TYPE_BIT_SET, TokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.AT, JavaTokenType.VAR_KEYWORD));
  private static final TokenSet RESOURCE_EXPRESSIONS = TokenSet.create(
    JavaElementType.REFERENCE_EXPRESSION, JavaElementType.THIS_EXPRESSION,
    JavaElementType.METHOD_CALL_EXPRESSION, JavaElementType.NEW_EXPRESSION);

  private static final String WHITESPACES = "\n\r \t";
  private static final String LINE_ENDS = "\n\r";

  private final JavaParser myParser;

  public DeclarationParser(@NotNull final JavaParser javaParser) {
    myParser = javaParser;
  }

  public void parseClassBodyWithBraces(final PsiBuilder builder, final boolean isAnnotation, final boolean isEnum) {
    assert builder.getTokenType() == JavaTokenType.LBRACE : builder.getTokenType();
    builder.advanceLexer();

    final PsiBuilder builderWrapper = braceMatchingBuilder(builder);
    if (isEnum) {
      parseEnumConstants(builderWrapper);
    }
    parseClassBodyDeclarations(builderWrapper, isAnnotation);

    expectOrError(builder, JavaTokenType.RBRACE, "expected.rbrace");
  }

  @Nullable
  private PsiBuilder.Marker parseClassFromKeyword(PsiBuilder builder, PsiBuilder.Marker declaration, boolean isAnnotation, Context context) {
    IElementType keywordTokenType = builder.getTokenType();
    final boolean isRecord = isRecordToken(builder, keywordTokenType);
    if (isRecord) {
      if (builder.lookAhead(1) != JavaTokenType.IDENTIFIER) {
        declaration.drop();
        return null;
      }
      final IElementType afterIdent = builder.lookAhead(2);
      // No parser recovery for local records without < or ( to support for light stubs
      // (look at com.intellij.psi.impl.source.JavaLightStubBuilder.CodeBlockVisitor.visit)
      if (context == Context.CODE_BLOCK && afterIdent != JavaTokenType.LPARENTH && afterIdent != JavaTokenType.LT) {
        // skipping record kw and identifier
        builder.advanceLexer();
        builder.advanceLexer();
        error(builder, JavaPsiBundle.message("expected.lt.or.lparen"));
        declaration.drop();
        return null;
      }
      builder.remapCurrentToken(JavaTokenType.RECORD_KEYWORD);
      keywordTokenType = JavaTokenType.RECORD_KEYWORD;
    }
    assert ElementType.CLASS_KEYWORD_BIT_SET.contains(keywordTokenType) : keywordTokenType;
    builder.advanceLexer();
    final boolean isEnum = (keywordTokenType == JavaTokenType.ENUM_KEYWORD);

    if (!expect(builder, JavaTokenType.IDENTIFIER)) {
      error(builder, JavaPsiBundle.message("expected.identifier"));
      declaration.drop();
      return null;
    }

    final ReferenceParser refParser = myParser.getReferenceParser();
    refParser.parseTypeParameters(builder);

    if (builder.getTokenType() == JavaTokenType.LPARENTH) {
      parseElementList(builder, ListType.RECORD_COMPONENTS);
    }

    refParser.parseReferenceList(builder, JavaTokenType.EXTENDS_KEYWORD, JavaElementType.EXTENDS_LIST, JavaTokenType.COMMA);
    refParser.parseReferenceList(builder, JavaTokenType.IMPLEMENTS_KEYWORD, JavaElementType.IMPLEMENTS_LIST, JavaTokenType.COMMA);
    if (getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_15_PREVIEW) &&
        builder.getTokenType() == JavaTokenType.IDENTIFIER &&
        PsiKeyword.PERMITS.equals(builder.getTokenText())) {
      builder.remapCurrentToken(JavaTokenType.PERMITS_KEYWORD);
    }
    refParser.parseReferenceList(builder, JavaTokenType.PERMITS_KEYWORD, JavaElementType.PERMITS_LIST, JavaTokenType.COMMA);

    if (builder.getTokenType() != JavaTokenType.LBRACE) {
      final PsiBuilder.Marker error = builder.mark();
      while (BEFORE_LBRACE_ELEMENTS_SET.contains(builder.getTokenType())) {
        builder.advanceLexer();
      }
      error.error(JavaPsiBundle.message("expected.lbrace"));
    }

    if (builder.getTokenType() == JavaTokenType.LBRACE) {
      parseClassBodyWithBraces(builder, isAnnotation, isEnum);
    }

    if (context == Context.FILE) {
      boolean declarationsAfterEnd = false;

      while (builder.getTokenType() != null && builder.getTokenType() != JavaTokenType.RBRACE) {
        final PsiBuilder.Marker position = builder.mark();
        final PsiBuilder.Marker extra = parse(builder, Context.CLASS);
        if (extra != null && AFTER_END_DECLARATION_SET.contains(exprType(extra))) {
          if (!declarationsAfterEnd) {
            error(builder, JavaPsiBundle.message("expected.class.or.interface"), extra);
          }
          declarationsAfterEnd = true;
          position.drop();
        }
        else {
          position.rollbackTo();
          break;
        }
      }

      if (declarationsAfterEnd) {
        expectOrError(builder, JavaTokenType.RBRACE, "expected.rbrace");
      }
    }

    done(declaration, JavaElementType.CLASS);
    return declaration;
  }

  private void parseEnumConstants(final PsiBuilder builder) {
    boolean first = true;
    while (builder.getTokenType() != null) {
      if (expect(builder, JavaTokenType.SEMICOLON)) {
        return;
      }

      if (builder.getTokenType() == JavaTokenType.PRIVATE_KEYWORD || builder.getTokenType() == JavaTokenType.PROTECTED_KEYWORD) {
        error(builder, JavaPsiBundle.message("expected.semicolon"));
        return;
      }

      PsiBuilder.Marker enumConstant = parseEnumConstant(builder);
      if (enumConstant == null && builder.getTokenType() == JavaTokenType.COMMA && first) {
        IElementType next = builder.lookAhead(1);
        if (next != JavaTokenType.SEMICOLON && next != JavaTokenType.RBRACE) {
          error(builder, JavaPsiBundle.message("expected.identifier"));
        }
      }

      first = false;

      if (!expect(builder, JavaTokenType.COMMA) &&
          builder.getTokenType() != null &&
          builder.getTokenType() != JavaTokenType.SEMICOLON) {
        error(builder, JavaPsiBundle.message("expected.comma.or.semicolon"));
        return;
      }
    }
  }

  @Nullable
  public PsiBuilder.Marker parseEnumConstant(final PsiBuilder builder) {
    final PsiBuilder.Marker constant = builder.mark();

    parseModifierList(builder);

    if (expect(builder, JavaTokenType.IDENTIFIER)) {
      if (builder.getTokenType() == JavaTokenType.LPARENTH) {
        myParser.getExpressionParser().parseArgumentList(builder);
      }
      else {
        emptyElement(builder, JavaElementType.EXPRESSION_LIST);
      }

      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        final PsiBuilder.Marker constantInit = builder.mark();
        parseClassBodyWithBraces(builder, false, false);
        done(constantInit, JavaElementType.ENUM_CONSTANT_INITIALIZER);
      }

      done(constant, JavaElementType.ENUM_CONSTANT);
      return constant;
    }
    else {
      constant.rollbackTo();
      return null;
    }
  }

  public void parseClassBodyDeclarations(final PsiBuilder builder, final boolean isAnnotation) {
    final Context context = isAnnotation ? Context.ANNOTATION_INTERFACE : Context.CLASS;

    PsiBuilder.Marker invalidElements = null;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == null || tokenType == JavaTokenType.RBRACE) break;

      if (tokenType == JavaTokenType.SEMICOLON) {
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

  @Nullable
  public PsiBuilder.Marker parse(final PsiBuilder builder, final Context context) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType == null) return null;

    if (tokenType == JavaTokenType.LBRACE) {
      if (context == Context.FILE || context == Context.CODE_BLOCK) return null;
    }
    else if (!isRecordToken(builder, tokenType) && !isSealed(builder, tokenType)) {
      if (TYPE_START.contains(tokenType) && tokenType != JavaTokenType.AT) {
        if (context == Context.FILE) return null;
      }
      else if (tokenType instanceof ILazyParseableElementType) {
        builder.advanceLexer();
        return null;
      }
      else if (!ElementType.MODIFIER_BIT_SET.contains(tokenType) &&
               !ElementType.CLASS_KEYWORD_BIT_SET.contains(tokenType) &&
               tokenType != JavaTokenType.AT &&
               (context == Context.CODE_BLOCK || tokenType != JavaTokenType.LT)) {
        return null;
      }
    }

    final PsiBuilder.Marker declaration = builder.mark();
    final int declarationStart = builder.getCurrentOffset();

    final Pair<PsiBuilder.Marker, Boolean> modListInfo = parseModifierList(builder);
    final PsiBuilder.Marker modList = modListInfo.first;

    if (expect(builder, JavaTokenType.AT)) {
      if (builder.getTokenType() == JavaTokenType.INTERFACE_KEYWORD) {
        final PsiBuilder.Marker result = parseClassFromKeyword(builder, declaration, true, context);
        return result != null ? result : modList;
      }
      else {
        declaration.rollbackTo();
        return null;
      }
    }
    if (ElementType.CLASS_KEYWORD_BIT_SET.contains(builder.getTokenType()) || isRecordToken(builder, builder.getTokenType())) {
      final PsiBuilder.Marker result = parseClassFromKeyword(builder, declaration, false, context);
      return result != null ? result : modList;
    }

    PsiBuilder.Marker typeParams = null;
    if (builder.getTokenType() == JavaTokenType.LT && context != Context.CODE_BLOCK) {
      typeParams = myParser.getReferenceParser().parseTypeParameters(builder);
    }

    if (context == Context.FILE) {
      error(builder, JavaPsiBundle.message("expected.class.or.interface"), typeParams);
      declaration.drop();
      return modList;
    }

    if (builder.getTokenType() == JavaTokenType.LBRACE) {
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

      done(declaration, JavaElementType.CLASS_INITIALIZER);
      return declaration;
    }

    ReferenceParser.TypeInfo type = null;

    if (TYPE_START.contains(builder.getTokenType())) {
      PsiBuilder.Marker pos = builder.mark();

      int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD;
      if (context == Context.CODE_BLOCK) flags |= ReferenceParser.VAR_TYPE;
      type = myParser.getReferenceParser().parseTypeInfo(builder, flags);

      if (type == null) {
        pos.rollbackTo();
      }
      else if (builder.getTokenType() == JavaTokenType.LPARENTH || builder.getTokenType() == JavaTokenType.LBRACE) {  // constructor
        if (context == Context.CODE_BLOCK) {
          declaration.rollbackTo();
          return null;
        }

        pos.rollbackTo();

        if (typeParams == null) {
          emptyElement(builder, JavaElementType.TYPE_PARAMETER_LIST);
        }
        parseAnnotations(builder);

        if (!expect(builder, JavaTokenType.IDENTIFIER)) {
          PsiBuilder.Marker primitive = builder.mark();
          builder.advanceLexer();
          primitive.error(JavaPsiBundle.message("expected.identifier"));
        }

        if (builder.getTokenType() == JavaTokenType.LPARENTH) {
          return parseMethodFromLeftParenth(builder, declaration, false, true);
        }
        else if (builder.getTokenType() == JavaTokenType.LBRACE) { // compact constructor
          emptyElement(builder, JavaElementType.THROWS_LIST);
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

    if (!expect(builder, JavaTokenType.IDENTIFIER)) {
      if (context != Context.CODE_BLOCK ||
          Boolean.FALSE.equals(modListInfo.second) ||
          (type.isPrimitive && builder.getTokenType() != JavaTokenType.DOT)) {
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

    if (builder.getTokenType() == JavaTokenType.LPARENTH) {
      if (context == Context.CLASS || context == Context.ANNOTATION_INTERFACE) {  // method
        if (typeParams == null) {
          emptyElement(type.marker, JavaElementType.TYPE_PARAMETER_LIST);
        }
        return parseMethodFromLeftParenth(builder, declaration, (context == Context.ANNOTATION_INTERFACE), false);
      }
    }

    if (typeParams != null) {
      typeParams.precede().errorBefore(JavaPsiBundle.message("unexpected.token"), type.marker);
    }
    return parseFieldOrLocalVariable(builder, declaration, declarationStart, context);
  }

  static boolean isRecordToken(PsiBuilder builder, IElementType tokenType) {
    return tokenType == JavaTokenType.IDENTIFIER && PsiKeyword.RECORD.equals(builder.getTokenText()) &&
           builder.lookAhead(1) == JavaTokenType.IDENTIFIER &&
           getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_14_PREVIEW);
  }

  private static boolean isSealed(PsiBuilder builder, IElementType tokenType) {
    return tokenType == JavaTokenType.IDENTIFIER &&
           PsiKeyword.SEALED.equals(builder.getTokenText()) &&
           getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_15_PREVIEW);
  }

  @NotNull
  public Pair<PsiBuilder.Marker, Boolean> parseModifierList(final PsiBuilder builder) {
    return parseModifierList(builder, ElementType.MODIFIER_BIT_SET);
  }

  @NotNull
  public Pair<PsiBuilder.Marker, Boolean> parseModifierList(final PsiBuilder builder, final TokenSet modifiers) {
    final PsiBuilder.Marker modList = builder.mark();
    boolean isEmpty = true;

    while (true) {
      IElementType tokenType = builder.getTokenType();
      if (tokenType == null) break;
      if (isSealed(builder, tokenType)) {
        builder.remapCurrentToken(JavaTokenType.SEALED_KEYWORD);
        tokenType = JavaTokenType.SEALED_KEYWORD;
      }
      if (modifiers.contains(tokenType)) {
        builder.advanceLexer();
        isEmpty = false;
      }
      else if (tokenType == JavaTokenType.AT) {
        if (ElementType.KEYWORD_BIT_SET.contains(builder.lookAhead(1))) {
          break;
        }
        parseAnnotation(builder);
        isEmpty = false;
      }
      else {
        break;
      }
    }

    done(modList, JavaElementType.MODIFIER_LIST);
    return Pair.create(modList, isEmpty);
  }

  private PsiBuilder.Marker parseMethodFromLeftParenth(PsiBuilder builder, PsiBuilder.Marker declaration, boolean anno, boolean constructor) {
    parseParameterList(builder);

    eatBrackets(builder, constructor ? "expected.semicolon" : null);

    myParser.getReferenceParser().parseReferenceList(builder, JavaTokenType.THROWS_KEYWORD, JavaElementType.THROWS_LIST, JavaTokenType.COMMA);

    if (anno && expect(builder, JavaTokenType.DEFAULT_KEYWORD)) {
      parseAnnotationValue(builder);
    }

    return parseMethodBody(builder, declaration, anno);
  }

  @NotNull
  private PsiBuilder.Marker parseMethodBody(PsiBuilder builder, PsiBuilder.Marker declaration, boolean anno) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType != JavaTokenType.SEMICOLON && tokenType != JavaTokenType.LBRACE) {
      PsiBuilder.Marker error = builder.mark();
      // heuristic: going to next line obviously means method signature is over, starting new method (actually, another one completion hack)
      CharSequence text = builder.getOriginalText();
      Loop:
      while (true) {
        for (int i = builder.getCurrentOffset() - 1; i >= 0; i--) {
          char ch = text.charAt(i);
          if (ch == '\n') break Loop;
          else if (ch != ' ' && ch != '\t') break;
        }
        if (!expect(builder, APPEND_TO_METHOD_SET)) break;
      }
      error.error(JavaPsiBundle.message("expected.lbrace.or.semicolon"));
    }

    if (!expect(builder, JavaTokenType.SEMICOLON)) {
      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        myParser.getStatementParser().parseCodeBlock(builder);
      }
    }

    done(declaration, anno ? JavaElementType.ANNOTATION_METHOD : JavaElementType.METHOD);
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

  private enum ListType {
    METHOD,
    RESOURCE,
    LAMBDA_TYPED,
    LAMBDA_UNTYPED,
    RECORD_COMPONENTS;

    IElementType getNodeType() {
      if (this == RESOURCE) {
        return JavaElementType.RESOURCE_LIST;
      }
      if (this == RECORD_COMPONENTS) {
        return JavaElementType.RECORD_HEADER;
      }
      return JavaElementType.PARAMETER_LIST;
    }
  }

  private void parseElementList(PsiBuilder builder, ListType type) {
    final boolean lambda = (type == ListType.LAMBDA_TYPED || type == ListType.LAMBDA_UNTYPED);
    final boolean resources = (type == ListType.RESOURCE);
    final PsiBuilder.Marker elementList = builder.mark();
    final boolean leftParenth = expect(builder, JavaTokenType.LPARENTH);
    assert lambda || leftParenth : builder.getTokenType();

    final IElementType delimiter = resources ? JavaTokenType.SEMICOLON : JavaTokenType.COMMA;
    final String noDelimiterMsg = resources ? "expected.semicolon" : "expected.comma";
    final String noElementMsg = resources ? "expected.resource" : "expected.parameter";

    PsiBuilder.Marker invalidElements = null;
    String errorMessage = null;
    boolean delimiterExpected = false;
    boolean noElements = true;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == null || PARAM_LIST_STOPPERS.contains(tokenType)) {
        final boolean noLastElement = !delimiterExpected && (!noElements && !resources || noElements && resources);
        if (noLastElement) {
          final String key = lambda ? "expected.parameter" : "expected.identifier.or.type";
          error(builder, JavaPsiBundle.message(key));
        }
        if (tokenType == JavaTokenType.RPARENTH) {
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
        final PsiBuilder.Marker listElement = type == ListType.RECORD_COMPONENTS ? parseParameterOrRecordComponent(builder, true, false, false, false) :
                                              resources ? parseResource(builder) :
                                              lambda ? parseLambdaParameter(builder, type == ListType.LAMBDA_TYPED) :
                                              parseParameter(builder, true, false, false);
        if (listElement != null) {
          delimiterExpected = true;
          if (invalidElements != null) {
            invalidElements.errorBefore(errorMessage, listElement);
            invalidElements = null;
          }
          noElements= false;
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

    done(elementList, type.getNodeType());
  }

  @Nullable
  public PsiBuilder.Marker parseParameter(PsiBuilder builder, boolean ellipsis, boolean disjunctiveType, boolean varType) {
    return parseParameterOrRecordComponent(builder, ellipsis, disjunctiveType, varType, true);
  }

  @Nullable
  public PsiBuilder.Marker parseParameterOrRecordComponent(PsiBuilder builder, boolean ellipsis, boolean disjunctiveType, boolean varType, boolean isParameter) {
    int typeFlags = 0;
    if (ellipsis) typeFlags |= ReferenceParser.ELLIPSIS;
    if (disjunctiveType) typeFlags |= ReferenceParser.DISJUNCTIONS;
    if (varType) typeFlags |= ReferenceParser.VAR_TYPE;
    return parseListElement(builder, true, typeFlags, isParameter ? JavaElementType.PARAMETER : JavaElementType.RECORD_COMPONENT);
  }

  @Nullable
  public PsiBuilder.Marker parseResource(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();

    PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    if (expr != null && RESOURCE_EXPRESSIONS.contains(exprType(expr)) && builder.getTokenType() != JavaTokenType.IDENTIFIER) {
      marker.done(JavaElementType.RESOURCE_EXPRESSION);
      return marker;
    }

    marker.rollbackTo();

    return parseListElement(builder, true, ReferenceParser.VAR_TYPE, JavaElementType.RESOURCE_VARIABLE);
  }

  @Nullable
  public PsiBuilder.Marker parseLambdaParameter(PsiBuilder builder, boolean typed) {
    int flags = ReferenceParser.ELLIPSIS;
    if (getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_11)) flags |= ReferenceParser.VAR_TYPE;
    return parseListElement(builder, typed, flags, JavaElementType.PARAMETER);
  }


  @Nullable
  private PsiBuilder.Marker parseListElement(PsiBuilder builder, boolean typed, int typeFlags, IElementType type) {
    PsiBuilder.Marker param = builder.mark();

    Pair<PsiBuilder.Marker, Boolean> modListInfo = parseModifierList(builder);

    ReferenceParser.TypeInfo typeInfo = null;
    if (typed) {
      int flags = ReferenceParser.EAT_LAST_DOT | ReferenceParser.WILDCARD | typeFlags;
      typeInfo = myParser.getReferenceParser().parseTypeInfo(builder, flags);

      if (typeInfo == null) {
        if (Boolean.TRUE.equals(modListInfo.second)) {
          param.rollbackTo();
          return null;
        }
        else {
          error(builder, JavaPsiBundle.message("expected.type"));
          emptyElement(builder, JavaElementType.TYPE);
        }
      }
    }

    if (typed) {
      IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.THIS_KEYWORD || tokenType == JavaTokenType.IDENTIFIER && builder.lookAhead(1) == JavaTokenType.DOT) {
        PsiBuilder.Marker mark = builder.mark();

        PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
        if (expr != null && exprType(expr) == JavaElementType.THIS_EXPRESSION) {
          mark.drop();
          done(param, JavaElementType.RECEIVER_PARAMETER);
          return param;
        }

        mark.rollbackTo();
      }
    }

    if (expect(builder, JavaTokenType.IDENTIFIER)) {
      if (type == JavaElementType.PARAMETER || type == JavaElementType.RECORD_COMPONENT) {
        eatBrackets(builder, typeInfo != null && typeInfo.isVarArg ? "expected.rparen" : null);
        done(param, type);
        return param;
      }
    }
    else {
      error(builder, JavaPsiBundle.message("expected.identifier"));
      param.drop();
      return modListInfo.first;
    }

    if (expectOrError(builder, JavaTokenType.EQ, "expected.eq")) {
      if (myParser.getExpressionParser().parse(builder) == null) {
        error(builder, JavaPsiBundle.message("expected.expression"));
      }
    }

    done(param, JavaElementType.RESOURCE_VARIABLE);
    return param;
  }

  @Nullable
  private PsiBuilder.Marker parseFieldOrLocalVariable(PsiBuilder builder, PsiBuilder.Marker declaration, int declarationStart, Context context) {
    final IElementType varType;
    if (context == Context.CLASS || context == Context.ANNOTATION_INTERFACE) {
      varType = JavaElementType.FIELD;
    }
    else if (context == Context.CODE_BLOCK) {
      varType = JavaElementType.LOCAL_VARIABLE;
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

      if (expect(builder, JavaTokenType.EQ)) {
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

      if (builder.getTokenType() != JavaTokenType.COMMA) break;
      done(variable, varType);
      builder.advanceLexer();

      if (builder.getTokenType() != JavaTokenType.IDENTIFIER) {
        error(builder, JavaPsiBundle.message("expected.identifier"));
        unclosed = true;
        eatSemicolon = false;
        openMarker = false;
        break;
      }

      variable = builder.mark();
      builder.advanceLexer();
    }

    if (builder.getTokenType() == JavaTokenType.SEMICOLON && eatSemicolon) {
      builder.advanceLexer();
    }
    else {
      // special treatment (see DeclarationParserTest.testMultiLineUnclosed())
      if (!builder.eof() && shouldRollback) {
        final CharSequence text = builder.getOriginalText();
        final int spaceEnd = builder.getCurrentOffset();
        final int spaceStart = CharArrayUtil.shiftBackward(text, spaceEnd-1, WHITESPACES);
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
      done(variable, varType);
    }

    return declaration;
  }

  private boolean eatBrackets(PsiBuilder builder, @Nullable @PropertyKey(resourceBundle = JavaPsiBundle.BUNDLE) String errorKey) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType != JavaTokenType.LBRACKET && tokenType != JavaTokenType.AT) return true;

    PsiBuilder.Marker marker = builder.mark();

    int count = 0;
    while (true) {
      parseAnnotations(builder);
      if (!expect(builder, JavaTokenType.LBRACKET)) {
        break;
      }
      ++count;
      if (!expect(builder, JavaTokenType.RBRACKET)) {
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

  @Nullable
  public PsiBuilder.Marker parseAnnotations(final PsiBuilder builder) {
    PsiBuilder.Marker firstAnno = null;

    while (builder.getTokenType() == JavaTokenType.AT) {
      final PsiBuilder.Marker anno = parseAnnotation(builder);
      if (firstAnno == null) firstAnno = anno;
    }

    return firstAnno;
  }

  @NotNull
  public PsiBuilder.Marker parseAnnotation(final PsiBuilder builder) {
    assert builder.getTokenType() == JavaTokenType.AT : builder.getTokenType();
    final PsiBuilder.Marker anno = builder.mark();
    builder.advanceLexer();

    PsiBuilder.Marker classRef = null;
    if (builder.getTokenType() == JavaTokenType.IDENTIFIER) {
      classRef = myParser.getReferenceParser().parseJavaCodeReference(builder, true, false, false, false);
    }
    if (classRef == null) {
      error(builder, JavaPsiBundle.message("expected.class.reference"));
    }

    parseAnnotationParameterList(builder);

    done(anno, JavaElementType.ANNOTATION);
    return anno;
  }

  private void parseAnnotationParameterList(final PsiBuilder builder) {
    PsiBuilder.Marker list = builder.mark();

    if (!expect(builder, JavaTokenType.LPARENTH)) {
      done(list, JavaElementType.ANNOTATION_PARAMETER_LIST);
      return;
    }

    if (expect(builder, JavaTokenType.RPARENTH)) {
      done(list, JavaElementType.ANNOTATION_PARAMETER_LIST);
      return;
    }

    final boolean isFirstParamNamed = parseAnnotationParameter(builder, true);
    boolean isFirstParamWarned = false;

    boolean afterBad = false;
    while (true) {
      final IElementType tokenType = builder.getTokenType();
      if (tokenType == null) {
        error(builder, JavaPsiBundle.message("expected.parameter"));
        break;
      }
      else if (expect(builder, JavaTokenType.RPARENTH)) {
        break;
      }
      else if (tokenType == JavaTokenType.COMMA) {
        final PsiBuilder.Marker errorStart = builder.mark();
        final PsiBuilder.Marker errorEnd = builder.mark();
        builder.advanceLexer();
        final boolean hasParamName = parseAnnotationParameter(builder, false);
        if (!isFirstParamNamed && hasParamName && !isFirstParamWarned) {
          errorStart.errorBefore(JavaPsiBundle.message("annotation.name.is.missing"), errorEnd);
          isFirstParamWarned = true;
        }
        else {
          errorStart.drop();
        }
        errorEnd.drop();
      }
      else if (!afterBad) {
        error(builder, JavaPsiBundle.message("expected.comma.or.rparen"));
        builder.advanceLexer();
        afterBad = true;
      }
      else {
        afterBad = false;
        parseAnnotationParameter(builder, false);
      }
    }

    done(list, JavaElementType.ANNOTATION_PARAMETER_LIST);
  }

  private boolean parseAnnotationParameter(final PsiBuilder builder, final boolean mayBeSimple) {
    PsiBuilder.Marker pair = builder.mark();

    if (mayBeSimple) {
      parseAnnotationValue(builder);
      if (builder.getTokenType() != JavaTokenType.EQ) {
        done(pair, JavaElementType.NAME_VALUE_PAIR);
        return false;
      }

      pair.rollbackTo();
      pair = builder.mark();
    }

    final boolean hasName = expectOrError(builder, JavaTokenType.IDENTIFIER, "expected.identifier");

    expectOrError(builder, JavaTokenType.EQ, "expected.eq");

    parseAnnotationValue(builder);

    done(pair, JavaElementType.NAME_VALUE_PAIR);

    return hasName;
  }

  public void parseAnnotationValue(PsiBuilder builder) {
    PsiBuilder.Marker result = doParseAnnotationValue(builder);

    if (result == null) {
      result = builder.mark();
      result.error(JavaPsiBundle.message("expected.value"));
    }
  }

  @Nullable
  private PsiBuilder.Marker doParseAnnotationValue(PsiBuilder builder) {
    PsiBuilder.Marker result;

    IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.AT) {
      result = parseAnnotation(builder);
    }
    else if (tokenType == JavaTokenType.LBRACE) {
      result = myParser.getExpressionParser().parseArrayInitializer(
        builder, JavaElementType.ANNOTATION_ARRAY_INITIALIZER, this::doParseAnnotationValue, "expected.value");
    }
    else {
      result = myParser.getExpressionParser().parseConditional(builder);
    }

    return result;
  }
}