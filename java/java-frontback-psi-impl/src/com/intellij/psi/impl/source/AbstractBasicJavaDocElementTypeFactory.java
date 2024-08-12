// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public abstract class AbstractBasicJavaDocElementTypeFactory {
  public static final class JavaDocElementTypeContainer {
    public final IElementType DOC_TAG;

    public  final IElementType DOC_COMMENT;

    public  final IElementType DOC_SNIPPET_TAG;

    public  final IElementType DOC_INLINE_TAG;

    public  final IElementType DOC_REFERENCE_HOLDER;

    public  final IElementType DOC_TAG_VALUE_ELEMENT;

    public  final IElementType DOC_SNIPPET_ATTRIBUTE_LIST;

    public  final IElementType DOC_SNIPPET_TAG_VALUE;

    public  final IElementType DOC_SNIPPET_BODY;

    public  final IElementType DOC_SNIPPET_ATTRIBUTE_VALUE;

    public  final IElementType DOC_SNIPPET_ATTRIBUTE;

    public  final IElementType DOC_METHOD_OR_FIELD_REF;

    public  final IElementType DOC_TYPE_HOLDER;

    public  final IElementType DOC_PARAMETER_REF;

    public final IElementType DOC_MARKDOWN_CODE_BLOCK;

    public final IElementType DOC_MARKDOWN_REFERENCE_LINK;

    public JavaDocElementTypeContainer(IElementType DOC_TAG,
                                       IElementType DOC_COMMENT,
                                       IElementType DOC_SNIPPET_TAG,
                                       IElementType DOC_INLINE_TAG,
                                       IElementType DOC_REFERENCE_HOLDER,
                                       IElementType DOC_TAG_VALUE_ELEMENT,
                                       IElementType DOC_SNIPPET_ATTRIBUTE_LIST,
                                       IElementType DOC_SNIPPET_TAG_VALUE,
                                       IElementType DOC_SNIPPET_BODY,
                                       IElementType DOC_SNIPPET_ATTRIBUTE_VALUE,
                                       IElementType DOC_SNIPPET_ATTRIBUTE,
                                       IElementType DOC_METHOD_OR_FIELD_REF,
                                       IElementType DOC_TYPE_HOLDER,
                                       IElementType DOC_PARAMETER_REF,
                                       IElementType DOC_MARKDOWN_CODE_BLOCK,
                                       IElementType DOC_MARKDOWN_REFERENCE_LINK) {
      this.DOC_TAG = DOC_TAG;
      this.DOC_COMMENT = DOC_COMMENT;
      this.DOC_SNIPPET_TAG = DOC_SNIPPET_TAG;
      this.DOC_INLINE_TAG = DOC_INLINE_TAG;
      this.DOC_REFERENCE_HOLDER = DOC_REFERENCE_HOLDER;
      this.DOC_TAG_VALUE_ELEMENT = DOC_TAG_VALUE_ELEMENT;
      this.DOC_SNIPPET_ATTRIBUTE_LIST = DOC_SNIPPET_ATTRIBUTE_LIST;
      this.DOC_SNIPPET_TAG_VALUE = DOC_SNIPPET_TAG_VALUE;
      this.DOC_SNIPPET_BODY = DOC_SNIPPET_BODY;
      this.DOC_SNIPPET_ATTRIBUTE_VALUE = DOC_SNIPPET_ATTRIBUTE_VALUE;
      this.DOC_SNIPPET_ATTRIBUTE = DOC_SNIPPET_ATTRIBUTE;
      this.DOC_METHOD_OR_FIELD_REF = DOC_METHOD_OR_FIELD_REF;
      this.DOC_TYPE_HOLDER = DOC_TYPE_HOLDER;
      this.DOC_PARAMETER_REF = DOC_PARAMETER_REF;
      this.DOC_MARKDOWN_CODE_BLOCK = DOC_MARKDOWN_CODE_BLOCK;
      this.DOC_MARKDOWN_REFERENCE_LINK = DOC_MARKDOWN_REFERENCE_LINK;
    }
  }

  public abstract JavaDocElementTypeContainer getContainer();
}
