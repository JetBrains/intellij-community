// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.java.syntax.element.JavaDocSyntaxElementType;
import com.intellij.java.syntax.lexer.JavaDocLexer;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.java.syntax.lexer.JavaTypeEscapeLexer;
import com.intellij.java.syntax.parser.JavaDocParser;
import com.intellij.java.syntax.parser.JavaParser;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.javadoc.PsiDocCommentImpl;
import com.intellij.psi.impl.source.javadoc.PsiDocFragmentNameImpl;
import com.intellij.psi.impl.source.javadoc.PsiDocFragmentRefImpl;
import com.intellij.psi.impl.source.javadoc.PsiDocMethodOrFieldRef;
import com.intellij.psi.impl.source.javadoc.PsiDocParamRef;
import com.intellij.psi.impl.source.javadoc.PsiDocTagImpl;
import com.intellij.psi.impl.source.javadoc.PsiInlineDocTagImpl;
import com.intellij.psi.impl.source.javadoc.PsiMarkdownCodeBlockImpl;
import com.intellij.psi.impl.source.javadoc.PsiMarkdownReferenceLabelImpl;
import com.intellij.psi.impl.source.javadoc.PsiMarkdownReferenceLinkImpl;
import com.intellij.psi.impl.source.javadoc.PsiSnippetAttributeImpl;
import com.intellij.psi.impl.source.javadoc.PsiSnippetAttributeListImpl;
import com.intellij.psi.impl.source.javadoc.PsiSnippetDocTagBodyImpl;
import com.intellij.psi.impl.source.javadoc.PsiSnippetDocTagImpl;
import com.intellij.psi.impl.source.javadoc.PsiSnippetDocTagValueImpl;
import com.intellij.psi.tree.ICompositeElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.IReparseableElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.java.IJavaDocElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * @see com.intellij.java.syntax.element.JavaDocSyntaxElementType
 */
public interface JavaDocElementType {
  final class JavaDocCompositeElementType extends IJavaDocElementType implements
                                                                      ICompositeElementType {
    private final Supplier<? extends ASTNode> myConstructor;

    private JavaDocCompositeElementType(@NonNls @NotNull String debugName, @NotNull Supplier<? extends ASTNode> nodeClass) {
      super(debugName);
      this.myConstructor = nodeClass;
    }

    @Override
    public @NotNull ASTNode createCompositeNode() {
      return myConstructor.get();
    }
  }

  final class JavaDocParentProviderElementType extends IJavaDocElementType {
    public JavaDocParentProviderElementType(@NotNull String debugName) {
      super(debugName);
    }
  }

  IElementType DOC_TAG = new JavaDocCompositeElementType("DOC_TAG", () -> new PsiDocTagImpl());
  IElementType DOC_INLINE_TAG = new JavaDocCompositeElementType("DOC_INLINE_TAG", () -> new PsiInlineDocTagImpl());
  IElementType DOC_METHOD_OR_FIELD_REF = new JavaDocCompositeElementType("DOC_METHOD_OR_FIELD_REF", () -> new PsiDocMethodOrFieldRef());
  IElementType DOC_FRAGMENT_REF = new JavaDocCompositeElementType("DOC_FRAGMENT_REF", () -> new PsiDocFragmentRefImpl());
  IElementType DOC_FRAGMENT_NAME = new JavaDocCompositeElementType("DOC_FRAGMENT_NAME", () -> new PsiDocFragmentNameImpl());
  IElementType DOC_PARAMETER_REF = new JavaDocCompositeElementType("DOC_PARAMETER_REF", () -> new PsiDocParamRef());
  IElementType DOC_TAG_VALUE_ELEMENT = new JavaDocParentProviderElementType("DOC_TAG_VALUE_ELEMENT");
  IElementType DOC_SNIPPET_TAG = new JavaDocCompositeElementType("DOC_SNIPPET_TAG", () -> new PsiSnippetDocTagImpl());
  IElementType DOC_SNIPPET_TAG_VALUE = new JavaDocCompositeElementType("DOC_SNIPPET_TAG_VALUE", () -> new PsiSnippetDocTagValueImpl());
  IElementType DOC_SNIPPET_BODY = new JavaDocCompositeElementType("DOC_SNIPPET_BODY", () -> new PsiSnippetDocTagBodyImpl());
  IElementType DOC_SNIPPET_ATTRIBUTE = new JavaDocCompositeElementType("DOC_SNIPPET_ATTRIBUTE", () -> new PsiSnippetAttributeImpl());
  IElementType DOC_SNIPPET_ATTRIBUTE_LIST =
    new JavaDocCompositeElementType("DOC_SNIPPET_ATTRIBUTE_LIST", () -> new PsiSnippetAttributeListImpl());
  IElementType DOC_SNIPPET_ATTRIBUTE_VALUE = new JavaDocParentProviderElementType("DOC_SNIPPET_ATTRIBUTE_VALUE");
  IElementType DOC_MARKDOWN_CODE_BLOCK = new JavaDocCompositeElementType("DOC_CODE_BLOCK", () -> new PsiMarkdownCodeBlockImpl());
  IElementType DOC_MARKDOWN_REFERENCE_LINK = new JavaDocCompositeElementType("DOC_REFERENCE_LINK", () -> new PsiMarkdownReferenceLinkImpl());
  IElementType DOC_MARKDOWN_REFERENCE_LABEL = new JavaDocCompositeElementType("DOC_REFERENCE_LABEL", () -> new PsiMarkdownReferenceLabelImpl());

  ILazyParseableElementType DOC_REFERENCE_HOLDER = new DocReferenceHolderElementType();
  ILazyParseableElementType DOC_TYPE_HOLDER = new DocTypeHolderElementType();
  ILazyParseableElementType DOC_COMMENT = new DocCommentElementType("DOC_COMMENT") {
    @Override
    public ASTNode createNode(final CharSequence text) {
      return new PsiDocCommentImpl(text);
    }
  };
  ILazyParseableElementType DOC_MARKDOWN_COMMENT = new DocCommentElementType("DOC_MARKDOWN_COMMENT") {
    @Override
    public ASTNode createNode(final CharSequence text) {
      return new PsiDocCommentImpl(text, true);
    }
  };

  /// TokenSet for both [classic][#DOC_COMMENT] and [Markdown][#DOC_MARKDOWN_COMMENT] Javadoc comments types 
  TokenSet DOC_COMMENT_TOKENS = TokenSet.create(DOC_COMMENT, DOC_MARKDOWN_COMMENT);

  @SuppressWarnings("unused") // used in plugins
  TokenSet ALL_JAVADOC_ELEMENTS = TokenSet.create(DOC_TAG, DOC_INLINE_TAG, DOC_METHOD_OR_FIELD_REF, DOC_PARAMETER_REF, DOC_TAG_VALUE_ELEMENT,
                                                  DOC_REFERENCE_HOLDER, DOC_TYPE_HOLDER, DOC_COMMENT, DOC_MARKDOWN_COMMENT);



  class JavaDocLazyElementType extends ILazyParseableElementType {

    private JavaDocLazyElementType(final @NonNls String debugName) {
      super(debugName, JavaLanguage.INSTANCE);
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new LazyParseablePsiElement(this, text);
    }
  }


  final class DocReferenceHolderElementType extends JavaDocLazyElementType {
    public DocReferenceHolderElementType() {
      super("DOC_REFERENCE_HOLDER");
    }

    @Override
    public @Nullable ASTNode parseContents(final @NotNull ASTNode chameleon) {
      return JavaParserUtil.parseFragment(
        chameleon,
        (builder, languageLevel) -> {
          new JavaDocParser(builder, languageLevel).parseJavadocReference(new JavaParser(languageLevel));
        },
        false,
        LanguageLevel.JDK_1_3);
    }
  }

  final class DocTypeHolderElementType extends JavaDocLazyElementType {
    public DocTypeHolderElementType() {
      super("DOC_TYPE_HOLDER");
    }

    @Override
    public @Nullable ASTNode parseContents(final @NotNull ASTNode chameleon) {
      return JavaParserUtil.parseFragment(
        chameleon,
        (builder, languageLevel) -> {
          new JavaDocParser(builder, languageLevel).parseJavadocType(new JavaParser(languageLevel));
        },
        false,
        LanguageLevel.JDK_1_3,
        level -> new JavaTypeEscapeLexer(new JavaLexer(level))
      );
    }
  }

  abstract class DocCommentElementType extends IReparseableElementType {
    private final JavaParserUtil.ParserWrapper myParser;


    public DocCommentElementType(String debugName) {
      super(debugName, JavaLanguage.INSTANCE);
      myParser = (builder, languageLevel) -> new JavaDocParser(builder, languageLevel).parseDocCommentText();
    }

    @Override
    public @Nullable ASTNode parseContents(final @NotNull ASTNode chameleon) {
      return JavaParserUtil.parseFragmentWithHighestLanguageLevel(chameleon, myParser, JavaDocLexer::new);
    }

    @Override
    public boolean isReparseable(@NotNull ASTNode currentNode,
                                 @NotNull CharSequence newText,
                                 @NotNull Language fileLanguage,
                                 @NotNull Project project) {
      if (!StringUtil.startsWith(newText, "/**") || !StringUtil.endsWith(newText, "*/")) return false;

      LanguageLevel level = LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
      JavaLexer lexer = new JavaLexer(level);
      lexer.start(newText);
      if (lexer.getTokenType() == JavaDocSyntaxElementType.DOC_COMMENT) {
        lexer.advance();
        return lexer.getTokenType() == null;
      }
      return false;
    }
  }
}