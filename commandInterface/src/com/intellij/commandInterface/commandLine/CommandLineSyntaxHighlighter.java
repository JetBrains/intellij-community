// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.commandLine;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.psi.tree.IElementType;
import com.intellij.commandInterface.commandLine.CommandLineElementTypes;
import com.intellij.commandInterface.commandLine._CommandLineLexer;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class CommandLineSyntaxHighlighter implements SyntaxHighlighter {
  private static final Map<IElementType, TextAttributesKey> ATTRIBUTES = new HashMap<>();

  static {
    ATTRIBUTES.put(CommandLineElementTypes.LITERAL_STARTS_FROM_LETTER,
                   TextAttributesKey.createTextAttributesKey("GNU.LETTER", DefaultLanguageHighlighterColors.LOCAL_VARIABLE)
    );
    ATTRIBUTES.put(CommandLineElementTypes.LITERAL_STARTS_FROM_DIGIT,
                   TextAttributesKey.createTextAttributesKey("GNU.NUMBER", DefaultLanguageHighlighterColors.NUMBER)
    );
    ATTRIBUTES.put(CommandLineElementTypes.SHORT_OPTION_NAME_TOKEN,
                   TextAttributesKey.createTextAttributesKey("GNU.SHORT_OPTION", DefaultLanguageHighlighterColors.INSTANCE_METHOD)

    );
    ATTRIBUTES.put(CommandLineElementTypes.LONG_OPTION_NAME_TOKEN,
                   TextAttributesKey.createTextAttributesKey("GNU.LONG_OPTION", DefaultLanguageHighlighterColors.INSTANCE_METHOD)
    );
  }

  @NotNull
  @Override
  public Lexer getHighlightingLexer() {
    return new FlexAdapter(new _CommandLineLexer());
  }

  @Override
  public TextAttributesKey @NotNull [] getTokenHighlights(final IElementType tokenType) {
    final TextAttributesKey attributesKey = ATTRIBUTES.get(tokenType);
    return attributesKey == null ? TextAttributesKey.EMPTY_ARRAY : new TextAttributesKey[]{attributesKey};
  }
}
