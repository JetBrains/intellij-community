// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IReparseableLeafElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class WhiteSpaceTokenType extends IElementType implements IReparseableLeafElementType<ASTNode> {
  WhiteSpaceTokenType() {
    super("WHITE_SPACE", Language.ANY);
  }

  @Nullable
  @Override
  public ASTNode reparseLeaf(@NotNull ASTNode leaf, @NotNull CharSequence newText) {
    Language contextLanguage = leaf.getPsi().getLanguage();
    if (contextLanguage == Language.ANY) {
      return null;
    }

    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(contextLanguage);
    if (parserDefinition == null) {
      return null;
    }

    for (int i = 0; i < newText.length(); i++) {
      if (!Character.isWhitespace(newText.charAt(i))) {
        return null;
      }
    }

    return parserDefinition.reparseSpace(leaf, newText);
  }
}
