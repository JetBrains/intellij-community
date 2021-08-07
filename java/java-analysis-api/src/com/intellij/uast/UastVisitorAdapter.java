// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uast;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;
import org.jetbrains.uast.visitor.UastVisitor;


public class UastVisitorAdapter extends PsiElementVisitor {
  private final UastVisitor myUastVisitor;
  private final boolean directOnly;

  /**
   * @param visitor    a non-recursive Uast Visitor. (Note: visitor methods should return <b>true</b> to be non-recursive)
   * @param directOnly if true only elements which are directly converted from passed {@link PsiElement} will be processed.
   *                   Setting to true is useful to avoid duplicating reports on {@code sourcePsi} elements.
   */
  public UastVisitorAdapter(AbstractUastNonRecursiveVisitor visitor, boolean directOnly) {
    myUastVisitor = visitor;
    this.directOnly = directOnly;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    super.visitElement(element);
    UElement uElement = UastContextKt.toUElement(element);
    if (uElement != null && (!directOnly || uElement.getSourcePsi() == element)) {
      uElement.accept(myUastVisitor);
    }
  }
}
