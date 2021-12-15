// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lang.java.parser.JavadocParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.javadoc.*;
import com.intellij.psi.tree.*;
import com.intellij.psi.tree.java.IJavaDocElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public interface JavaDocElementType {
  final class JavaDocCompositeElementType extends IJavaDocElementType implements ICompositeElementType {
    private final Supplier<? extends ASTNode> myConstructor;

    private JavaDocCompositeElementType(@NonNls @NotNull String debugName, @NotNull Supplier<? extends ASTNode> nodeClass) {
      super(debugName);
      myConstructor = nodeClass;
    }

    @NotNull
    @Override
    public ASTNode createCompositeNode() {
      return myConstructor.get();
    }
  }

  class JavaDocLazyElementType extends ILazyParseableElementType {
    private JavaDocLazyElementType(@NonNls final String debugName) {
      super(debugName, JavaLanguage.INSTANCE);
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new LazyParseablePsiElement(this, text);
    }
  }

  IElementType DOC_TAG = new JavaDocCompositeElementType("DOC_TAG", () -> new PsiDocTagImpl());
  IElementType DOC_INLINE_TAG = new JavaDocCompositeElementType("DOC_INLINE_TAG", () -> new PsiInlineDocTagImpl());
  IElementType DOC_METHOD_OR_FIELD_REF = new JavaDocCompositeElementType("DOC_METHOD_OR_FIELD_REF", () -> new PsiDocMethodOrFieldRef());
  IElementType DOC_PARAMETER_REF = new JavaDocCompositeElementType("DOC_PARAMETER_REF", () -> new PsiDocParamRef());
  IElementType DOC_TAG_VALUE_ELEMENT = new IJavaDocElementType("DOC_TAG_VALUE_ELEMENT");
  IElementType DOC_SNIPPET_TAG = new JavaDocCompositeElementType("DOC_SNIPPET_TAG", () -> new PsiSnippetDocTagImpl());
  IElementType DOC_SNIPPET_TAG_VALUE = new JavaDocCompositeElementType("DOC_SNIPPET_TAG_VALUE", () -> new PsiSnippetDocTagValueImpl());
  IElementType DOC_SNIPPET_BODY = new JavaDocCompositeElementType("DOC_SNIPPET_BODY", () -> new PsiSnippetDocTagBodyImpl());
  IElementType DOC_SNIPPET_ATTRIBUTE = new JavaDocCompositeElementType("DOC_SNIPPET_ATTRIBUTE", () -> new PsiSnippetAttributeImpl());
  IElementType DOC_SNIPPET_ATTRIBUTE_LIST = new JavaDocCompositeElementType("DOC_SNIPPET_ATTRIBUTE_LIST", () -> new PsiSnippetAttributeListImpl());

  ILazyParseableElementType DOC_REFERENCE_HOLDER = new JavaDocLazyElementType("DOC_REFERENCE_HOLDER") {
    private final JavaParserUtil.ParserWrapper myParser = builder -> JavadocParser.parseJavadocReference(builder);

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser, false, LanguageLevel.JDK_1_3);
    }
  };

  ILazyParseableElementType DOC_TYPE_HOLDER = new JavaDocLazyElementType("DOC_TYPE_HOLDER") {
    private final JavaParserUtil.ParserWrapper myParser = builder -> JavadocParser.parseJavadocType(builder);

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser, false, LanguageLevel.JDK_1_3);
    }
  };

  ILazyParseableElementType DOC_COMMENT = new IReparseableElementType("DOC_COMMENT", JavaLanguage.INSTANCE) {
    private final JavaParserUtil.ParserWrapper myParser = builder -> JavadocParser.parseDocCommentText(builder);

    @Override
    public ASTNode createNode(final CharSequence text) {
      return new PsiDocCommentImpl(text);
    }

    @Nullable
    @Override
    public ASTNode parseContents(@NotNull final ASTNode chameleon) {
      return JavaParserUtil.parseFragment(chameleon, myParser);
    }

    @Override
    public boolean isParsable(@NotNull final CharSequence buffer, @NotNull Language fileLanguage, @NotNull final Project project) {
      if (!StringUtil.startsWith(buffer, "/**") || !StringUtil.endsWith(buffer, "*/")) return false;

      Lexer lexer = JavaParserDefinition.createLexer(LanguageLevelProjectExtension.getInstance(project).getLanguageLevel());
      lexer.start(buffer);
      if (lexer.getTokenType() == DOC_COMMENT) {
        lexer.advance();
        return lexer.getTokenType() == null;
      }
      return false;
    }
  };

  TokenSet ALL_JAVADOC_ELEMENTS = TokenSet.create(
    DOC_TAG, DOC_INLINE_TAG, DOC_METHOD_OR_FIELD_REF, DOC_PARAMETER_REF, DOC_TAG_VALUE_ELEMENT,
    DOC_REFERENCE_HOLDER, DOC_TYPE_HOLDER, DOC_COMMENT);
}