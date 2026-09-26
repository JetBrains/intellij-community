// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.highlighter;

import com.intellij.lexer.JavaHighlightingLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.HashMap;
import java.util.Map;

public class JavaFileHighlighter extends AbstractBasicJavaFileHighlighter {
  private static class Holder {
    private final static @Unmodifiable @NotNull Map<@NotNull IElementType, @NotNull TextAttributesKey @NotNull []> ourMap;
    static {
      Map<IElementType, TextAttributesKey> ourMap1 = new HashMap<>();
      Map<IElementType, TextAttributesKey> ourMap2 = new HashMap<>();

      fillStandardMaps(ourMap1, ourMap2);
      initAdditional(ourMap1, ourMap2);
      ourMap = merge(ourMap1, ourMap2);
    }
  }

  public JavaFileHighlighter() {
    this(LanguageLevel.HIGHEST);
  }

  public JavaFileHighlighter(@NotNull LanguageLevel languageLevel) {
    super(languageLevel);
  }

  @Override
  public @NotNull Lexer getHighlightingLexer() {
    return new JavaHighlightingLexer(myLanguageLevel);
  }

  @Override
  public @NotNull TextAttributesKey @NotNull [] getTokenHighlights(@NotNull IElementType tokenType) {
    while (tokenType instanceof ParentProviderElementType parentProviderElementType) {
      if (parentProviderElementType.getParents().size() == 1) {
        tokenType = parentProviderElementType.getParents().iterator().next();
      }
    }
    return Holder.ourMap.getOrDefault(tokenType, TextAttributesKey.EMPTY_ARRAY);
  }

  private static void initAdditional(@NotNull Map<? super @NotNull IElementType, ? super @NotNull TextAttributesKey> map1,
                                     @NotNull Map<? super @NotNull IElementType, ? super @NotNull TextAttributesKey> map2) {
    map1.put(XmlTokenType.XML_DATA_CHARACTERS, JavaHighlightingColors.DOC_COMMENT);
    map1.put(XmlTokenType.XML_REAL_WHITE_SPACE, JavaHighlightingColors.DOC_COMMENT);

    IElementType[] javaDocMarkup = {
      XmlTokenType.XML_START_TAG_START, XmlTokenType.XML_END_TAG_START, XmlTokenType.XML_TAG_END, XmlTokenType.XML_EMPTY_ELEMENT_END,
      XmlTokenType.TAG_WHITE_SPACE, XmlTokenType.XML_TAG_NAME, XmlTokenType.XML_NAME, XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
      XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER, XmlTokenType.XML_ATTRIBUTE_VALUE_END_DELIMITER, XmlTokenType.XML_CHAR_ENTITY_REF,
      XmlTokenType.XML_ENTITY_REF_TOKEN, XmlTokenType.XML_EQ
    };
    for (IElementType idx : javaDocMarkup) {
      map1.put(idx, JavaHighlightingColors.DOC_COMMENT);
      map2.put(idx, JavaHighlightingColors.DOC_COMMENT_MARKUP);
    }
  }
}