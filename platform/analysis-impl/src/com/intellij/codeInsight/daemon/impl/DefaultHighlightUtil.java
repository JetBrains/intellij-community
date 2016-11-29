/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.TokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultHighlightUtil {
  @Nullable
  public static HighlightInfo checkBadCharacter(@NotNull PsiElement element) {
    ASTNode node = element.getNode();
    if (node != null && node.getElementType() == TokenType.BAD_CHARACTER) {
      char c = element.textToCharArray()[0];
      boolean printable = StringUtil.isPrintableUnicode(c) && !Character.isSpaceChar(c);
      String hex = String.format("U+%04X", (int)c);
      String text = "Illegal character: " + (printable ? c + " (" + hex + ")" : hex);
      return HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR).range(element).descriptionAndTooltip(text).create();
    }

    return null;
  }
}