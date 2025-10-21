// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiElement;

/**
 * Container element for link labels in Markdown JavaDoc.
 * It can contain multiple elements, for instance in {@code [label with `inline code`][java.lang.String]}.
 */
public interface PsiMarkdownReferenceLabel extends PsiElement {}
