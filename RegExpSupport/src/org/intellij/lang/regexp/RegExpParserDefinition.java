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
package org.intellij.lang.regexp;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.intellij.lang.regexp.psi.impl.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.EnumSet;

public class RegExpParserDefinition implements ParserDefinition {
    private static final TokenSet COMMENT_TOKENS = TokenSet.create(RegExpTT.COMMENT);
    private static final EnumSet<RegExpCapability> CAPABILITIES = EnumSet.of(RegExpCapability.NESTED_CHARACTER_CLASSES,
                                                                             RegExpCapability.ALLOW_HORIZONTAL_WHITESPACE_CLASS,
                                                                             RegExpCapability.UNICODE_CATEGORY_SHORTHAND);

    @TestOnly
    public static void setTestCapability(@Nullable RegExpCapability capability, @NotNull Disposable parentDisposable) {
        if (!CAPABILITIES.contains(capability)) {
            CAPABILITIES.add(capability);
            Disposer.register(parentDisposable, () -> CAPABILITIES.remove(capability));
        }
    }
    
    @NotNull
    public Lexer createLexer(Project project) {
        return new RegExpLexer(CAPABILITIES);
    }

    public PsiParser createParser(Project project) {
        return new RegExpParser(CAPABILITIES);
    }

    public IFileElementType getFileNodeType() {
        return RegExpElementTypes.REGEXP_FILE;
    }

    @NotNull
    public TokenSet getWhitespaceTokens() {
        // trick to hide quote tokens from parser... should actually go into the lexer
        return TokenSet.create(RegExpTT.QUOTE_BEGIN, RegExpTT.QUOTE_END, TokenType.WHITE_SPACE);
    }

    @NotNull
    public TokenSet getStringLiteralElements() {
        return TokenSet.EMPTY;
    }

    @NotNull
    public TokenSet getCommentTokens() {
        return COMMENT_TOKENS;
    }

    @NotNull
    public PsiElement createElement(ASTNode node) {
        final IElementType type = node.getElementType();
        if (type == RegExpElementTypes.PATTERN) {
            return new RegExpPatternImpl(node);
        } else if (type == RegExpElementTypes.BRANCH) {
            return new RegExpBranchImpl(node);
        } else if (type == RegExpElementTypes.SIMPLE_CLASS) {
            return new RegExpSimpleClassImpl(node);
        } else if (type == RegExpElementTypes.CLASS) {
            return new RegExpClassImpl(node);
        } else if (type == RegExpElementTypes.CHAR_RANGE) {
            return new RegExpCharRangeImpl(node);
        } else if (type == RegExpElementTypes.CHAR) {
            return new RegExpCharImpl(node);
        } else if (type == RegExpElementTypes.GROUP) {
            return new RegExpGroupImpl(node);
        } else if (type == RegExpElementTypes.PROPERTY) {
            return new RegExpPropertyImpl(node);
        } else if (type == RegExpElementTypes.SET_OPTIONS) {
            return new RegExpSetOptionsImpl(node);
        } else if (type == RegExpElementTypes.OPTIONS) {
            return new RegExpOptionsImpl(node);
        } else if (type == RegExpElementTypes.BACKREF) {                                    
            return new RegExpBackrefImpl(node);
        } else if (type == RegExpElementTypes.CLOSURE) {
            return new RegExpClosureImpl(node);
        } else if (type == RegExpElementTypes.QUANTIFIER) {
            return new RegExpQuantifierImpl(node);
        } else if (type == RegExpElementTypes.BOUNDARY) {
            return new RegExpBoundaryImpl(node);
        } else if (type == RegExpElementTypes.INTERSECTION) {
            return new RegExpIntersectionImpl(node);
        } else if (type == RegExpElementTypes.UNION) {
            return new RegExpUnionImpl(node);
        } else if (type == RegExpElementTypes.NAMED_GROUP_REF) {
            return new RegExpNamedGroupRefImpl(node);
        } else if (type == RegExpElementTypes.PY_COND_REF) {
            return new RegExpPyCondRefImpl(node);
        } else if (type == RegExpElementTypes.POSIX_BRACKET_EXPRESSION) {
            return new RegExpPosixBracketExpressionImpl(node);
        }
      
        return new ASTWrapperPsiElement(node);
    }

    public PsiFile createFile(FileViewProvider viewProvider) {
        return new RegExpFile(viewProvider, RegExpLanguage.INSTANCE);
    }

    public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MUST_NOT;
    }
}
