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
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressLocalWithCommentFix extends SuppressByJavaCommentFix {
  public SuppressLocalWithCommentFix(@NotNull HighlightDisplayKey key) {
    super(key);
  }

  @Nullable
  @Override
  public PsiElement getContainer(PsiElement context) {
    final PsiElement container = super.getContainer(context);
    if (container != null) {
      final PsiElement elementToAnnotate = JavaSuppressionUtil.getElementToAnnotate(context, container);
      if (elementToAnnotate == null) return null;
    }
    return container;
  }

  @Override
  protected PsiElement getElementToAnnotate(@NotNull PsiElement element, @NotNull PsiElement container) {
    return null;
  }

  @NotNull
  @Override
  public String getText() {
    return JavaAnalysisBundle.message("suppress.for.statement.with.comment");
  }

  @Override
  public int getPriority() {
    return 20;
  }
}
