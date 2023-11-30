// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree;

import com.intellij.psi.impl.source.AbstractBasicJavaDocElementTypeFactory;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public class JavaDocElementTypeFactory extends AbstractBasicJavaDocElementTypeFactory {
  private JavaDocElementTypeFactory() {
  }

  public final static AbstractBasicJavaDocElementTypeFactory INSTANCE = new JavaDocElementTypeFactory();

  @NotNull
  private static JavaDocElementTypeContainer getJavaDocElementTypeContainer() {
    return new JavaDocElementTypeContainer(
      JavaDocElementType.DOC_TAG,
      JavaDocElementType.DOC_COMMENT,
      JavaDocElementType.DOC_SNIPPET_TAG,
      JavaDocElementType.DOC_INLINE_TAG,
      JavaDocElementType.DOC_REFERENCE_HOLDER,
      JavaDocElementType.DOC_TAG_VALUE_ELEMENT,
      JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_LIST,
      JavaDocElementType.DOC_SNIPPET_TAG_VALUE,
      JavaDocElementType.DOC_SNIPPET_BODY,
      JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_VALUE,
      JavaDocElementType.DOC_SNIPPET_ATTRIBUTE,
      JavaDocElementType.DOC_METHOD_OR_FIELD_REF,
      JavaDocElementType.DOC_TYPE_HOLDER,
      JavaDocElementType.DOC_PARAMETER_REF
    );
  }

  @Override
  public JavaDocElementTypeContainer getContainer() {
    return SingletonHelper.INSTANCE;
  }


  private static class SingletonHelper {
    private static final JavaDocElementTypeContainer INSTANCE = getJavaDocElementTypeContainer();
  }
}
