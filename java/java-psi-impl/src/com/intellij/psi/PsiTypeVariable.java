/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;

/**
 * Used in Generify refactoring
 */
public abstract class PsiTypeVariable extends PsiType {
  protected PsiTypeVariable() {
    super(PsiAnnotation.EMPTY_ARRAY);
  }

  public abstract int getIndex();
  public abstract boolean isValidInContext (PsiType type);

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    if (visitor instanceof PsiTypeVisitorEx) {
      return ((PsiTypeVisitorEx<A>)visitor).visitTypeVariable(this);
    }
    else {
      return visitor.visitType(this);
    }
  }
}
