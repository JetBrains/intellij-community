/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uast;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;
import org.jetbrains.uast.visitor.UastVisitor;

/**
 * @author yole
 */
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
  public void visitElement(PsiElement element) {
    super.visitElement(element);
    UElement uElement = UastContextKt.toUElement(element);
    if (uElement != null && (!directOnly || uElement.getSourcePsi() == element)) {
      uElement.accept(myUastVisitor);
    }
  }
}
