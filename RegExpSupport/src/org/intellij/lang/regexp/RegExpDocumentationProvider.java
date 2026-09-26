// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.lang.regexp;

import com.intellij.lang.ASTNode;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpProperty;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public final class RegExpDocumentationProvider extends AbstractDocumentationProvider {
  @Override
  public @Nullable @Nls String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (element instanceof RegExpProperty prop) {
      final ASTNode node = prop.getCategoryNode();
      if (node != null) {
        final String description = RegExpLanguageHosts.getInstance().getPropertyDescription(node.getPsi(), node.getText());
        if (description != null) {
          if (prop.isNegated()) {
            return RegExpBundle.message("doc.property.block.stands.for.characters.not.matching.0", description);
          } else {
            return RegExpBundle.message("doc.property.block.stands.for.0", description);
          }
        }
      }
    }
    return null;
  }

  @Override
  public @Nullable @Nls String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
    if (element instanceof RegExpGroup group) {
      return StringUtil.escapeXmlEntities(group.getUnescapedText());
    } else {
      return null;
    }
  }
}
