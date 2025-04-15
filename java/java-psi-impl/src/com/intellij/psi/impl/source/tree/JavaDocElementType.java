// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.lang.ASTNode;
import com.intellij.psi.impl.source.BasicJavaDocElementType;
import com.intellij.psi.impl.source.javadoc.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ILazyParseableElementType;
import com.intellij.psi.tree.ParentProviderElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.java.IJavaDocElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

/**
 * @see com.intellij.java.syntax.element.JavaDocSyntaxElementType
 */
public interface JavaDocElementType {
  final class JavaDocCompositeElementType extends BasicJavaDocElementType.JavaDocCompositeElementType {
    private JavaDocCompositeElementType(@NonNls @NotNull String debugName, @NotNull Supplier<? extends ASTNode> nodeClass, IElementType parentElementType) {
      super(debugName, nodeClass, parentElementType);
    }
  }

  final class JavaDocParentProviderElementType extends IJavaDocElementType implements ParentProviderElementType {

    private final Set<IElementType> myParentElementTypes;

    public JavaDocParentProviderElementType(@NotNull String debugName, @NotNull IElementType parentElementType) {
      super(debugName);
      myParentElementTypes = Collections.singleton(parentElementType);
    }

    @Override
    public @NotNull Set<IElementType> getParents() {
      return myParentElementTypes;
    }
  }

  IElementType DOC_TAG = new JavaDocCompositeElementType("DOC_TAG", () -> new PsiDocTagImpl(), BasicJavaDocElementType.BASIC_DOC_TAG);
  IElementType DOC_INLINE_TAG = new JavaDocCompositeElementType("DOC_INLINE_TAG", () -> new PsiInlineDocTagImpl(), BasicJavaDocElementType.BASIC_DOC_INLINE_TAG);
  IElementType DOC_METHOD_OR_FIELD_REF = new JavaDocCompositeElementType("DOC_METHOD_OR_FIELD_REF", () -> new PsiDocMethodOrFieldRef(), BasicJavaDocElementType.BASIC_DOC_METHOD_OR_FIELD_REF);
  IElementType DOC_PARAMETER_REF = new JavaDocCompositeElementType("DOC_PARAMETER_REF", () -> new PsiDocParamRef(), BasicJavaDocElementType.BASIC_DOC_PARAMETER_REF);
  IElementType DOC_TAG_VALUE_ELEMENT = new JavaDocParentProviderElementType("DOC_TAG_VALUE_ELEMENT", BasicJavaDocElementType.BASIC_DOC_TAG_VALUE_ELEMENT);
  IElementType DOC_SNIPPET_TAG = new JavaDocCompositeElementType("DOC_SNIPPET_TAG", () -> new PsiSnippetDocTagImpl(), BasicJavaDocElementType.BASIC_DOC_SNIPPET_TAG);
  IElementType DOC_SNIPPET_TAG_VALUE = new JavaDocCompositeElementType("DOC_SNIPPET_TAG_VALUE", () -> new PsiSnippetDocTagValueImpl(), BasicJavaDocElementType.BASIC_DOC_SNIPPET_TAG_VALUE);
  IElementType DOC_SNIPPET_BODY = new JavaDocCompositeElementType("DOC_SNIPPET_BODY", () -> new PsiSnippetDocTagBodyImpl(), BasicJavaDocElementType.BASIC_DOC_SNIPPET_BODY);
  IElementType DOC_SNIPPET_ATTRIBUTE = new JavaDocCompositeElementType("DOC_SNIPPET_ATTRIBUTE", () -> new PsiSnippetAttributeImpl(), BasicJavaDocElementType.BASIC_DOC_SNIPPET_ATTRIBUTE);
  IElementType DOC_SNIPPET_ATTRIBUTE_LIST =
    new JavaDocCompositeElementType("DOC_SNIPPET_ATTRIBUTE_LIST", () -> new PsiSnippetAttributeListImpl(), BasicJavaDocElementType.BASIC_DOC_SNIPPET_ATTRIBUTE_LIST);
  IElementType DOC_SNIPPET_ATTRIBUTE_VALUE = new JavaDocParentProviderElementType("DOC_SNIPPET_ATTRIBUTE_VALUE", BasicJavaDocElementType.BASIC_DOC_SNIPPET_ATTRIBUTE_VALUE);
  IElementType DOC_MARKDOWN_CODE_BLOCK = new JavaDocCompositeElementType("DOC_CODE_BLOCK", () -> new PsiMarkdownCodeBlockImpl(), BasicJavaDocElementType.BASIC_DOC_MARKDOWN_CODE_BLOCK);
  IElementType DOC_MARKDOWN_REFERENCE_LINK = new JavaDocCompositeElementType("DOC_REFERENCE_LINK", () -> new PsiMarkdownReferenceLinkImpl(), BasicJavaDocElementType.BASIC_DOC_MARKDOWN_REFERENCE_LINK);

  ILazyParseableElementType DOC_REFERENCE_HOLDER = new BasicJavaDocElementType.DocReferenceHolderElementType();
  ILazyParseableElementType DOC_TYPE_HOLDER = new BasicJavaDocElementType.DocTypeHolderElementType();
  ILazyParseableElementType DOC_COMMENT = new BasicJavaDocElementType.DocCommentElementType() {
    @Override
    public ASTNode createNode(final CharSequence text) {
      return new PsiDocCommentImpl(text);
    }
  };

  @SuppressWarnings("unused") // used in plugins
  TokenSet ALL_JAVADOC_ELEMENTS = TokenSet.create(DOC_TAG, DOC_INLINE_TAG, DOC_METHOD_OR_FIELD_REF, DOC_PARAMETER_REF, DOC_TAG_VALUE_ELEMENT,
                                                  DOC_REFERENCE_HOLDER, DOC_TYPE_HOLDER, DOC_COMMENT);
}