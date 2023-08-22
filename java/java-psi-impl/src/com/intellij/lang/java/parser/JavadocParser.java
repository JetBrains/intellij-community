// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.parser;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.WhitespacesBinders;
import com.intellij.openapi.util.Key;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.TokenType;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class JavadocParser {
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

  private JavadocParser() { }

  public static void parseJavadocReference(@NotNull PsiBuilder builder) {
    JavaParser.INSTANCE.getReferenceParser().parseJavaCodeReference(builder, true, true, false, false);
    swallowTokens(builder);
  }

  public static void parseJavadocType(@NotNull PsiBuilder builder) {
    JavaParser.INSTANCE.getReferenceParser().parseType(builder, ReferenceParser.EAT_LAST_DOT | ReferenceParser.ELLIPSIS | ReferenceParser.WILDCARD);
    swallowTokens(builder);
  }

  private static void swallowTokens(PsiBuilder builder) {
    while (!builder.eof()) builder.advanceLexer();
  }

  public static void parseDocCommentText(@NotNull PsiBuilder builder) {
    builder.enforceCommentTokens(SKIP_TOKENS);

    while (!builder.eof()) {
      IElementType tokenType = getTokenType(builder);
      if (tokenType == JavaDocTokenType.DOC_TAG_NAME) {
        parseTag(builder);
      }
      else {
        parseDataItem(builder, null, false);
      }
    }
  }

  private static void parseTag(PsiBuilder builder) {
    String tagName = builder.getTokenText();
    PsiBuilder.Marker tag = builder.mark();
    builder.advanceLexer();
    while (true) {
      IElementType tokenType = getTokenType(builder);
      if (tokenType == null || tokenType == JavaDocTokenType.DOC_TAG_NAME || tokenType == JavaDocTokenType.DOC_COMMENT_END) break;
      parseDataItem(builder, tagName, false);
    }
    tag.done(JavaDocElementType.DOC_TAG);
  }

  private static void parseDataItem(PsiBuilder builder, @Nullable String tagName, boolean isInline) {
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

        parseDataItem(builder, inlineTagName, true);
        if (tokenType == JavaDocTokenType.DOC_INLINE_TAG_END) {
          braceScope = getBraceScope(builder);
          if (braceScope > 0) setBraceScope(builder, --braceScope);
          if (braceScope == 0) break;
        }
      }

      if (SNIPPET_TAG.equals(inlineTagName)) {
        tag.done(JavaDocElementType.DOC_SNIPPET_TAG);
      } else {
        tag.done(JavaDocElementType.DOC_INLINE_TAG);
      }
    }
    else if (TAG_VALUES_SET.contains(tokenType)) {
      if (SEE_TAG.equals(tagName) && !isInline ||
          LINK_TAG.equals(tagName) && isInline ||
          JavaParserUtil.getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_1_4) && LINK_PLAIN_TAG.equals(tagName) && isInline) {
        parseSeeTagValue(builder, false);
      }
      else if (!isInline && tagName != null && REFERENCE_TAGS.contains(tagName)) {
        PsiBuilder.Marker tagValue = builder.mark();
        builder.remapCurrentToken(JavaDocElementType.DOC_REFERENCE_HOLDER);
        builder.advanceLexer();
        tagValue.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
      }
      else if (!isInline && PARAM_TAG.equals(tagName)) {
        parseParameterRef(builder);
      }
      else if (JavaParserUtil.getLanguageLevel(builder).isAtLeast(LanguageLevel.JDK_1_5) && VALUE_TAG.equals(tagName) && isInline) {
        parseSeeTagValue(builder, true);
      }
      else if (SNIPPET_TAG.equals(tagName) && isInline) {
        parseSnippetTagValue(builder);
      }
      else {
        parseSimpleTagValue(builder);
      }
    }
    else {
      remapAndAdvance(builder);
    }
  }

  private static void parseSnippetTagValue(PsiBuilder builder) {
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
      JavaParserUtil.emptyElement(builder, JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_LIST);
      parseSnippetTagBody(builder);
    } else if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      parseSnippetAttributeList(builder);
      if (builder.getTokenType() == JavaDocTokenType.DOC_TAG_VALUE_COLON) {
        parseSnippetTagBody(builder);
      }
    } else {
      JavaParserUtil.emptyElement(builder, JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_LIST);
      IElementType current = getTokenType(builder);
      while (current != null && current != JavaDocTokenType.DOC_INLINE_TAG_END) {
        builder.advanceLexer();
        current = getTokenType(builder);
      }
    }
    snippetValue.done(JavaDocElementType.DOC_SNIPPET_TAG_VALUE);
  }

  private static void parseSnippetTagBody(PsiBuilder builder) {
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
    body.done(JavaDocElementType.DOC_SNIPPET_BODY);
  }

  private static void parseSnippetAttributeList(PsiBuilder builder) {
    PsiBuilder.Marker attributeList = builder.mark();
    outer:
    while (true) {
      IElementType type = getTokenType(builder);
      while (type != JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
        // recovery
        if (type !=  JavaDocTokenType.DOC_TAG_VALUE_QUOTE) {
          break outer;
        }
        builder.advanceLexer();
        type = getTokenType(builder);
      }
      parseSnippetAttribute(builder);
    }
    attributeList.done(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_LIST);
  }

  private static void parseSnippetAttribute(PsiBuilder builder) {
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
        if(getTokenType(builder) == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
          builder.advanceLexer();
        }
        if(getTokenType(builder) == JavaDocTokenType.DOC_TAG_VALUE_QUOTE) {
          builder.advanceLexer();
        }
        quotedValue.collapse(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_VALUE);
      } else if (afterEqToken == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
        builder.remapCurrentToken(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_VALUE);
        builder.advanceLexer();
      }
    }
    attribute.done(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE);
  }

  private static void parseSeeTagValue(PsiBuilder builder, boolean allowBareFieldReference) {
    IElementType tokenType = getTokenType(builder);
    if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
      parseMethodRef(builder, builder.mark());
    }
    else if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      PsiBuilder.Marker refStart = builder.mark();
      builder.remapCurrentToken(JavaDocElementType.DOC_REFERENCE_HOLDER);
      builder.advanceLexer();

      if (getTokenType(builder) == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
        parseMethodRef(builder, refStart);
      }
      else if (allowBareFieldReference) {
        refStart.rollbackTo();
        builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_VALUE_TOKEN);
        parseMethodRef(builder, builder.mark());
      }
      else {
        refStart.drop();
      }
    }
    else {
      PsiBuilder.Marker tagValue = builder.mark();
      builder.advanceLexer();
      tagValue.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
    }
  }

  private static void parseMethodRef(PsiBuilder builder, PsiBuilder.Marker refStart) {
    if (getTokenType(builder) == JavaDocTokenType.DOC_TAG_VALUE_SHARP_TOKEN) {
      builder.advanceLexer();
    }
    if (getTokenType(builder) != JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
      refStart.done(JavaDocElementType.DOC_METHOD_OR_FIELD_REF);
      return;
    }
    builder.advanceLexer();

    if (getTokenType(builder) == JavaDocTokenType.DOC_TAG_VALUE_LPAREN) {
      builder.advanceLexer();

      PsiBuilder.Marker subValue = builder.mark();

      IElementType tokenType;
      while (TAG_VALUES_SET.contains(tokenType = getTokenType(builder))) {
        if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) {
          builder.remapCurrentToken(JavaDocElementType.DOC_TYPE_HOLDER);
          builder.advanceLexer();

          while (TAG_VALUES_SET.contains(tokenType = getTokenType(builder)) &&
                 tokenType != JavaDocTokenType.DOC_TAG_VALUE_COMMA && tokenType != JavaDocTokenType.DOC_TAG_VALUE_RPAREN) {
            builder.advanceLexer();
          }
        }
        else if (tokenType == JavaDocTokenType.DOC_TAG_VALUE_RPAREN) {
          subValue.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
          builder.advanceLexer();
          refStart.done(JavaDocElementType.DOC_METHOD_OR_FIELD_REF);
          return;
        }
        else {
          builder.advanceLexer();
        }
      }

      subValue.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
    }

    refStart.done(JavaDocElementType.DOC_METHOD_OR_FIELD_REF);
  }

  private static void parseParameterRef(PsiBuilder builder) {
    PsiBuilder.Marker tagValue = builder.mark();
    while (TAG_VALUES_SET.contains(getTokenType(builder))) builder.advanceLexer();
    tagValue.done(JavaDocElementType.DOC_PARAMETER_REF);
  }

  private static void parseSimpleTagValue(PsiBuilder builder) {
    PsiBuilder.Marker tagData = builder.mark();
    while (true) {
      IElementType tokenType = getTokenType(builder);
      if (tokenType == JavaDocTokenType.DOC_COMMENT_BAD_CHARACTER) builder.remapCurrentToken(JavaDocTokenType.DOC_TAG_VALUE_TOKEN);
      else if (!TAG_VALUES_SET.contains(tokenType)) break;
      builder.advanceLexer();
    }
    tagData.done(JavaDocElementType.DOC_TAG_VALUE_ELEMENT);
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