/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.intellij.lang.regexp;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class RegExpWordSelectionFilter implements Condition<PsiElement> {
  @Override
  public boolean value(@NotNull PsiElement element) {
    final ASTNode node = element.getNode();
    if ((node != null && node.getElementType() == RegExpTT.CHARACTER) || element instanceof RegExpChar) {
      return false;
    }
    return true;
  }
}
