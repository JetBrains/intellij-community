// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import org.jetbrains.annotations.Nullable;

/**
 * Represents inline javadoc tag '@snippet' which may hold code example in comment or a link to a file with example.
 * <p>
 * <a href="https://openjdk.org/jeps/413">JEP 413</a>
 */
public interface PsiSnippetDocTag extends PsiInlineDocTag {
  @Override
  @Nullable PsiSnippetDocTagValue getValueElement();
}
