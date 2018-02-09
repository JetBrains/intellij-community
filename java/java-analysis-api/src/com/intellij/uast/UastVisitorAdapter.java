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

  /**
   * @deprecated {@link UastVisitor} is recursive by default, when {@link PsiElementVisitor} is not recursive,
   * thus, passing arbitrary {@code UastVisitor} to this constructor is error-prone (IDEA-181783).
   * Make sure your {@code UastVisitor} is non-recursive,
   * in case of doubts implement {@link PsiElementVisitor#visitElement(PsiElement)} yourself
   * and convert passed @{code PsiElement} to required Uast type with {@link UastContextKt#toUElement(PsiElement, Class)}
   */
  @Deprecated
  public UastVisitorAdapter(UastVisitor visitor) {
    myUastVisitor = visitor;
  }

  public UastVisitorAdapter(AbstractUastNonRecursiveVisitor visitor) {
    myUastVisitor = visitor;
  }

  @Override
  public void visitElement(PsiElement element) {
    super.visitElement(element);
    UElement uElement = UastContextKt.toUElement(element);
    if (uElement != null) {
      uElement.accept(myUastVisitor);
    }
  }
}
