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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.regexp.RegExpLanguage;
import org.intellij.lang.regexp.psi.RegExpElement;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.jetbrains.annotations.NotNull;

public abstract class RegExpElementImpl extends ASTWrapperPsiElement implements RegExpElement {
    public RegExpElementImpl(ASTNode node) {
        super(node);
    }

    @NotNull
    public Language getLanguage() {
        return RegExpLanguage.INSTANCE;
    }

    @NotNull
    @SuppressWarnings({ "ConstantConditions", "EmptyMethod" })
    public ASTNode getNode() {
        return super.getNode();
    }

    public String toString() {
        return getClass().getSimpleName() + ": <" + getUnescapedText() + ">";
    }

    public void accept(@NotNull PsiElementVisitor visitor) {
        if (visitor instanceof RegExpElementVisitor) {
            accept((RegExpElementVisitor)visitor);
        } else {
            super.accept(visitor);
        }
    }

    public void accept(RegExpElementVisitor visitor) {
        visitor.visitRegExpElement(this);
    }

    public PsiElement replace(@NotNull PsiElement psiElement) throws IncorrectOperationException {
        final ASTNode node = psiElement.getNode();
        assert node != null;
        getNode().getTreeParent().replaceChild(getNode(), node);
        return psiElement;
    }

    public void delete() throws IncorrectOperationException {
        getNode().getTreeParent().removeChild(getNode());
    }

    public final String getUnescapedText() {
        if (InjectedLanguageUtil.isInInjectedLanguagePrefixSuffix(this)) {
            // do not attempt to decode text if PsiElement is part of prefix/suffix
            return getText();
        }
        if (isInsideStringLiteral()) {
            return StringUtil.unescapeStringCharacters(getNode().getText());
        } else {
            return getNode().getText();
        }
    }

    protected final boolean isInsideStringLiteral() {
        return getContainingFile().getContext() instanceof PsiLiteralExpression;
    }
}
