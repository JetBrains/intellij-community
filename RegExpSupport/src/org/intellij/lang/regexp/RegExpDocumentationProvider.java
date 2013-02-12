/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.psi.PsiElement;
import org.intellij.lang.regexp.psi.RegExpElement;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpProperty;
import org.jetbrains.annotations.Nullable;

/**
 * @author vnikolaenko
 */
public final class RegExpDocumentationProvider extends AbstractDocumentationProvider {
  @Override
  @Nullable
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (element instanceof RegExpProperty) {
      final RegExpProperty prop = (RegExpProperty)element;
      final ASTNode node = prop.getCategoryNode();
      if (node != null) {
        final String description = RegExpLanguageHosts.getInstance().getPropertyDescription(node.getPsi(), node.getText());
        if (description != null) {
          if (prop.isNegated()) {
            return "Property block stands for characters not matching " + description;
          } else {
            return "Property block stands for " + description;
          }
        }
      }
    }
    return null;
  }

  @Nullable
  public String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof RegExpGroup) {
      return "Capturing Group: " + ((RegExpElement)element).getUnescapedText();
    } else {
      return null;
    }
  }
}
