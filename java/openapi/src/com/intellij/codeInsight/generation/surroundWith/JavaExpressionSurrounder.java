/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public abstract class JavaExpressionSurrounder implements Surrounder {
  public static final ExtensionPointName<JavaExpressionSurrounder> EP_NAME = ExtensionPointName.create("com.intellij.javaExpressionSurrounder");

  @Override
  public boolean isApplicable(@NotNull PsiElement[] elements) {
    return elements.length == 1 &&
           elements[0] instanceof PsiExpression &&
           isApplicable((PsiExpression)elements[0]);
  }

  public abstract boolean isApplicable(PsiExpression expr);

  @Override
  public TextRange surroundElements(@NotNull Project project,
                                    @NotNull Editor editor,
                                    @NotNull PsiElement[] elements) throws IncorrectOperationException {
    if (elements.length != 1 || !(elements[0] instanceof PsiExpression)) {
      throw new IllegalArgumentException(Arrays.toString(elements));
    }
    return surroundExpression(project, editor, (PsiExpression)elements[0]);
  }

  public abstract TextRange surroundExpression(Project project, Editor editor, PsiExpression expr) throws IncorrectOperationException;
}
