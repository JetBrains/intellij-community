/*
 * Copyright 2003-2006 Dave Griffith
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
package com.siyeh.ipp.base;

import com.intellij.modcommand.ActionContext;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class PsiElementContextPredicate implements PsiElementPredicate {
  public abstract boolean satisfiedBy(PsiElement element, @NotNull ActionContext context);

  @Override
  public boolean satisfiedBy(PsiElement element) {
    throw new UnsupportedOperationException("Context must be provided");
  }
}