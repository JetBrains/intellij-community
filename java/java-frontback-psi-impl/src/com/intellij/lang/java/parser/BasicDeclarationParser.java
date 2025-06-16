// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.core.JavaPsiBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderUtil;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.AbstractBasicJavaElementTypeFactory;
import com.intellij.psi.impl.source.WhiteSpaceAndCommentSetHolder;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import static com.intellij.lang.PsiBuilderUtil.expect;
import static com.intellij.lang.java.parser.BasicJavaParserUtil.*;
import static com.intellij.psi.impl.source.BasicElementTypes.*;


/**
 * @deprecated Use the new Java syntax library instead.
 *             See {@link com.intellij.java.syntax.parser.JavaParser}
 */
@Deprecated
@SuppressWarnings("UnnecessarilyQualifiedStaticUsage")  //suppress to be clear, what type is used
@ApiStatus.Experimental
public class BasicDeclarationParser {
  public enum BaseContext {
    FILE, CLASS, CODE_BLOCK, ANNOTATION_INTERFACE, JSHELL
  }

  private static final TokenSet BEFORE_LBRACE_ELEMENTS_SET = TokenSet.create(
    JavaTokenType.IDENTIFIER, JavaTokenType.COMMA, JavaTokenType.EXTENDS_KEYWORD, JavaTokenType.IMPLEMENTS_KEYWORD, JavaTokenType.LPARENTH);
  private static final TokenSet APPEND_TO_METHOD_SET = TokenSet.create(
    JavaTokenType.IDENTIFIER, JavaTokenType.COMMA, JavaTokenType.THROWS_KEYWORD);
  private static final TokenSet PARAM_LIST_STOPPERS = TokenSet.create(
    JavaTokenType.RPARENTH, JavaTokenType.LBRACE, JavaTokenType.ARROW);
  private static final TokenSet METHOD_PARAM_LIST_STOPPERS = TokenSet.create(
    JavaTokenType.RPARENTH, JavaTokenType.LBRACE, JavaTokenType.ARROW, JavaTokenType.SEMICOLON);
  private static final TokenSet TYPE_START = TokenSet.orSet(
    BASIC_PRIMITIVE_TYPE_BIT_SET, TokenSet.create(JavaTokenType.IDENTIFIER, JavaTokenType.AT, JavaTokenType.VAR_KEYWORD));
  private final TokenSet RESOURCE_EXPRESSIONS;

  private static final String WHITESPACES = "\n\r \t";
  private static final String LINE_ENDS = "\n\r";

  private final BasicJavaParser myParser;
  private final AbstractBasicJavaElementTypeFactory.JavaElementTypeContainer myJavaElementTypeContainer;

  public BasicDeclarationParser(@NotNull BasicJavaParser javaParser) {
    myParser = javaParser;
    myJavaElementTypeContainer = javaParser.getJavaElementTypeFactory().getContainer();
    RESOURCE_EXPRESSIONS = TokenSet.create(
      myJavaElementTypeContainer.REFERENCE_EXPRESSION, myJavaElementTypeContainer.THIS_EXPRESSION,
      myJavaElementTypeContainer.METHOD_CALL_EXPRESSION, myJavaElementTypeContainer.NEW_EXPRESSION);
  }

  public void parseClassBodyWithBraces(PsiBuilder builder, boolean isAnnotation, boolean isEnum) {
    assert builder.getTokenType() == JavaTokenType.LBRACE : builder.getTokenType();
    builder.advanceLexer();

    final PsiBuilder builderWrapper = braceMatchingBuilder(builder);
    if (isEnum) {
      parseEnumConstants(builderWrapper);
    }
    parseClassBodyDeclarations(builderWrapper, isAnnotation);

    expectOrError(builder, JavaTokenType.RBRACE, "expected.rbrace");
  }

  private @Nullable PsiBuilder.Marker parseClassFromKeyword(PsiBuilder builder,
                                                            PsiBuilder.Marker declaration,
                                                            boolean isAnnotation,
                                                            BaseContext context) {
    IElementType keywordTokenType = builder.getTokenType();
    final boolean isRecord = isRecordToken(builder, keywordTokenType);
    if (isRecord) {
      builder.remapCurrentToken(JavaTokenType.RECORD_KEYWORD);
      if (builder.lookAhead(1) != JavaTokenType.IDENTIFIER) {
        builder.advanceLexer();
        error(builder, JavaPsiBundle.message("expected.identifier"));
        declaration.drop();
        return null;
      }
      final IElementType afterIdent = builder.lookAhead(2);
      // No parser recovery for local records without < or ( to support light stubs
      // (look at com.intellij.psi.impl.source.JavaLightStubBuilder.CodeBlockVisitor.visit)
      if (context == BaseContext.CODE_BLOCK && afterIdent != JavaTokenType.LPARENTH && afterIdent != JavaTokenType.LT) {
        // skipping record kw and identifier
        PsiBuilderUtil.advance(builder, 2);
        error(builder, JavaPsiBundle.message("expected.lt.or.lparen"));
        declaration.drop();
        return null;
      }
      keywordTokenType = JavaTokenType.RECORD_KEYWORD;
    }
    assert BASIC_CLASS_KEYWORD_BIT_SET.contains(keywordTokenType) : keywordTokenType;
    builder.advanceLexer();
    final boolean isEnum = (keywordTokenType == JavaTokenType.ENUM_KEYWORD);

    if (!expect(builder, JavaTokenType.IDENTIFIER)) {
      error(builder, JavaPsiBundle.message("expected.identifier"));
      declaration.drop();
      return null;
    }

    final BasicReferenceParser refParser = myParser.getReferenceParser();
    refParser.parseTypeParameters(builder);

    if (builder.getTokenType() == JavaTokenType.LPARENTH) {
      parseElementList(builder, ListType.RECORD_COMPONENTS);
    }

    refParser.parseReferenceList(builder, JavaTokenType.EXTENDS_KEYWORD, myJavaElementTypeContainer.EXTENDS_LIST, JavaTokenType.COMMA);
    refParser.parseReferenceList(builder, JavaTokenType.IMPLEMENTS_KEYWORD, myJavaElementTypeContainer.IMPLEMENTS_LIST,
                                 JavaTokenType.COMMA);
    if (builder.getTokenType() == JavaTokenType.IDENTIFIER &&
        JavaKeywords.PERMITS.equals(builder.getTokenText())) {
      builder.remapCurrentToken(JavaTokenType.PERMITS_KEYWORD);
    }
    if (builder.getTokenType() == JavaTokenType.PERMITS_KEYWORD) {
      refParser.parseReferenceList(builder, JavaTokenType.PERMITS_KEYWORD, myJavaElementTypeContainer.PERMITS_LIST, JavaTokenType.COMMA);
    }

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

    done(declaration, myJavaElementTypeContainer.CLASS, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
    return declaration;
  }

  private void parseEnumConstants(PsiBuilder builder) {
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

      int commaCount = 0;
      while (builder.getTokenType() == JavaTokenType.COMMA) {
        if (commaCount > 0) {
          error(builder, JavaPsiBundle.message("expected.identifier"));
        }
        builder.advanceLexer();
        commaCount++;
      }
      if (commaCount == 0 &&
          builder.getTokenType() != null &&
          builder.getTokenType() != JavaTokenType.SEMICOLON) {
        error(builder, JavaPsiBundle.message("expected.comma.or.semicolon"));
        return;
      }
    }
  }

  public @Nullable PsiBuilder.Marker parseEnumConstant(PsiBuilder builder) {
    final PsiBuilder.Marker constant = builder.mark();

    parseModifierList(builder);

    if (expect(builder, JavaTokenType.IDENTIFIER)) {
      if (builder.getTokenType() == JavaTokenType.LPARENTH) {
        myParser.getExpressionParser().parseArgumentList(builder);
      }
      else {
        emptyElement(builder, myJavaElementTypeContainer.EXPRESSION_LIST);
      }

      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        final PsiBuilder.Marker constantInit = builder.mark();
        parseClassBodyWithBraces(builder, false, false);
        done(constantInit, myJavaElementTypeContainer.ENUM_CONSTANT_INITIALIZER, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
      }

      done(constant, myJavaElementTypeContainer.ENUM_CONSTANT, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
      return constant;
    }
    else {
      constant.rollbackTo();
      return null;
    }
  }

  public void parseClassBodyDeclarations(PsiBuilder builder, boolean isAnnotation) {
    final BaseContext context = isAnnotation ? BaseContext.ANNOTATION_INTERFACE : BaseContext.CLASS;

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

  public @Nullable PsiBuilder.Marker parse(@NotNull PsiBuilder builder, BaseContext context) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType == null) return null;

    if (tokenType == JavaTokenType.LBRACE) {
      if (context == BaseContext.FILE || context == BaseContext.CODE_BLOCK) return null;
    }
    else if (!isRecordToken(builder, tokenType) && !isSealedToken(builder, tokenType) && !isNonSealedToken(builder, tokenType)) {
      if (!TYPE_START.contains(tokenType) || tokenType == JavaTokenType.AT) {
        if (tokenType instanceof ILazyParseableElementType) {
          builder.advanceLexer();
          return null;
        }
        else if (!BASIC_MODIFIER_BIT_SET.contains(tokenType) &&
                 !BASIC_CLASS_KEYWORD_BIT_SET.contains(tokenType) &&
                 tokenType != JavaTokenType.AT &&
                 (context == BaseContext.CODE_BLOCK || tokenType != JavaTokenType.LT)) {
          return null;
        }
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
    if (BASIC_CLASS_KEYWORD_BIT_SET.contains(builder.getTokenType()) || isRecordToken(builder, builder.getTokenType())) {
      final PsiBuilder.Marker result = parseClassFromKeyword(builder, declaration, false, context);
      return result != null ? result : modList;
    }

    PsiBuilder.Marker typeParams = null;
    if (builder.getTokenType() == JavaTokenType.LT && context != BaseContext.CODE_BLOCK) {
      typeParams = myParser.getReferenceParser().parseTypeParameters(builder);
    }

    if (builder.getTokenType() == JavaTokenType.LBRACE) {
      if (context == BaseContext.CODE_BLOCK) {
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

      done(declaration, myJavaElementTypeContainer.CLASS_INITIALIZER, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
      return declaration;
    }

    BasicReferenceParser.TypeInfo type = null;

    if (TYPE_START.contains(builder.getTokenType())) {
      PsiBuilder.Marker pos = builder.mark();

      int flags = BasicReferenceParser.EAT_LAST_DOT | BasicReferenceParser.WILDCARD;
      if (context == BaseContext.CODE_BLOCK ||  context == BaseContext.JSHELL) flags |= BasicReferenceParser.VAR_TYPE;
      type = myParser.getReferenceParser().parseTypeInfo(builder, flags);

      if (type == null) {
        pos.rollbackTo();
      }
      else if (builder.getTokenType() == JavaTokenType.LPARENTH ||
               builder.getTokenType() == JavaTokenType.LBRACE ||
               builder.getTokenType() == JavaTokenType.THROWS_KEYWORD) {  // constructor
        if (context == BaseContext.CODE_BLOCK) {
          declaration.rollbackTo();
          return null;
        }

        pos.rollbackTo();
        if (typeParams == null) {
          emptyElement(builder, myJavaElementTypeContainer.TYPE_PARAMETER_LIST);
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
          emptyElement(builder, myJavaElementTypeContainer.THROWS_LIST);
          return parseMethodBody(builder, declaration, false);
        }
        else if (builder.getTokenType() == JavaTokenType.THROWS_KEYWORD) {
          myParser.getReferenceParser()
            .parseReferenceList(builder, JavaTokenType.THROWS_KEYWORD, myJavaElementTypeContainer.THROWS_LIST, JavaTokenType.COMMA);
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
      if (context != BaseContext.CODE_BLOCK ||
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
      if (context == BaseContext.CLASS || context == BaseContext.ANNOTATION_INTERFACE || context == BaseContext.FILE
          || context == BaseContext.JSHELL) {  // method
        if (typeParams == null) {
          emptyElement(type.marker, myJavaElementTypeContainer.TYPE_PARAMETER_LIST);
        }
        return parseMethodFromLeftParenth(builder, declaration, (context == BaseContext.ANNOTATION_INTERFACE), false);
      }
    }

    if (typeParams != null) {
      typeParams.precede().errorBefore(JavaPsiBundle.message("unexpected.token"), type.marker);
    }
    return parseFieldOrLocalVariable(builder, declaration, declarationStart, context);
  }

  static boolean isRecordToken(PsiBuilder builder, IElementType tokenType) {
    if (tokenType == JavaTokenType.IDENTIFIER && JavaKeywords.RECORD.equals(builder.getTokenText())) {
      IElementType nextToken = builder.lookAhead(1);
      if (nextToken == JavaTokenType.IDENTIFIER || 
          // The following tokens cannot be part of a valid record declaration, 
          // but we assume it to be a malformed record, rather than a malformed type.
          BASIC_MODIFIER_BIT_SET.contains(nextToken) || BASIC_CLASS_KEYWORD_BIT_SET.contains(nextToken) || TYPE_START.contains(nextToken) ||
          nextToken == JavaTokenType.AT || nextToken == JavaTokenType.LBRACE || nextToken == JavaTokenType.RBRACE) {
        return JavaFeature.RECORDS.isSufficient(getLanguageLevel(builder));
      }
    }
    return false;
  }

  private static boolean isSealedToken(PsiBuilder builder, IElementType tokenType) {
    return JavaFeature.SEALED_CLASSES.isSufficient(getLanguageLevel(builder)) &&
           tokenType == JavaTokenType.IDENTIFIER &&
           JavaKeywords.SEALED.equals(builder.getTokenText());
  }

  private static boolean isValueToken(PsiBuilder builder, IElementType tokenType) {
    return JavaFeature.VALHALLA_VALUE_CLASSES.isSufficient(getLanguageLevel(builder)) &&
           tokenType == JavaTokenType.IDENTIFIER &&
           JavaKeywords.VALUE.equals(builder.getTokenText());
  }

   static boolean isNonSealedToken(PsiBuilder builder, IElementType tokenType) {
    if (!JavaFeature.SEALED_CLASSES.isSufficient(getLanguageLevel(builder)) ||
        tokenType != JavaTokenType.IDENTIFIER ||
        !"non".equals(builder.getTokenText()) ||
        builder.lookAhead(1) != JavaTokenType.MINUS ||
        builder.lookAhead(2) != JavaTokenType.IDENTIFIER) {
      return false;
    }
    PsiBuilder.Marker maybeNonSealed = builder.mark();
    PsiBuilderUtil.advance(builder, 2);
    boolean isNonSealed = JavaKeywords.SEALED.equals(builder.getTokenText());
    maybeNonSealed.rollbackTo();
    return isNonSealed;
  }

  public @NotNull Pair<PsiBuilder.Marker, Boolean> parseModifierList(PsiBuilder builder) {
    return parseModifierList(builder, BASIC_MODIFIER_BIT_SET);
  }

  public @NotNull Pair<PsiBuilder.Marker, Boolean> parseModifierList(PsiBuilder builder, TokenSet modifiers) {
    final PsiBuilder.Marker modList = builder.mark();
    boolean isEmpty = true;

    while (true) {
      IElementType tokenType = builder.getTokenType();
      if (tokenType == null) break;
      if (isValueToken(builder, tokenType)) {
        builder.remapCurrentToken(JavaTokenType.VALUE_KEYWORD);
        tokenType = JavaTokenType.VALUE_KEYWORD;
      }
      else if (isSealedToken(builder, tokenType)) {
        builder.remapCurrentToken(JavaTokenType.SEALED_KEYWORD);
        tokenType = JavaTokenType.SEALED_KEYWORD;
      }
      if (isNonSealedToken(builder, tokenType)) {
        PsiBuilder.Marker nonSealed = builder.mark();
        PsiBuilderUtil.advance(builder, 3);
        nonSealed.collapse(JavaTokenType.NON_SEALED_KEYWORD);
        isEmpty = false;
      }
      else if (modifiers.contains(tokenType)) {
        builder.advanceLexer();
        isEmpty = false;
      }
      else if (tokenType == JavaTokenType.AT) {
        if (BASIC_KEYWORD_BIT_SET.contains(builder.lookAhead(1))) {
          break;
        }
        parseAnnotation(builder);
        isEmpty = false;
      }
      else {
        break;
      }
    }

    done(modList, myJavaElementTypeContainer.MODIFIER_LIST, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
    return Pair.create(modList, isEmpty);
  }

  private PsiBuilder.Marker parseMethodFromLeftParenth(PsiBuilder builder,
                                                       PsiBuilder.Marker declaration,
                                                       boolean anno,
                                                       boolean constructor) {
    parseParameterList(builder);

    eatBrackets(builder, constructor ? "expected.lbrace" : null);

    myParser.getReferenceParser()
      .parseReferenceList(builder, JavaTokenType.THROWS_KEYWORD, myJavaElementTypeContainer.THROWS_LIST, JavaTokenType.COMMA);

    if (anno && expect(builder, JavaTokenType.DEFAULT_KEYWORD) && parseAnnotationValue(builder) == null) {
      error(builder, JavaPsiBundle.message("expected.value"));
    }

    return parseMethodBody(builder, declaration, anno);
  }

  private @NotNull PsiBuilder.Marker parseMethodBody(PsiBuilder builder, PsiBuilder.Marker declaration, boolean anno) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType != JavaTokenType.SEMICOLON && tokenType != JavaTokenType.LBRACE) {
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

    if (!expect(builder, JavaTokenType.SEMICOLON)) {
      if (builder.getTokenType() == JavaTokenType.LBRACE) {
        myParser.getStatementParser().parseCodeBlock(builder);
      }
    }

    done(declaration, anno ? myJavaElementTypeContainer.ANNOTATION_METHOD : myJavaElementTypeContainer.METHOD,
         builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
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

    IElementType getNodeType(AbstractBasicJavaElementTypeFactory.JavaElementTypeContainer javaElementTypeContainer) {
      if (this == RESOURCE) {
        return javaElementTypeContainer.RESOURCE_LIST;
      }
      if (this == RECORD_COMPONENTS) {
        return javaElementTypeContainer.RECORD_HEADER;
      }
      return javaElementTypeContainer.PARAMETER_LIST;
    }

    TokenSet getStopperTypes() {
      if (this == METHOD) {
        return METHOD_PARAM_LIST_STOPPERS;
      }
      return PARAM_LIST_STOPPERS;
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
      if (tokenType == null || type.getStopperTypes().contains(tokenType)) {
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

    done(elementList, type.getNodeType(myJavaElementTypeContainer), builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
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
    if (ellipsis) typeFlags |= BasicReferenceParser.ELLIPSIS;
    if (disjunctiveType) typeFlags |= BasicReferenceParser.DISJUNCTIONS;
    if (varType) typeFlags |= BasicReferenceParser.VAR_TYPE;
    return parseListElement(builder, true, typeFlags,
                            isParameter ? myJavaElementTypeContainer.PARAMETER : myJavaElementTypeContainer.RECORD_COMPONENT);
  }

  public @Nullable PsiBuilder.Marker parseResource(PsiBuilder builder) {
    PsiBuilder.Marker marker = builder.mark();

    PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
    if (expr != null && RESOURCE_EXPRESSIONS.contains(exprType(expr)) && builder.getTokenType() != JavaTokenType.IDENTIFIER) {
      marker.done(myJavaElementTypeContainer.RESOURCE_EXPRESSION);
      return marker;
    }

    marker.rollbackTo();

    return parseListElement(builder, true, BasicReferenceParser.VAR_TYPE, myJavaElementTypeContainer.RESOURCE_VARIABLE);
  }

  public @Nullable PsiBuilder.Marker parseLambdaParameter(PsiBuilder builder, boolean typed) {
    int flags = BasicReferenceParser.ELLIPSIS;
    if (JavaFeature.VAR_LAMBDA_PARAMETER.isSufficient(getLanguageLevel(builder))) flags |= BasicReferenceParser.VAR_TYPE;
    return parseListElement(builder, typed, flags, myJavaElementTypeContainer.PARAMETER);
  }


  private @Nullable PsiBuilder.Marker parseListElement(PsiBuilder builder, boolean typed, int typeFlags, IElementType type) {
    PsiBuilder.Marker param = builder.mark();

    Pair<PsiBuilder.Marker, Boolean> modListInfo = parseModifierList(builder);

    if (typed) {
      int flags = BasicReferenceParser.EAT_LAST_DOT | BasicReferenceParser.WILDCARD | typeFlags;
      BasicReferenceParser.TypeInfo typeInfo = myParser.getReferenceParser().parseTypeInfo(builder, flags);

      if (typeInfo == null) {
        if (Boolean.TRUE.equals(modListInfo.second)) {
          param.rollbackTo();
          return null;
        }
        else {
          error(builder, JavaPsiBundle.message("expected.type"));
          emptyElement(builder, myJavaElementTypeContainer.TYPE);
        }
      }
    }

    if (typed) {
      IElementType tokenType = builder.getTokenType();
      if (tokenType == JavaTokenType.THIS_KEYWORD || tokenType == JavaTokenType.IDENTIFIER && builder.lookAhead(1) == JavaTokenType.DOT) {
        PsiBuilder.Marker mark = builder.mark();

        PsiBuilder.Marker expr = myParser.getExpressionParser().parse(builder);
        if (expr != null && exprType(expr) == myJavaElementTypeContainer.THIS_EXPRESSION) {
          mark.drop();
          done(param, myJavaElementTypeContainer.RECEIVER_PARAMETER, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
          return param;
        }

        mark.rollbackTo();
      }
    }

    if (expect(builder, JavaTokenType.IDENTIFIER)) {
      if (type == myJavaElementTypeContainer.PARAMETER || type == myJavaElementTypeContainer.RECORD_COMPONENT) {
        eatBrackets(builder, null);
        done(param, type, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
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

    done(param, myJavaElementTypeContainer.RESOURCE_VARIABLE, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
    return param;
  }

  private @Nullable PsiBuilder.Marker parseFieldOrLocalVariable(PsiBuilder builder,
                                                                PsiBuilder.Marker declaration,
                                                                int declarationStart,
                                                                BaseContext context) {
    final IElementType varType;
    if (context == BaseContext.CLASS || context == BaseContext.ANNOTATION_INTERFACE || context == BaseContext.FILE
        || context == BaseContext.JSHELL) {
      varType = myJavaElementTypeContainer.FIELD;
    }
    else if (context == BaseContext.CODE_BLOCK) {
      varType = myJavaElementTypeContainer.LOCAL_VARIABLE;
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
      done(variable, varType, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
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
      done(variable, varType, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
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

  public @Nullable PsiBuilder.Marker parseAnnotations(PsiBuilder builder) {
    PsiBuilder.Marker firstAnno = null;

    while (builder.getTokenType() == JavaTokenType.AT) {
      final PsiBuilder.Marker anno = parseAnnotation(builder);
      if (firstAnno == null) firstAnno = anno;
    }

    return firstAnno;
  }

  public @NotNull PsiBuilder.Marker parseAnnotation(PsiBuilder builder) {
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

    done(anno, myJavaElementTypeContainer.ANNOTATION, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
    return anno;
  }

  private void parseAnnotationParameterList(PsiBuilder builder) {
    PsiBuilder.Marker list = builder.mark();

    if (!expect(builder, JavaTokenType.LPARENTH) || expect(builder, JavaTokenType.RPARENTH)) {
      done(list, myJavaElementTypeContainer.ANNOTATION_PARAMETER_LIST, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
      return;
    }

    if (builder.getTokenType() == null) {
      error(builder, JavaPsiBundle.message("expected.parameter.or.rparen"));
      done(list, myJavaElementTypeContainer.ANNOTATION_PARAMETER_LIST, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
      return;
    }
    PsiBuilder.Marker elementMarker = parseAnnotationElement(builder);
    while (true) {
      IElementType tokenType = builder.getTokenType();
      if (tokenType == null) {
        error(builder, JavaPsiBundle.message(elementMarker == null ? "expected.parameter.or.rparen" : "expected.comma.or.rparen"));
        break;
      }
      else if (expect(builder, JavaTokenType.RPARENTH)) {
        break;
      }
      else if (tokenType == JavaTokenType.COMMA) {
        builder.advanceLexer();
        elementMarker = parseAnnotationElement(builder);
        if (elementMarker == null) {
          error(builder, JavaPsiBundle.message("annotation.name.is.missing"));
          tokenType = builder.getTokenType();
          if (tokenType != JavaTokenType.COMMA && tokenType != JavaTokenType.RPARENTH) {
            break;
          }
        }
      }
      else {
        error(builder, JavaPsiBundle.message(elementMarker == null ? "expected.parameter.or.rparen" : "expected.comma.or.rparen"));
        tokenType = builder.lookAhead(1);
        if (tokenType != JavaTokenType.COMMA && tokenType != JavaTokenType.RPARENTH) break;
        builder.advanceLexer();
      }
    }

    done(list, myJavaElementTypeContainer.ANNOTATION_PARAMETER_LIST, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
  }

  private PsiBuilder.Marker parseAnnotationElement(PsiBuilder builder) {
    PsiBuilder.Marker pair = builder.mark();

    PsiBuilder.Marker valueMarker = parseAnnotationValue(builder);
    if (valueMarker == null && builder.getTokenType() != JavaTokenType.EQ) {
      pair.drop();
      return null;
    }
    if (builder.getTokenType() != JavaTokenType.EQ) {
      done(pair, myJavaElementTypeContainer.NAME_VALUE_PAIR, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
      return pair;
    }

    pair.rollbackTo();
    pair = builder.mark();

    expectOrError(builder, JavaTokenType.IDENTIFIER, "expected.identifier");
    expect(builder, JavaTokenType.EQ);
    valueMarker = parseAnnotationValue(builder);
    if (valueMarker == null) error(builder, JavaPsiBundle.message("expected.value"));

    done(pair, myJavaElementTypeContainer.NAME_VALUE_PAIR, builder, WhiteSpaceAndCommentSetHolder.INSTANCE);
    return pair;
  }

  public @Nullable PsiBuilder.Marker parseAnnotationValue(PsiBuilder builder) {
    IElementType tokenType = builder.getTokenType();
    if (tokenType == JavaTokenType.AT) {
      return parseAnnotation(builder);
    }
    else if (tokenType == JavaTokenType.LBRACE) {
      return myParser.getExpressionParser().parseArrayInitializer(
        builder, myJavaElementTypeContainer.ANNOTATION_ARRAY_INITIALIZER, this::parseAnnotationValue, "expected.value");
    }
    else {
      return myParser.getExpressionParser().parseConditional(builder);
    }
  }
}