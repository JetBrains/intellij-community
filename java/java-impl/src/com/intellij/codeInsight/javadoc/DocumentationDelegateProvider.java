// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.javadoc;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiDocCommentOwner;
import com.intellij.psi.PsiMember;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Computes an element from which the documentation for a given member should be copied.
 */
public abstract class DocumentationDelegateProvider {

  public static final ExtensionPointName<DocumentationDelegateProvider> EP_NAME = ExtensionPointName.create(
    "com.intellij.documentationDelegateProvider"
  );

  /**
   * <p>
   * Computes {@link PsiDocCommentOwner } to get documentation from.
   * </p>
   * <p>
   * Suppose there is a {@code Foo#bar()} with doc and {@code Baz#bar()} without doc:
   * <pre>
   * class Foo {
   *   /**
   *   * Some javadoc
   *   *&#47;
   *   void bar() {}
   * }
   * class Baz {
   *   void bar() {}
   * }
   * </pre>
   * If it is needed to display doc from {@code Foo#bar()} when doc for {@code Baz#bar()} is queried
   * then this method should return PsiMethod corresponding to {@code Foo#bar()} for PsiMethod corresponding to {@code Baz#bar()}.
   * <br>
   * The copied documentation will include <i>Description copied from</i> link.
   * </p>
   *
   * @param member method to search delegate for.
   * @return delegate PsiDocCommentOwner instance.
   */
  @Nullable
  @Contract(pure = true)
  public abstract PsiDocCommentOwner computeDocumentationDelegate(@NotNull PsiMember member);

  @Nullable
  public static PsiDocCommentOwner findDocumentationDelegate(@NotNull PsiMember method) {
    for (DocumentationDelegateProvider delegator : EP_NAME.getExtensionList()) {
      PsiDocCommentOwner type = delegator.computeDocumentationDelegate(method);
      if (type != null) {
        return type;
      }
    }
    return null;
  }
}
