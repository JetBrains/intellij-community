/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.paths;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class GlobalPathReferenceProvider implements PathReferenceProvider {

  @NonNls
  private static final String MAILTO_PREFIX = "mailto:";

  public boolean createReferences(@NotNull PsiElement psiElement, final @NotNull List<PsiReference> references, final boolean soft) {
    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(psiElement);
    if (manipulator == null) {
      return false;
    }
    final TextRange range = manipulator.getRangeInElement(psiElement);
    final String s = range.substring(psiElement.getText());
    if (s.contains("://") || s.startsWith(MAILTO_PREFIX) || s.startsWith("//")) {
      final PsiReference reference = PsiReferenceBase.createSelfReference(psiElement, psiElement);
      references.add(reference);
      return true;
    }
    return false;
  }

  @Nullable
  public PathReference getPathReference(@NotNull String path, @NotNull final PsiElement element) {
    return path.contains("://") ? new PathReference(path, PathReference.NULL_ICON) : null;
  }
}
