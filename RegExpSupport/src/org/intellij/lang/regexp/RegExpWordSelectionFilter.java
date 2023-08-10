// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.regexp;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.jetbrains.annotations.NotNull;


public class RegExpWordSelectionFilter implements Condition<PsiElement> {
  @Override
  public boolean value(@NotNull PsiElement element) {
    final ASTNode node = element.getNode();
    if ((node != null && node.getElementType() == RegExpTT.CHARACTER) ||
        (element instanceof RegExpChar && ((RegExpChar)element).getType() == RegExpChar.Type.CHAR)) {
      return false;
    }
    return true;
  }
}
