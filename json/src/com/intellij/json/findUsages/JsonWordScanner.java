package com.intellij.json.findUsages;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonLexer;
import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.psi.tree.TokenSet;

import static com.intellij.json.JsonTokenSets.JSON_COMMENTARIES;
import static com.intellij.json.JsonTokenSets.JSON_LITERALS;

/**
 * @author Mikhail Golubev
 */
public class JsonWordScanner extends DefaultWordsScanner {
  public JsonWordScanner() {
    super(new JsonLexer(), TokenSet.create(JsonElementTypes.IDENTIFIER), JSON_COMMENTARIES, JSON_LITERALS);
    setMayHaveFileRefsInLiterals(true);
  }
}
