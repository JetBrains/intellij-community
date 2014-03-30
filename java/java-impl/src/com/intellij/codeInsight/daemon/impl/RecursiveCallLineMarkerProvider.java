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
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.FunctionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Danila Ponomarenko
 */
public class RecursiveCallLineMarkerProvider implements LineMarkerProvider {

  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
    return null; //do nothing
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<PsiElement> elements,
                                     @NotNull Collection<LineMarkerInfo> result) {
    final Set<PsiStatement> statements = new HashSet<PsiStatement>();

    for (PsiElement element : elements) {
      ProgressManager.checkCanceled();
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

  public static boolean isRecursiveMethodCall(@NotNull PsiMethodCallExpression methodCall) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(methodCall, PsiMethod.class);
    if (method == null || !method.getName().equals(methodCall.getMethodExpression().getReferenceName())) {
      return false;
    }

    final PsiMethod resolvedMethod = methodCall.resolveMethod();

    if (!Comparing.equal(method, resolvedMethod)) {
      return false;
    }
    final PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
    return qualifier == null || qualifier instanceof PsiThisExpression;
  }

  private static class RecursiveMethodCallMarkerInfo extends LineMarkerInfo<PsiMethodCallExpression> {
    private RecursiveMethodCallMarkerInfo(@NotNull PsiMethodCallExpression methodCall) {
      super(methodCall,
            methodCall.getTextRange(),
            AllIcons.Gutter.RecursiveMethod,
            Pass.UPDATE_OVERRIDEN_MARKERS,
            FunctionUtil.<PsiMethodCallExpression, String>constant("Recursive call"),
            null,
            GutterIconRenderer.Alignment.RIGHT
      );
    }

    @Override
    public GutterIconRenderer createGutterRenderer() {
      if (myIcon == null) return null;
      return new LineMarkerGutterIconRenderer<PsiMethodCallExpression>(this){
        @Override
        public AnAction getClickAction() {
          return null; // to place breakpoint on mouse click
        }
      };
    }
  }
}

