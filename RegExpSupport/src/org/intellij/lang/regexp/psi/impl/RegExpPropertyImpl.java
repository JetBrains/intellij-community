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

import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.intellij.lang.regexp.RegExpLanguageHosts;
import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RegExpPropertyImpl extends RegExpElementImpl implements RegExpProperty {
    public RegExpPropertyImpl(ASTNode astNode) {
        super(astNode);
    }

    public PsiReference getReference() {
        final ASTNode lbrace = getNode().findChildByType(RegExpTT.LBRACE);
        if (lbrace == null) return null;
        return new MyPsiReference();
    }

    public boolean isNegated() {
        final ASTNode node1 = getNode().findChildByType(RegExpTT.PROPERTY);
        final ASTNode node2 = getNode().findChildByType(RegExpTT.CARET);
        return (node1 != null && node1.textContains('P')) ^ (node2 != null);
    }

    @Nullable
    public ASTNode getCategoryNode() {
        return getNode().findChildByType(RegExpTT.NAME);
    }

    public void accept(RegExpElementVisitor visitor) {
        visitor.visitRegExpProperty(this);
    }

  private class MyPsiReference implements PsiReference {
        public PsiElement getElement() {
            return RegExpPropertyImpl.this;
        }

        public TextRange getRangeInElement() {
            ASTNode firstNode = getNode().findChildByType(RegExpTT.CARET);
            if (firstNode == null) {
              firstNode = getNode().findChildByType(RegExpTT.LBRACE);
            }
            assert firstNode != null;
            final ASTNode rbrace = getNode().findChildByType(RegExpTT.RBRACE);
            int to = rbrace == null ? getTextRange().getEndOffset() : rbrace.getTextRange().getEndOffset() - 1;

            final TextRange t = new TextRange(firstNode.getStartOffset() + 1, to);
            return t.shiftRight(-getTextRange().getStartOffset());
        }

        @Nullable
        public PsiElement resolve() {
            return RegExpPropertyImpl.this;
        }

        @NotNull
        public String getCanonicalText() {
            return getRangeInElement().substring(getElement().getText());
        }

        public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
            throw new IncorrectOperationException();
        }

        public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
            throw new IncorrectOperationException();
        }

        public boolean isReferenceTo(PsiElement element) {
            return false;
        }

    @NotNull
    public Object[] getVariants() {
      final ASTNode categoryNode = getCategoryNode();
      if (categoryNode != null && categoryNode.getText().startsWith("In") && !categoryNode.getText().startsWith("Intelli")) {
        return UNICODE_BLOCKS;
      }
      else {
        boolean startsWithIs = categoryNode != null && categoryNode.getText().startsWith("Is");
        Collection<LookupElement> result = ContainerUtil.newArrayList();
        for (String[] properties : RegExpLanguageHosts.getInstance().getAllKnownProperties(getElement())) {
          String name = ArrayUtil.getFirstElement(properties);
          if (name != null) {
            String typeText = properties.length > 1 ? properties[1] : ("Character.is" + name.substring("java".length()) + "()");
            result.add(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(name)
                                                               .withPresentableText(startsWithIs ? "Is" + name : name)
                                                               .withIcon(PlatformIcons.PROPERTY_ICON)
                                                               .withTypeText(typeText), getPriority(name)));
          }
        }
        return ArrayUtil.toObjectArray(result);
      }
    }

    private int getPriority(@NotNull String propertyName) {
      if (propertyName.equals("all")) return 3;
      if (propertyName.startsWith("java")) return 1;
      if (propertyName.length() > 2) return 2;
      return 0;
    }

    public boolean isSoft() {
      return true;
    }
  }

    private static final String[] UNICODE_BLOCKS;
    static {
        final Field[] fields = Character.UnicodeBlock.class.getFields();
        final List<String> unicodeBlocks = new ArrayList<>(fields.length);
        for (Field field : fields) {
            if (field.getType().equals(Character.UnicodeBlock.class)) {
                if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())) {
                    unicodeBlocks.add("In" + field.getName());
                }
            }
        }
      UNICODE_BLOCKS = ArrayUtil.toStringArray(unicodeBlocks);
    }
}
