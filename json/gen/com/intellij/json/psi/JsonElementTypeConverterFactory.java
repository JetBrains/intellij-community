// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.intellij.json.psi;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.syntax.JsonSyntaxElementTypes;
import com.intellij.psi.tree.IElementType;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.platform.syntax.psi.ElementTypeConverterFactory;
import com.intellij.platform.syntax.psi.ElementTypeConverter;
import com.intellij.platform.syntax.psi.ElementTypeConverterKt;
import org.jetbrains.annotations.NotNull;
import kotlin.Pair;

public class JsonElementTypeConverterFactory implements ElementTypeConverterFactory {

  @Override
  public @NotNull ElementTypeConverter getElementTypeConverter() {
    return ElementTypeConverterKt.elementTypeConverterOf(
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.ARRAY, JsonElementTypes.ARRAY),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.BOOLEAN_LITERAL, JsonElementTypes.BOOLEAN_LITERAL),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.LITERAL, JsonElementTypes.LITERAL),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.NULL_LITERAL, JsonElementTypes.NULL_LITERAL),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.NUMBER_LITERAL, JsonElementTypes.NUMBER_LITERAL),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.OBJECT, JsonElementTypes.OBJECT),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.PROPERTY, JsonElementTypes.PROPERTY),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.REFERENCE_EXPRESSION, JsonElementTypes.REFERENCE_EXPRESSION),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.STRING_LITERAL, JsonElementTypes.STRING_LITERAL),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.VALUE, JsonElementTypes.VALUE),

      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.L_CURLY, JsonElementTypes.L_CURLY),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.R_CURLY, JsonElementTypes.R_CURLY),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.L_BRACKET, JsonElementTypes.L_BRACKET),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.R_BRACKET, JsonElementTypes.R_BRACKET),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.COMMA, JsonElementTypes.COMMA),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.COLON, JsonElementTypes.COLON),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.LINE_COMMENT, JsonElementTypes.LINE_COMMENT),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.BLOCK_COMMENT, JsonElementTypes.BLOCK_COMMENT),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.DOUBLE_QUOTED_STRING, JsonElementTypes.DOUBLE_QUOTED_STRING),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.SINGLE_QUOTED_STRING, JsonElementTypes.SINGLE_QUOTED_STRING),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.NUMBER, JsonElementTypes.NUMBER),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.TRUE, JsonElementTypes.TRUE),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.FALSE, JsonElementTypes.FALSE),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.NULL, JsonElementTypes.NULL),
      new Pair<SyntaxElementType, IElementType>(JsonSyntaxElementTypes.IDENTIFIER, JsonElementTypes.IDENTIFIER)
    );
  }
}
