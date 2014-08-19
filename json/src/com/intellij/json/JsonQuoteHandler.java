package com.intellij.json;

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler;

/**
 * @author Mikhail Golubev
 */
public class JsonQuoteHandler extends SimpleTokenSetQuoteHandler {
  public JsonQuoteHandler() {
    super(JsonParserDefinition.STRING_LITERALS);
  }
}
