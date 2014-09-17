/*
 * @author max
 */
package com.intellij.json;

import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class JsonNamesValidator implements NamesValidator {

  private final JsonLexer myLexer = new JsonLexer();

  @Override
  public synchronized boolean isKeyword(@NotNull String name, Project project) {
    myLexer.start(name);
    return JsonParserDefinition.JSON_KEYWORDS.contains( myLexer.getTokenType() ) && myLexer.getTokenEnd() == name.length();
  }
  @Override
  public synchronized boolean isIdentifier(@NotNull String name, final Project project) {
    if (!StringUtil.startsWithChar(name,'\'') && !StringUtil.startsWithChar(name,'\"')) {
      name = "\"" + name;
    }

    if (!StringUtil.endsWithChar(name,'"') && !StringUtil.endsWithChar(name,'\"')) {
      name += "\"";
    }

    myLexer.start(name);
    IElementType type = myLexer.getTokenType();

    return myLexer.getTokenEnd() == name.length() && (type == JsonElementTypes.DOUBLE_QUOTED_STRING ||
                                                      type == JsonElementTypes.SINGLE_QUOTED_STRING);
  }

}
