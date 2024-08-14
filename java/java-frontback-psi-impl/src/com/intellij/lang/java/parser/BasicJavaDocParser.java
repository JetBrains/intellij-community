// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesBinders;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.AbstractBasicJavaDocElementTypeFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class BasicJavaDocParser {
  private static final TokenSet TAG_VALUES_SET = TokenSet.create(
    JavaDocTokenType.DOC_TAG_VALUE_TOKEN, JavaDocTokenType.DOC_TAG_VALUE_COMMA, JavaDocTokenType.DOC_TAG_VALUE_DOT,
    JavaDocTokenType.DOC_TAG_VALUE_LPAREN, JavaDocTokenType.DOC_TAG_VALUE_RPAREN, JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN,
    JavaDocTokenType.DOC_TAG_VALUE_LT, JavaDocTokenType.DOC_TAG_VALUE_GT, JavaDocTokenType.DOC_TAG_VALUE_COLON,
    JavaDocTokenType.DOC_TAG_VALUE_QUOTE
  );

  private static final TokenSet INLINE_TAG_BORDERS_SET = TokenSet.create(
    JavaDocTokenType.DOC_INLINE_TAG_START, JavaDocTokenType.DOC_INLINE_TAG_END);

  private static final TokenSet SKIP_TOKENS = TokenSet.create(JavaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS);

  private static final String SEE_TAG = "@see";
  private static final String LINK_TAG = "@link";
  private static final String LINK_PLAIN_TAG = "@linkplain";
  private static final String PARAM_TAG = "@param";
  private static final String VALUE_TAG = "@value";
  private static final String SNIPPET_TAG = "@snippet";
  private static final Set<String> REFERENCE_TAGS = ContainerUtil.immutableSet("@throws", "@exception", "@provides", "@uses");

  private static final Key<Integer> BRACE_SCOPE_KEY = Key.create("Javadoc.Parser.Brace.Scope");

  private BasicJavaDocParser() { }

  public static void parseJavadocReference(@NotNull PsiBuilder builder, @NotNull BasicJavaParser parser) {
    parser.getReferenceParser().parseJavaCodeReference(builder, true, true, false, false);
    swallowTokens(builder);
  }

  public static void parseJavadocType(@NotNull PsiBuilder builder, @NotNull BasicJavaParser parser) {
    parser.getReferenceParser()
      .parseType(builder, BasicReferenceParser.EAT_LAST_DOT | BasicReferenceParser.ELLIPSIS | BasicReferenceParser.WILDCARD);
    swallowTokens(builder);
  }

  private static void swallowTokens(PsiBuilder builder) {
    while (!builder.eof()) builder.advanceLexer();
  }

  public static void parseDocCommentText(@NotNull PsiBuilder builder,
                                         @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    builder.enforceCommentTokens(SKIP_TOKENS);

    while (!builder.eof()) {
      IElementType tokenType = getTokenType(builder);
      if (tokenType == JavaDocTokenType.DOC_TAG_NAME) {
        parseTag(builder, javaDocElementTypeContainer);
      }
      else {
        parseDataItem(builder, null, false, javaDocElementTypeContainer);
      }
    }
  }

  private static void parseTag(PsiBuilder builder,
                               @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    String tagName = builder.getTokenText();
    PsiBuilder.Marker tag = builder.mark();
    builder.advanceLexer();
    while (true) {
      IElementType tokenType = getTokenType(builder);
      if (tokenType == null || tokenType == JavaDocTokenType.DOC_TAG_NAME || tokenType == JavaDocTokenType.DOC_COMMENT_END) break;
      parseDataItem(builder, tagName, false, javaDocElementTypeContainer);
    }
    tag.done(javaDocElementTypeContainer.DOC_TAG);
  }

  private static void parseDataItem(PsiBuilder builder,
                                    @Nullable String tagName,
                                    boolean isInline,
                                    @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    IElementType tokenType = getTokenType(builder);
    if (tokenType == JavaDocTokenType.DOC_INLINE_TAG_START) {
      int braceScope = getBraceScope(builder);
      if (braceScope > 0) {
        setBraceScope(builder, braceScope + 1);
        builder.remapCurrentToken(JavaDocTokenType.DOC_COMMENT_DATA);
        builder.advanceLexer();
        return;
      }

      PsiBuilder.Marker tag = builder.mark();
      builder.advanceLexer();

      tokenType = getTokenType(builder);
      if (tokenType != JavaDocTokenType.DOC_TAG_NAME && tokenType != JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
        tag.rollbackTo();
        builder.remapCurrentToken(JavaDocTokenType.DOC_COMMENT_DATA);
        builder.advanceLexer();
        return;
      }

      setBraceScope(builder, braceScope + 1);
      String inlineTagName = "";

      while (true) {
        tokenType = getTokenType(builder);
        if (tokenType == JavaDocTokenType.DOC_TAG_NAME) {
          inlineTagName = builder.getTokenText();
        }
        else if (tokenType == null || tokenType == JavaDocTokenType.DOC_COMMENT_END) {
          break;
        }

        parseDataItem(builder, inlineTagName, true, javaDocElementTypeContainer);
        if (tokenType == JavaDocTokenType.DOC_INLINE_TAG_END) {
          braceScope = getBraceScope(builder);
          if (braceScope > 0) setBraceScope(builder, --braceScope);
          if (braceScope == 0) break;
        }
      }

      if (SNIPPET_TAG.equals(inlineTagName)) {
        tag.done(javaDocElementTypeContainer.DOC_SNIPPET_TAG);
      }
      else {
        tag.done(javaDocElementTypeContainer.DOC_INLINE_TAG);
      }
    }
    else if (TAG_VALUES_SET.contains(tokenType)) {
      if (SEE_TAG.equals(tagName) && !isInline ||
          LINK_TAG.equals(tagName) && isInline ||
          BasicJavaParserUtil.getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_1_4) && LINK_PLAIN_TAG.equals(tagName) && isInline) {
        parseSeeTagValue(builder, false, javaDocElementTypeContainer);
      }
      else if (!isInline && tagName != null && REFERENCE_TAGS.contains(tagName)) {
        PsiBuilder.Marker tagValue = builder.mark();
        builder.remapCurrentToken(javaDocElementTypeContainer.DOC_REFERENCE_HOLDER);
        builder.advanceLexer();
        tagValue.done(javaDocElementTypeContainer.DOC_TAG_VALUE_ELEMENT);
      }
      else if (!isInline && PARAM_TAG.equals(tagName)) {
        parseParameterRef(builder, javaDocElementTypeContainer);
      }
      else if (BasicJavaParserUtil.getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_1_5) && VALUE_TAG.equals(tagName) && isInline) {
        parseSeeTagValue(builder, true, javaDocElementTypeContainer);
      }
      else if (SNIPPET_TAG.equals(tagName) && isInline) {
        parseSnippetTagValue(builder, javaDocElementTypeContainer);
      }
      else {
        parseSimpleTagValue(builder, javaDocElementTypeContainer);
      }
    }
    else if (tokenType == JavaDocTokenType.DOC_CODE_FENCE) {
      parseCodeBlock(builder, javaDocElementTypeContainer);
    }
    else if (tokenType == JavaDocTokenType.DOC_LBRACKET) {
      parseMarkdownReferenceChecked(builder, javaDocElementTypeContainer);
    }
    else {
      remapAndAdvance(builder);
    }
  }

  private static void parseCodeBlock(PsiBuilder builder,
                                     AbstractBasicJavaDocElementTypeFactory.@NotNull JavaDocElementTypeContainer javaDocElementTypeContainer) {
    if (getBraceScope(builder) > 0) {
      builder.remapCurrentToken(JavaDocTokenType.DOC_COMMENT_DATA);
      builder.advanceLexer();
      return;
    }
    // Store the fence type, a fenced code block can only be closed by the same fence type
    char fenceStart = builder.getTokenText().charAt(0);
    PsiBuilder.Marker tag = builder.mark();

    // Look for the nearest closing code fence, converting everything inside as comment data
    while (!builder.eof()) {
      builder.advanceLexer();
      if (getTokenType(builder) == JavaDocTokenType.DOC_CODE_FENCE && builder.getTokenText().charAt(0) == fenceStart) {
        break;
      }
      builder.remapCurrentToken(JavaDocTokenType.DOC_COMMENT_DATA);
    }

    if (!builder.eof()) {
      builder.advanceLexer();
    }

    tag.done(javaDocElementTypeContainer.DOC_MARKDOWN_CODE_BLOCK);
  }

  /** Ensure a reference link is good before parsing it */
  private static void parseMarkdownReferenceChecked(PsiBuilder builder, @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    boolean hasLabel = true;
    PsiBuilder.Marker tag = builder.mark();

    // Step 1 ensure that we have a label
    int leftBracketCount = 1;
    int rightBracketCount = 0;
    int startLabelOffset = builder.getCurrentOffset();
    int endLabelOffset;

    // Labels are allowed balanced brackets, count each side
    while (!builder.eof()) {
      IElementType token = findInlineToken(builder, JavaDocTokenType.DOC_RBRACKET, JavaDocTokenType.DOC_LBRACKET, true);
      if (token == JavaDocTokenType.DOC_LBRACKET) {
        leftBracketCount++;
        continue;
      }
      if (token == JavaDocTokenType.DOC_RBRACKET) {
        rightBracketCount++;
        if (leftBracketCount == rightBracketCount) break;
      }
    }

    endLabelOffset = builder.getCurrentOffset();
    boolean isShortRefEmpty = endLabelOffset - startLabelOffset <= 1;
    if (leftBracketCount != rightBracketCount || isShortRefEmpty) {
      // Stop if unbalanced brackets/empty reference
      tag.rollbackTo();
      builder.advanceLexer();
      return;
    }

    IElementType firstReferenceToken = findInlineToken(builder, JavaDocTokenType.DOC_LBRACKET, JavaDocTokenType.DOC_SPACE, false);
    if (firstReferenceToken != JavaDocTokenType.DOC_LBRACKET) {
      hasLabel = false;
      // The label is actually a reference, verify brackets balance or if we have a normal markdown link
      if (leftBracketCount > 1 || firstReferenceToken == JavaDocTokenType.DOC_LPAREN) {
        tag.rollbackTo();
        builder.advanceLexer();
        return;
      }
    }

    // Step 2 get the reference for full reference link
    if (hasLabel) {
      if (findInlineToken(builder, JavaDocTokenType.DOC_RBRACKET, JavaDocTokenType.DOC_LBRACKET, true) != JavaDocTokenType.DOC_RBRACKET) {
        hasLabel = false;
        // The label is actually a reference, verify brackets balance
        if (leftBracketCount > 1) {
          tag.rollbackTo();
          builder.advanceLexer();
          return;
        }
      }
    }

    // Step 3, validity ensured, parse the link
    tag.rollbackTo();
    tag = builder.mark();
    if (hasLabel) {
      // Label range already known, mark it as comment data
      while (!builder.eof()) {
        builder.advanceLexer();
        if (builder.getCurrentOffset() < endLabelOffset) {
          builder.remapCurrentToken(JavaDocTokenType.DOC_COMMENT_DATA);
          continue;
        }
        break;
      }
      builder.advanceLexer();
    }

    // Parse the reference itself
    builder.advanceLexer();
    parseMarkdownReference(builder, javaDocElementTypeContainer);
    builder.advanceLexer();

    tag.done(javaDocElementTypeContainer.DOC_MARKDOWN_REFERENCE_LINK);
  }

  private static void parseMarkdownReference(@NotNull PsiBuilder builder,
                                             @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    PsiBuilder.Marker refStart = builder.mark();
    if (getTokenType(builder) == JavaDocTokenType.DOC_RBRACKET) {
      refStart.drop();
      return;
    }

    if (getTokenType(builder) != JavaDocTokenType.DOC_SHARP) {
      builder.remapCurrentToken(javaDocElementTypeContainer.DOC_REFERENCE_HOLDER);
      builder.advanceLexer();
    }

    if (getTokenType(builder) == JavaDocTokenType.DOC_SHARP) {
      // Existing integration require this token for auto completion
      builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN);

      // method/variable name
      builder.advanceLexer();
      builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_VALUE_TOKEN);

      // A method only has parenthesis and a single comment data token
      builder.advanceLexer();
      if (builder.getTokenType() == JavaDocTokenType.DOC_LPAREN) {
        builder.advanceLexer();
        PsiBuilder.Marker subValue = builder.mark();

        if (getTokenType(builder) == JavaDocTokenType.DOC_COMMENT_DATA) {
          builder.remapCurrentToken(javaDocElementTypeContainer.DOC_TYPE_HOLDER);
          builder.advanceLexer();
        }

        if (getTokenType(builder) == JavaDocTokenType.DOC_RPAREN) {
          subValue.done(javaDocElementTypeContainer.DOC_TAG_VALUE_ELEMENT);
          builder.advanceLexer();
        }
        else {
          subValue.drop();
        }
      }

      refStart.done(javaDocElementTypeContainer.DOC_METHOD_OR_FIELD_REF);
      return;
    }
    refStart.drop();
  }

  /**
   * Look for the token provided by `needle`, taking into account markdown line break rules
   * @param travelToken The token that is either allowed or disallowed to encounter while looking for the `needle`
   * @param isTravelTokenDisallowed When `true`, the `travelToken` will abort the search
   *                                When `false`, encountering something other than `travelToken` or `needle` will abort the search
   * @return The last token encountered during the search.
   */
  @Contract(mutates = "param1")
  private static @Nullable IElementType findInlineToken(@NotNull PsiBuilder builder,
                                                        IElementType needle,
                                                        IElementType travelToken,
                                                        boolean isTravelTokenDisallowed) {
    IElementType token = null;
    IElementType previousToken;
    while (!builder.eof()) {
      builder.advanceLexer();
      previousToken = token;
      token = getTokenType(builder);
      if (token == needle) {
        return token;
      }
      boolean travelTokenFound = travelToken == token;
      if ((isTravelTokenDisallowed && travelTokenFound) || (!isTravelTokenDisallowed && !travelTokenFound)) {
        break;
      }

      // Markdown specific, check for EOL
      if (token == JavaDocTokenType.DOC_SPACE && previousToken == JavaDocTokenType.DOC_SPACE) {
        break;
      }
    }

    return builder.eof() ? null : token;
  }

  private static void parseSnippetTagValue(@NotNull PsiBuilder builder,
                                           @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    // we are right after @snippet
    PsiBuilder.Marker snippetValue = builder.mark();
    snippetValue.setCustomEdgeTokenBinders(WhitespacesBinders.GREEDY_LEFT_BINDER, WhitespacesBinders.GREEDY_RIGHT_BINDER);

    // recovery, when "foo" goes right after @snippet
    while (true) {
      IElementType token = getTokenType(builder);
      if (token != JavaDocTokenType.DOC_TAG_VALUE_QUOTE) {
        break;
      }
      builder.advanceLexer();
    }

    IElementType tokenType = getTokenType(builder);
    if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_COLON) {
      BasicJavaParserUtil.emptyElement(builder, javaDocElementTypeContainer.DOC_SNIPPET_ATTRIBUTE_LIST);
      parseSnippetTagBody(builder, javaDocElementTypeContainer);
    }
    else if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      parseSnippetAttributeList(builder, javaDocElementTypeContainer);
      if (builder.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_COLON) {
        parseSnippetTagBody(builder, javaDocElementTypeContainer);
      }
    }
    else {
      BasicJavaParserUtil.emptyElement(builder, javaDocElementTypeContainer.DOC_SNIPPET_ATTRIBUTE_LIST);
      IElementType current = getTokenType(builder);
      while (current != null && current != JavaDocTokenType.DOC_INLINE_TAG_END) {
        builder.advanceLexer();
        current = getTokenType(builder);
      }
    }
    snippetValue.done(javaDocElementTypeContainer.DOC_SNIPPET_TAG_VALUE);
  }

  private static void parseSnippetTagBody(@NotNull PsiBuilder builder,
                                          @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    PsiBuilder.Marker body = builder.mark();
    body.setCustomEdgeTokenBinders(WhitespacesBinders.GREEDY_LEFT_BINDER, WhitespacesBinders.GREEDY_RIGHT_BINDER);
    assert getTokenType(builder) == JavaDocTokenType.DOC_TAG_VALUE_COLON;
    builder.advanceLexer();
    while (true) {
      IElementType tokenType = getTokenType(builder);
      if (tokenType == null || tokenType == JavaDocTokenType.DOC_INLINE_TAG_END) {
        break;
      }
      builder.advanceLexer();
    }
    body.done(javaDocElementTypeContainer.DOC_SNIPPET_BODY);
  }

  private static void parseSnippetAttributeList(@NotNull PsiBuilder builder,
                                                @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    PsiBuilder.Marker attributeList = builder.mark();
    outer:
    while (true) {
      IElementType type = getTokenType(builder);
      while (type != JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
        // recovery
        if (type != JavaDocTokenType.DOC_TAG_VALUE_QUOTE) {
          break outer;
        }
        builder.advanceLexer();
        type = getTokenType(builder);
      }
      parseSnippetAttribute(builder, javaDocElementTypeContainer);
    }
    attributeList.done(javaDocElementTypeContainer.DOC_SNIPPET_ATTRIBUTE_LIST);
  }

  private static void parseSnippetAttribute(@NotNull PsiBuilder builder,
                                            @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    PsiBuilder.Marker attribute = builder.mark();
    assert builder.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN;
    builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_ATTRIBUTE_NAME);
    builder.advanceLexer();
    getTokenType(builder); // skipping spaces
    if ("=".equals(builder.getTokenText())) {
      builder.advanceLexer();
      IElementType afterEqToken = getTokenType(builder);
      if (afterEqToken == JavaDocTokenType.DOC_TAG_VALUE_QUOTE) {
        PsiBuilder.Marker quotedValue = builder.mark();
        builder.advanceLexer();
        if (getTokenType(builder) == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
          builder.advanceLexer();
        }
        if (getTokenType(builder) == JavaDocTokenType.DOC_TAG_VALUE_QUOTE) {
          builder.advanceLexer();
        }
        quotedValue.collapse(javaDocElementTypeContainer.DOC_SNIPPET_ATTRIBUTE_VALUE);
      }
      else if (afterEqToken == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
        builder.remapCurrentToken(javaDocElementTypeContainer.DOC_SNIPPET_ATTRIBUTE_VALUE);
        builder.advanceLexer();
      }
    }
    attribute.done(javaDocElementTypeContainer.DOC_SNIPPET_ATTRIBUTE);
  }

  private static void parseSeeTagValue(@NotNull PsiBuilder builder, boolean allowBareFieldReference,
                                       @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    IElementType tokenType = getTokenType(builder);
    if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
      parseMethodRef(builder, builder.mark(), javaDocElementTypeContainer);
    }
    else if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      PsiBuilder.Marker refStart = builder.mark();
      builder.remapCurrentToken(javaDocElementTypeContainer.DOC_REFERENCE_HOLDER);
      builder.advanceLexer();

      if (getTokenType(builder) == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
        parseMethodRef(builder, refStart, javaDocElementTypeContainer);
      }
      else if (allowBareFieldReference) {
        refStart.rollbackTo();
        builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_VALUE_TOKEN);
        parseMethodRef(builder, builder.mark(), javaDocElementTypeContainer);
      }
      else {
        refStart.drop();
      }
    }
    else {
      PsiBuilder.Marker tagValue = builder.mark();
      builder.advanceLexer();
      tagValue.done(javaDocElementTypeContainer.DOC_TAG_VALUE_ELEMENT);
    }
  }

  private static void parseMethodRef(@NotNull PsiBuilder builder,
                                     @NotNull PsiBuilder.Marker refStart,
                                     @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    if (getTokenType(builder) == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
      builder.advanceLexer();
    }
    if (getTokenType(builder) != JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      refStart.done(javaDocElementTypeContainer.DOC_METHOD_OR_FIELD_REF);
      return;
    }
    builder.advanceLexer();

    if (getTokenType(builder) == JavaDocTokenType.DOC_TAG_VALUE_LPAREN) {
      builder.advanceLexer();

      PsiBuilder.Marker subValue = builder.mark();

      IElementType tokenType;
      while (TAG_VALUES_SET.contains(tokenType = getTokenType(builder))) {
        if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
          builder.remapCurrentToken(javaDocElementTypeContainer.DOC_TYPE_HOLDER);
          builder.advanceLexer();

          while (TAG_VALUES_SET.contains(tokenType = getTokenType(builder)) &&
                 tokenType != JavaDocTokenType.DOC_TAG_VALUE_COMMA && tokenType != JavaDocTokenType.DOC_TAG_VALUE_RPAREN) {
            builder.advanceLexer();
          }
        }
        else if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_RPAREN) {
          subValue.done(javaDocElementTypeContainer.DOC_TAG_VALUE_ELEMENT);
          builder.advanceLexer();
          refStart.done(javaDocElementTypeContainer.DOC_METHOD_OR_FIELD_REF);
          return;
        }
        else {
          builder.advanceLexer();
        }
      }

      subValue.done(javaDocElementTypeContainer.DOC_TAG_VALUE_ELEMENT);
    }

    refStart.done(javaDocElementTypeContainer.DOC_METHOD_OR_FIELD_REF);
  }

  private static void parseParameterRef(@NotNull PsiBuilder builder,
                                        @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    PsiBuilder.Marker tagValue = builder.mark();
    while (TAG_VALUES_SET.contains(getTokenType(builder))) builder.advanceLexer();
    tagValue.done(javaDocElementTypeContainer.DOC_PARAMETER_REF);
  }

  private static void parseSimpleTagValue(@NotNull PsiBuilder builder,
                                          @NotNull AbstractBasicJavaDocElementTypeFactory.JavaDocElementTypeContainer javaDocElementTypeContainer) {
    PsiBuilder.Marker tagData = builder.mark();
    while (true) {
      IElementType tokenType = getTokenType(builder);
      if (tokenType == JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER) {
        builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_VALUE_TOKEN);
      }
      else if (!TAG_VALUES_SET.contains(tokenType)) break;
      builder.advanceLexer();
    }
    tagData.done(javaDocElementTypeContainer.DOC_TAG_VALUE_ELEMENT);
  }

  @Nullable
  private static IElementType getTokenType(PsiBuilder builder) {
    IElementType tokenType;
    while ((tokenType = builder.getTokenType()) == JavaDocTokenType.DOC_SPACE) {
      builder.remapCurrentToken(TokenType.WHITE_SPACE);
      builder.advanceLexer();
    }
    return tokenType;
  }

  private static int getBraceScope(PsiBuilder builder) {
    Integer braceScope = builder.getUserData(BRACE_SCOPE_KEY);
    return braceScope != null ? braceScope : 0;
  }

  private static void setBraceScope(PsiBuilder builder, int braceScope) {
    builder.putUserData(BRACE_SCOPE_KEY, braceScope);
  }

  private static void remapAndAdvance(PsiBuilder builder) {
    if (INLINE_TAG_BORDERS_SET.contains(builder.getTokenType()) && getBraceScope(builder) != 1) {
      builder.remapCurrentToken(JavaDocTokenType.DOC_COMMENT_DATA);
    }
    builder.advanceLexer();
  }
}