// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiElement;

/**
 * Represents elements starting from ':' (inclusive) and until '}' (exclusive) in @snippet javadoc tag.
 * @see PsiSnippetDocTag
 */
public interface PsiSnippetDocTagBody extends PsiElement {
}
