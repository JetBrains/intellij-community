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
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.FunctionUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Danila Ponomarenko
 */
public class RecursiveCallLineMarkerProvider implements LineMarkerProvider, DumbAware {
  private static final Icon RECURSIVE_METHOD_ICON = IconLoader.getIcon("/gutter/recursiveMethod.png");

  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    return null;    //do nothing
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
    final Set<PsiStatement> statements = new HashSet<PsiStatement>();

    for (PsiElement element : elements) {
      if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;
        final PsiStatement statement = PsiTreeUtil.getParentOfType(methodCall, PsiStatement.class, true, PsiMethod.class);
        if (!statements.contains(statement) && isRecursiveMethodCall(methodCall)) {
          statements.add(statement);
          result.add(new RecursiveMethodCallMarkerInfo(methodCall));
        }
      }
    }
  }

  private static boolean isRecursiveMethodCall(@NotNull PsiMethodCallExpression methodCall) {
    final PsiMethod referencedMethod = methodCall.resolveMethod();

    if (referencedMethod == null || !referencedMethod.isValid() || !methodCall.isValid()) {
      return false;
    }

    final PsiFile methodCallFile = methodCall.getContainingFile();
    final PsiFile methodFile = referencedMethod.getContainingFile();

    if (methodCallFile == null || methodFile == null || !methodCallFile.equals(methodFile)) {
      return false;
    }

    final TextRange rmRange = referencedMethod.getTextRange();
    final int mcOffset = methodCall.getTextRange().getStartOffset();
    return rmRange != null && rmRange.contains(mcOffset);
  }

  private static class RecursiveMethodCallMarkerInfo extends LineMarkerInfo<PsiMethodCallExpression> {
    private RecursiveMethodCallMarkerInfo(@NotNull PsiMethodCallExpression methodCall) {
      super(methodCall,
            methodCall.getTextRange(),
            RECURSIVE_METHOD_ICON,
            Pass.UPDATE_OVERRIDEN_MARKERS,
            FunctionUtil.<PsiMethodCallExpression, String>constant("Recursive method call"),
            null,
            GutterIconRenderer.Alignment.RIGHT
      );
    }
  }
}
