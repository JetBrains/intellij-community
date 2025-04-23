// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.java.syntax.element.JavaDocSyntaxElementType;
import com.intellij.java.syntax.lexer.JavaLexer;
import com.intellij.java.syntax.lexer.JavaTypeEscapeLexer;
import com.intellij.java.syntax.parser.JavaDocParser;
import com.intellij.java.syntax.parser.JavaParser;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.java.parser.BasicJavaParserUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.tree.LazyParseablePsiElement;
import com.intellij.psi.tree.*;
import com.intellij.psi.tree.java.IJavaDocElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

//todo remove and merge with standard
/**
 * @see JavaDocSyntaxElementType
 */
public interface BasicJavaDocElementType {
  IElementType BASIC_DOC_TAG = new IJavaDocElementType("DOC_TAG");
  IElementType BASIC_DOC_INLINE_TAG = new IJavaDocElementType("DOC_INLINE_TAG");
  IElementType BASIC_DOC_METHOD_OR_FIELD_REF = new IJavaDocElementType("DOC_METHOD_OR_FIELD_REF");
  IElementType BASIC_DOC_PARAMETER_REF = new IJavaDocElementType("DOC_PARAMETER_REF");
  IElementType BASIC_DOC_TAG_VALUE_ELEMENT = new IJavaDocElementType("DOC_TAG_VALUE_ELEMENT");
  IElementType BASIC_DOC_SNIPPET_TAG = new IJavaDocElementType("DOC_SNIPPET_TAG");
  IElementType BASIC_DOC_SNIPPET_TAG_VALUE = new IJavaDocElementType("DOC_SNIPPET_TAG_VALUE");
  IElementType BASIC_DOC_SNIPPET_BODY = new IJavaDocElementType("DOC_SNIPPET_BODY");
  IElementType BASIC_DOC_SNIPPET_ATTRIBUTE = new IJavaDocElementType("DOC_SNIPPET_ATTRIBUTE");
  IElementType BASIC_DOC_SNIPPET_ATTRIBUTE_LIST =
    new IJavaDocElementType("DOC_SNIPPET_ATTRIBUTE_LIST");
  IElementType BASIC_DOC_SNIPPET_ATTRIBUTE_VALUE = new IJavaDocElementType("DOC_SNIPPET_ATTRIBUTE_VALUE");

  IElementType BASIC_DOC_REFERENCE_HOLDER = new IJavaDocElementType("DOC_REFERENCE_HOLDER");

  IElementType BASIC_DOC_TYPE_HOLDER = new IJavaDocElementType("DOC_TYPE_HOLDER");

  IElementType BASIC_DOC_COMMENT = new IJavaDocElementType("DOC_COMMENT");
  IElementType BASIC_DOC_MARKDOWN_CODE_BLOCK = new IJavaDocElementType("DOC_CODE_BLOCK");
  IElementType BASIC_DOC_MARKDOWN_REFERENCE_LINK = new IJavaDocElementType("DOC_REFERENCE_LINK");

  ParentAwareTokenSet BASIC_ALL_JAVADOC_ELEMENTS = ParentAwareTokenSet.create(
    BASIC_DOC_TAG, BASIC_DOC_INLINE_TAG, BASIC_DOC_METHOD_OR_FIELD_REF, BASIC_DOC_PARAMETER_REF, BASIC_DOC_TAG_VALUE_ELEMENT,
    BASIC_DOC_REFERENCE_HOLDER, BASIC_DOC_TYPE_HOLDER, BASIC_DOC_COMMENT);


  class JavaDocCompositeElementType extends IJavaDocElementType implements ICompositeElementType, ParentProviderElementType {
    private final Supplier<? extends ASTNode> myConstructor;
    private final Set<IElementType> myParentElementTypes;

    protected JavaDocCompositeElementType(@NonNls @NotNull String debugName,
                                          @NotNull Supplier<? extends ASTNode> nodeClass,
                                          @NotNull IElementType parentElementType) {
      super(debugName);
      myConstructor = nodeClass;
      myParentElementTypes = Collections.singleton(parentElementType);
    }

    @Override
    public @NotNull ASTNode createCompositeNode() {
      return myConstructor.get();
    }

    @Override
    public @NotNull Set<IElementType> getParents() {
      return myParentElementTypes;
    }
  }

  class JavaDocLazyElementType extends ILazyParseableElementType implements ParentProviderElementType {
    private final Set<IElementType> myParentElementTypes;

    private JavaDocLazyElementType(final @NonNls String debugName, @NotNull IElementType parentElementType) {
      super(debugName, JavaLanguage.INSTANCE);
      myParentElementTypes = Collections.singleton(parentElementType);
    }

    @Override
    public ASTNode createNode(CharSequence text) {
      return new LazyParseablePsiElement(this, text);
    }

    @Override
    public @NotNull Set<IElementType> getParents() {
      return myParentElementTypes;
    }
  }

  final class DocReferenceHolderElementType extends JavaDocLazyElementType {
    public DocReferenceHolderElementType() {
      super("DOC_REFERENCE_HOLDER", BASIC_DOC_REFERENCE_HOLDER);
    }

    @Override
    public @Nullable ASTNode parseContents(final @NotNull ASTNode chameleon) {
      return BasicJavaParserUtil.parseFragment(
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
      super("DOC_TYPE_HOLDER", BASIC_DOC_TYPE_HOLDER);
    }

    @Override
    public @Nullable ASTNode parseContents(final @NotNull ASTNode chameleon) {
      return BasicJavaParserUtil.parseFragment(
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

  abstract class DocCommentElementType extends IReparseableElementType implements ParentProviderElementType {
    private final BasicJavaParserUtil.ParserWrapper myParser;

    private static final Set<IElementType> myParentElementTypes = Collections.singleton(BASIC_DOC_COMMENT);

    public DocCommentElementType() {
      super("DOC_COMMENT", JavaLanguage.INSTANCE);
      myParser = (builder, languageLevel) -> new JavaDocParser(builder, languageLevel).parseDocCommentText();
    }

    @Override
    public @Nullable ASTNode parseContents(final @NotNull ASTNode chameleon) {
      return BasicJavaParserUtil.parseFragmentWithHighestLanguageLevel(chameleon, myParser);
    }

    @Override
    public boolean isParsable(final @NotNull CharSequence buffer, @NotNull Language fileLanguage, final @NotNull Project project) {
      if (!StringUtil.startsWith(buffer, "/**") || !StringUtil.endsWith(buffer, "*/")) return false;

      LanguageLevel level = LanguageLevelProjectExtension.getInstance(project).getLanguageLevel();
      JavaLexer lexer = new JavaLexer(level);
      lexer.start(buffer);
      if (lexer.getTokenType() == JavaDocSyntaxElementType.DOC_COMMENT) {
        lexer.advance();
        return lexer.getTokenType() == null;
      }
      return false;
    }

    @Override
    public @NotNull Set<IElementType> getParents() {
      return myParentElementTypes;
    }
  }
}