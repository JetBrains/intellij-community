// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.json5.Json5ParserDefinitionKt;
import com.intellij.platform.syntax.SyntaxElementType;
import com.intellij.json.json5.Json5ParserDefinition;
import com.intellij.platform.syntax.psi.ElementTypeConverter;
import com.intellij.platform.syntax.psi.ElementTypeConverterFactory;
import com.intellij.platform.syntax.psi.ElementTypeConverterKt;
import com.intellij.psi.tree.IElementType;
import com.intellij.json.jsonLines.JsonLinesParserDefinition;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;

public class JsonFileTypeConverterFactory implements ElementTypeConverterFactory {

  @Override
  public @NotNull ElementTypeConverter getElementTypeConverter() {
    return ElementTypeConverterKt.elementTypeConverterOf(
      new Pair<SyntaxElementType, IElementType>(JsonParserDefinitionKt.SYNTAX_FILE, JsonParserDefinitionKt.FILE),
      new Pair<SyntaxElementType, IElementType>(Json5ParserDefinitionKt.SYNTAX_FILE, Json5ParserDefinitionKt.FILE),
      new Pair<SyntaxElementType, IElementType>(JsonLinesParserDefinition.JSON_LINES_FILE, JsonLinesParserDefinition.FILE)
    );
  }
}
