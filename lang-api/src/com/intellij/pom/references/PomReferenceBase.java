/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.pom.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class PomReferenceBase extends PomReference {
  protected PomReferenceBase(PsiElement element, TextRange rangeInElement) {
    super(element, rangeInElement);
  }

  protected PomReferenceBase(PsiElement element) {
    super(element);
  }

  @NotNull
  public PomTarget[] multiResolve() {
    final PomTarget target = resolve();
    if (target == null) {
      return PomTarget.EMPTY_ARRAY;
    }
    return new PomTarget[]{target};
  }

  public boolean isReferenceTo(@NotNull PomTarget candidate) {
    return candidate.equals(resolve());
  }

}
