// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaDocTokenType;
import org.jetbrains.annotations.NotNull;

public final class JavaWhiteSpaceFormattingStrategy extends StaticSymbolWhiteSpaceDefinitionStrategy {
  public JavaWhiteSpaceFormattingStrategy() {
    // Java also recognizes '\f' AKA form-feed as a white space character.
    super('\u000c');
  }

  // Handles Javadoc
  @Override
  public boolean containsWhitespacesOnly(@NotNull final ASTNode node) {
    return (node.getElementType() == JavaDocTokenType.DOC_COMMENT_DATA ||
            node.getElementType() == JavaDocTokenType.DOC_TAG_VALUE_TOKEN) && node.getText().trim().isEmpty();
  }
}
