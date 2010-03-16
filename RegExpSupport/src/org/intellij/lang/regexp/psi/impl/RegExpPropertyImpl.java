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

import com.intellij.codeInsight.lookup.LookupValueFactory;
import com.intellij.codeInsight.lookup.LookupValueWithPriority;
import com.intellij.codeInsight.lookup.LookupValueWithUIHint;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Icons;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.regexp.RegExpTT;
import org.intellij.lang.regexp.psi.RegExpElementVisitor;
import org.intellij.lang.regexp.psi.RegExpProperty;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
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
        final ASTNode node = getNode().findChildByType(RegExpTT.PROPERTY);
        return node != null && node.textContains('P');
    }

    @Nullable
    public ASTNode getCategoryNode() {
        return getNode().findChildByType(RegExpTT.NAME);
    }

    public void accept(RegExpElementVisitor visitor) {
        visitor.visitRegExpProperty(this);
    }

    public static boolean isValidCategory(String category) {
        if (category.startsWith("In")) {
            try {
                return Character.UnicodeBlock.forName(category.substring(2)) != null;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        if (category.startsWith("Is")) {
            category = category.substring(2);
        }
        for (String[] name : PROPERTY_NAMES) {
            if (name[0].equals(category)) {
                return true;
            }
        }
        return false;
    }

    private class MyPsiReference implements PsiReference {
        public PsiElement getElement() {
            return RegExpPropertyImpl.this;
        }

        public TextRange getRangeInElement() {
            final ASTNode lbrace = getNode().findChildByType(RegExpTT.LBRACE);
            assert lbrace != null;
            final ASTNode rbrace = getNode().findChildByType(RegExpTT.RBRACE);
            int to = rbrace == null ? getTextRange().getEndOffset() : rbrace.getTextRange().getEndOffset() - 1;

            final TextRange t = new TextRange(lbrace.getStartOffset() + 1, to);
            return t.shiftRight(-getTextRange().getStartOffset());
        }

        @Nullable
        public PsiElement resolve() {
            return RegExpPropertyImpl.this;
        }

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
            } else {
                final Object[] objects = new Object[PROPERTY_NAMES.length];
                for (int i = 0; i < objects.length; i++) {
                    final String[] prop = PROPERTY_NAMES[i];
                    objects[i] = new MyLookupValue(prop);

                }
                return objects;
            }
        }

        public boolean isSoft() {
            return true;
        }

        private class MyLookupValue extends LookupValueFactory.LookupValueWithIcon implements LookupValueWithPriority, LookupValueWithUIHint {
            private final String[] myProp;

            public MyLookupValue(String[] prop) {
                super(prop[0], Icons.PROPERTY_ICON);
                myProp = prop;
            }

            public String getPresentation() {
                final ASTNode categoryNode = getCategoryNode();
                if (categoryNode != null) {
                    if (categoryNode.getText().startsWith("Is")) {
                        return "Is" + super.getPresentation();
                    }
                }
                return super.getPresentation();
            }

            public int getPriority() {
                final String name = myProp[0];
                if (name.equals("all")) return HIGH + 1;
                if (name.startsWith("java")) return HIGHER;
                return name.length() > 2 ? HIGH : NORMAL;
            }

            public String getTypeHint() {
                return myProp.length > 1 ? myProp[1] : ("Character.is" + myProp[0].substring("java".length()) + "()");
            }

            @Nullable
            public Color getColorHint() {
                return null;
            }

            public boolean isBold() {
                return false;
            }
        }
    }


    private static final String[] UNICODE_BLOCKS;
    static {
        final Field[] fields = Character.UnicodeBlock.class.getFields();
        final List<String> unicodeBlocks = new ArrayList<String>(fields.length);
        for (Field field : fields) {
            if (field.getType().equals(Character.UnicodeBlock.class)) {
                if (Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())) {
                    unicodeBlocks.add("In" + field.getName());
                }
            }
        }
      UNICODE_BLOCKS = ArrayUtil.toStringArray(unicodeBlocks);
    }
    public static final String[][] PROPERTY_NAMES = {
            { "Cn", "UNASSIGNED" },
            { "Lu", "UPPERCASE_LETTER" },
            { "Ll", "LOWERCASE_LETTER" },
            { "Lt", "TITLECASE_LETTER" },
            { "Lm", "MODIFIER_LETTER" },
            { "Lo", "OTHER_LETTER" },
            { "Mn", "NON_SPACING_MARK" },
            { "Me", "ENCLOSING_MARK" },
            { "Mc", "COMBINING_SPACING_MARK" },
            { "Nd", "DECIMAL_DIGIT_NUMBER" },
            { "Nl", "LETTER_NUMBER" },
            { "No", "OTHER_NUMBER" },
            { "Zs", "SPACE_SEPARATOR" },
            { "Zl", "LINE_SEPARATOR" },
            { "Zp", "PARAGRAPH_SEPARATOR" },
            { "Cc", "CNTRL" },
            { "Cf", "FORMAT" },
            { "Co", "PRIVATE USE" },
            { "Cs", "SURROGATE" },
            { "Pd", "DASH_PUNCTUATION" },
            { "Ps", "START_PUNCTUATION" },
            { "Pe", "END_PUNCTUATION" },
            { "Pc", "CONNECTOR_PUNCTUATION" },
            { "Po", "OTHER_PUNCTUATION" },
            { "Sm", "MATH_SYMBOL" },
            { "Sc", "CURRENCY_SYMBOL" },
            { "Sk", "MODIFIER_SYMBOL" },
            { "So", "OTHER_SYMBOL" },
            { "L", "LETTER" },
            { "M", "MARK" },
            { "N", "NUMBER" },
            { "Z", "SEPARATOR" },
            { "C", "CONTROL" },
            { "P", "PUNCTUATION" },
            { "S", "SYMBOL" },
            { "LD", "LETTER_OR_DIGIT" },
            { "L1", "Latin-1" },
            { "all", "ALL" },
            { "ASCII", "ASCII" },
            { "Alnum", "Alphanumeric characters" },
            { "Alpha", "Alphabetic characters" },
            { "Blank", "Space and tab characters" },
            { "Cntrl", "Control characters" },
            { "Digit", "Numeric characters" },
            { "Graph", "printable and visible" },
            { "Lower", "Lower-case alphabetic" },
            { "Print", "Printable characters" },
            { "Punct", "Punctuation characters" },
            { "Space", "Space characters" },
            { "Upper", "Upper-case alphabetic" },
            { "XDigit", "hexadecimal digits" },
            { "javaLowerCase", },
            { "javaUpperCase", },
            { "javaTitleCase", },
            { "javaDigit", },
            { "javaDefined", },
            { "javaLetter", },
            { "javaLetterOrDigit", },
            { "javaJavaIdentifierStart", },
            { "javaJavaIdentifierPart", },
            { "javaUnicodeIdentifierStart", },
            { "javaUnicodeIdentifierPart", },
            { "javaIdentifierIgnorable", },
            { "javaSpaceChar", },
            { "javaWhitespace", },
            { "javaISOControl", },
            { "javaMirrored", },
    };
}
