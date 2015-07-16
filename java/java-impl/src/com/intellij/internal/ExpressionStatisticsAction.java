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

/*
 * @author max
 */
package com.intellij.internal;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExpressionStatisticsAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    assert project != null;

    final Data topLevelData = new Data();
    final Data subData = new Data();

    final VirtualFile dir = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
    assert dir != null;

    final List<VirtualFile> javaFiles = collectJavaFiles(dir, project);

    if (!ApplicationManagerEx.getApplicationEx().runProcessWithProgressSynchronouslyInReadAction(project, "Traversing PSI", true, "Cancel", null, new Runnable() {
      @Override
      public void run() {
        final PsiManager psiManager = PsiManager.getInstance(project);
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        indicator.setIndeterminate(false);
        for (int i = 0; i < javaFiles.size(); i++) {
          VirtualFile file = javaFiles.get(i);
          indicator.setText2(file.getPath());
          indicator.setFraction((double)i / javaFiles.size());
          PsiFile psiFile = psiManager.findFile(file);
          if (psiFile instanceof PsiJavaFile) {
            psiFile.accept(new PsiRecursiveElementWalkingVisitor() {
              @Override
              public void visitElement(PsiElement element) {
                if (element instanceof PsiIdentifier) {
                  int offset = element.getTextRange().getStartOffset();
                  PsiExpression minExpression = PsiTreeUtil.getParentOfType(element, PsiExpression.class);
                  if (minExpression != null && minExpression.getTextRange().getStartOffset() == offset) {
                    PsiExpression maxExpression = minExpression;
                    while (true) {
                      PsiExpression nextExpression = PsiTreeUtil.getParentOfType(maxExpression, PsiExpression.class, true);
                      if (nextExpression == null || nextExpression.getTextRange().getStartOffset() != offset) break;
                      maxExpression = nextExpression;
                    }
                    collectExpressionData(minExpression, maxExpression, topLevelData, subData);
                  }
                }

                super.visitElement(element);
              }
            });
          }
        }
      }
    })) {
      return;
    }

    Messages.showMessageDialog("Top-level: " + topLevelData.toString() + "\n\n" + "Sub-expressions: " + subData.toString(), "Expression Statistics", null);
  }

  @NotNull
  private static List<VirtualFile> collectJavaFiles(VirtualFile dir, Project project) {
    final List<VirtualFile> javaFiles = ContainerUtil.newArrayList();
    ProjectFileIndex.SERVICE.getInstance(project).iterateContentUnderDirectory(dir, new ContentIterator() {
      @Override
      public boolean processFile(VirtualFile file) {
        if (file.getName().endsWith(".java")) {
          javaFiles.add(file);
        }
        return true;
      }
    });
    return javaFiles;
  }

  private static void collectExpressionData(PsiExpression minExpression, PsiExpression maxExpression, Data topLevelData, Data subExpressionData) {
    Data data;
    if (minExpression == maxExpression || maxExpression instanceof PsiMethodCallExpression && minExpression == ((PsiMethodCallExpression)maxExpression).getMethodExpression()) {
      data = topLevelData;
    } else {
      data = subExpressionData;
    }

    if (minExpression instanceof PsiClassObjectAccessExpression || minExpression instanceof PsiThisExpression || minExpression instanceof PsiSuperExpression) {
      data.classes++;
      return;
    }

    if (!(minExpression instanceof PsiJavaCodeReferenceElement)) {
      data.other++;
      return;
    }

    classifyTarget(data, ((PsiJavaCodeReferenceElement)minExpression).resolve());
  }

  private static void classifyTarget(Data data, PsiElement target) {
    if (target instanceof PsiLocalVariable) {
      data.localVars++;
    }
    else if (target instanceof PsiParameter) {
      data.parameters++;
    }
    else if (target instanceof PsiMethod) {
      data.methods++;
    }
    else if (target instanceof PsiClass) {
      data.classes++;
    }
    else if (target instanceof PsiPackage) {
      data.packages++;
    }
    else if (target instanceof PsiField) {
      data.fields++;
    }
    else {
      data.other++;
    }
  }

  private static class Data {
    int localVars;
    int parameters;
    int methods;
    int classes;
    int fields;
    int packages;
    int other;

    @Override
    public String toString() {
      return "localVars=" + localVars +
             "\nparameters=" + parameters +
             "\nmethods=" + methods +
             "\nfields=" + fields +
             "\nclasses=" + classes +
             "\npackages=" + packages +
             "\nother=" + other +
             "\ntotal=" + (localVars + parameters + methods + fields + classes + packages + other);
    }
  }


}

