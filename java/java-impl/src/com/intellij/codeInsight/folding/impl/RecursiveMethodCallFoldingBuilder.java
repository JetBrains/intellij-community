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
package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Danila Ponomarenko
 */
public class RecursiveMethodCallFoldingBuilder extends FoldingBuilderEx {

  @NotNull
  @Override
  public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    if (!(root instanceof PsiJavaFile) || quick || !JavaCodeFoldingSettings.getInstance().isCollapseRecursiveMethodCalls()) {
      return FoldingDescriptor.EMPTY;
    }
    final List<FoldingDescriptor> result = new ArrayList<FoldingDescriptor>();
    root.accept(new JavaRecursiveElementWalkingVisitor() {

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        if (isRecursiveMethodCall(expression)) {
          result.add(new FoldingDescriptor(expression, expression.getTextRange()));
        }
        else {
          super.visitMethodCallExpression(expression);
        }
      }
    });

    Collections.sort(result, new Comparator<FoldingDescriptor>() {
      @Override
      public int compare(FoldingDescriptor o1, FoldingDescriptor o2) {
        return o2.getRange().getStartOffset() - o1.getRange().getStartOffset();
      }
    });

    return result.toArray(new FoldingDescriptor[result.size()]);
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(node);
    JavaCodeFoldingSettings settings = JavaCodeFoldingSettings.getInstance();

    if (element instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;
      return isRecursiveMethodCall(methodCall) && settings.isCollapseRecursiveMethodCalls();
    }
    return false;
  }

  private static final char ANTICLOCKWISE_GAPPED_CIRCLE_ARROW = '\u27F2'; //⟲

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    return getPlaceholderText(node.getPsi());
  }

  @NotNull
  private static String getPlaceholderText(@NotNull PsiElement element) {
    if (element instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)element;
      return getPlaceholderText(methodCall);
    }

    return element.getText();
  }

  @NotNull
  private static String getPlaceholderText(@NotNull PsiMethodCallExpression methodCall) {
    return getQualifierText(methodCall) + ANTICLOCKWISE_GAPPED_CIRCLE_ARROW + methodCall.getArgumentList().getText();
  }

  @NotNull
  private static String getQualifierText(@NotNull PsiMethodCallExpression methodCall) {
    final PsiElement qualifier = methodCall.getMethodExpression().getQualifier();
    return qualifier != null ? qualifier.getText() + "." : "";
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
}
