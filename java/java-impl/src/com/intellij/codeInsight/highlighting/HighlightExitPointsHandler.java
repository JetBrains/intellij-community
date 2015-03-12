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
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.featureStatistics.ProductivityFeatureNames;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.controlFlow.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class HighlightExitPointsHandler extends HighlightUsagesHandlerBase<PsiElement> {
  private final PsiElement myTarget;

  public HighlightExitPointsHandler(final Editor editor, final PsiFile file, final PsiElement target) {
    super(editor, file);
    myTarget = target;
  }

  @Override
  public List<PsiElement> getTargets() {
    return Collections.singletonList(myTarget);
  }

  @Override
  protected void selectTargets(final List<PsiElement> targets, final Consumer<List<PsiElement>> selectionConsumer) {
    selectionConsumer.consume(targets);
  }

  @Override
  public void computeUsages(final List<PsiElement> targets) {
    PsiElement parent = myTarget.getParent();
    if (!(parent instanceof PsiReturnStatement) && !(parent instanceof PsiThrowStatement)) return;

    PsiCodeBlock body = null;
    final PsiElement psiElement = PsiTreeUtil.getParentOfType(myTarget, PsiLambdaExpression.class, PsiMethod.class);
    if (psiElement instanceof PsiLambdaExpression) {
      final PsiElement lambdaBody = ((PsiLambdaExpression)psiElement).getBody();
      if (lambdaBody instanceof PsiCodeBlock) {
        body = (PsiCodeBlock)lambdaBody;
      }
    } else if (psiElement instanceof PsiMethod) {
      body = ((PsiMethod)psiElement).getBody();
    }

    if (body == null) return;

    try {
      highlightExitPoints((PsiStatement)parent, body);
    }
    catch (AnalysisCanceledException e) {
      // ignore
    }
  }

  @Nullable
  private static PsiElement getExitTarget(PsiStatement exitStatement) {
    if (exitStatement instanceof PsiReturnStatement) {
      return PsiTreeUtil.getParentOfType(exitStatement, PsiMethod.class);
    }
    else if (exitStatement instanceof PsiBreakStatement) {
      return ((PsiBreakStatement)exitStatement).findExitedStatement();
    }
    else if (exitStatement instanceof PsiContinueStatement) {
      return ((PsiContinueStatement)exitStatement).findContinuedStatement();
    }
    else if (exitStatement instanceof PsiThrowStatement) {
      final PsiExpression expr = ((PsiThrowStatement)exitStatement).getException();
      if (expr == null) return null;
      final PsiType exceptionType = expr.getType();
      if (!(exceptionType instanceof PsiClassType)) return null;

      PsiElement target = exitStatement;
      while (!(target instanceof PsiMethod || target == null || target instanceof PsiClass || target instanceof PsiFile)) {
        if (target instanceof PsiTryStatement) {
          final PsiTryStatement tryStatement = (PsiTryStatement)target;
          final PsiParameter[] params = tryStatement.getCatchBlockParameters();
          for (PsiParameter param : params) {
            if (param.getType().isAssignableFrom(exceptionType)) {
              break;
            }
          }

        }
        target = target.getParent();
      }
      if (target instanceof PsiMethod || target instanceof PsiTryStatement) {
        return target;
      }
      return null;
    }

    return null;
  }

  private void highlightExitPoints(final PsiStatement parent, final PsiCodeBlock body) throws AnalysisCanceledException {
    final Project project = myTarget.getProject();
    ControlFlow flow = ControlFlowFactory.getInstance(project).getControlFlow(body, LocalsOrMyInstanceFieldsControlFlowPolicy.getInstance(), false);

    Collection<PsiStatement> exitStatements = ControlFlowUtil.findExitPointsAndStatements(flow, 0, flow.getSize(), new IntArrayList(),
                                                                                          PsiReturnStatement.class, PsiBreakStatement.class,
                                                                                          PsiContinueStatement.class, PsiThrowStatement.class);
    if (!exitStatements.contains(parent)) return;

    PsiElement originalTarget = getExitTarget(parent);

    final Iterator<PsiStatement> it = exitStatements.iterator();
    while (it.hasNext()) {
      PsiStatement psiStatement = it.next();
      if (getExitTarget(psiStatement) != originalTarget) {
        it.remove();
      }
    }

    for (PsiElement e : exitStatements) {
      addOccurrence(e);
    }
    myStatusText = CodeInsightBundle.message("status.bar.exit.points.highlighted.message", exitStatements.size(),
                                                                      HighlightUsagesHandler.getShortcutText());
  }

  @Nullable
  @Override
  public String getFeatureId() {
    return ProductivityFeatureNames.CODEASSISTS_HIGHLIGHT_RETURN;
  }
}
