/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.lang.regexp.psi.RegExpElement;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class RegExpElementImpl extends ASTWrapperPsiElement implements RegExpElement {
    public RegExpElementImpl(ASTNode node) {
        super(node);
    }

    @Override
    @NotNull
    public Language getLanguage() {
        return RegExpLanguage.INSTANCE;
    }

  @Override
  public String toString() {
        return getClass().getSimpleName() + ": <" + getText() + ">";
    }

    @Override
    public void accept(@NotNull PsiElementVisitor visitor) {
        if (visitor instanceof RegExpElementVisitor) {
            accept((RegExpElementVisitor)visitor);
        } else {
            super.accept(visitor);
        }
    }

    public abstract void accept(RegExpElementVisitor visitor);

    @Override
    public PsiElement replace(@NotNull PsiElement psiElement) throws IncorrectOperationException {
        final ASTNode node = psiElement.getNode();
        assert node != null;
        getNode().getTreeParent().replaceChild(getNode(), node);
        return psiElement;
    }

    @Override
    public void delete() throws IncorrectOperationException {
        getNode().getTreeParent().removeChild(getNode());
    }

    @NotNull
    @Override
    public final String getUnescapedText() {
        return InjectedLanguageManager.getInstance(getProject()).getUnescapedText(this);
    }

  public static boolean isLiteralExpression(@Nullable PsiElement context) {
    if (context == null) return false;
    ASTNode astNode = context.getNode();
    if (astNode == null) {
      return false;
    }
    ASTNode child = null;
    if (astNode instanceof CompositeElement) { // in some languages token nodes are wrapped within a single-child composite
      ASTNode[] children = astNode.getChildren(null);
      if (children.length == 1) {
        child = children[0];
      }
    }
    final ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(context.getLanguage());
    final TokenSet literalElements = parserDefinition.getStringLiteralElements();
    return literalElements.contains(astNode.getElementType()) || child != null && literalElements.contains(child.getElementType());
  }
}
