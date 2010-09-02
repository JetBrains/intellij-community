/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.codeInspection.inferNullity;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.BaseAnalysisAction;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiUtil;
import com.intellij.ui.TitledSeparator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class InferNullityAnnotationsAction extends BaseAnalysisAction {
  @NonNls private static final String INFER_NULLITY_ANNOTATIONS = "Infer Nullity Annotations";
  private JCheckBox myAnnotateLocalVariablesCb;

  public InferNullityAnnotationsAction() {
    super("Infer nullity", INFER_NULLITY_ANNOTATIONS);
  }

  @Override
  protected void analyze(@NotNull final Project project, final AnalysisScope scope) {
    final PsiClass annotationClass = JavaPsiFacade.getInstance(project).findClass(AnnotationUtil.NULLABLE, GlobalSearchScope.allScope(project));
    if (annotationClass == null) {
      Messages.showErrorDialog(project, "Infer Nullity Annotations requires that the JetBrains nullity annotations" +
                                        " be available to your project.\n\nYou will need to add annotations.jar (available in your IDEA distribution) as a library. " +
                                        " The IDEA nullity annotations are freely usable and redistributable under the Apache 2.0 license.",
                               INFER_NULLITY_ANNOTATIONS);
      return;
    }
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(annotationClass);
    if (languageLevel.compareTo(LanguageLevel.JDK_1_5) < 0) {
      Messages.showErrorDialog(project, "Infer Nullity Annotations requires the project language level be set to 1.5 or greater.",
                               INFER_NULLITY_ANNOTATIONS);
      return;
    }


    if (scope.checkScopeWritable(project)) return;
    final NullityInferrer inferrer = new NullityInferrer(myAnnotateLocalVariablesCb.isSelected(), project);


    final ProgressManager progressManager = ProgressManager.getInstance();
    final int totalFiles = scope.getFileCount();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();

    if (indicator != null) {
      indicator.setText(INFER_NULLITY_ANNOTATIONS);
    }
    if (!progressManager.runProcessWithProgressSynchronously(new Runnable() {
      @Override
      public void run() {
        scope.accept(new PsiElementVisitor() {
          int myFileCount = 0;
          @Override
          public void visitFile(final PsiFile file) {
            myFileCount++;
            if (indicator != null) {
              final VirtualFile virtualFile = file.getVirtualFile();
              if (virtualFile != null) {
                indicator.setText2(ProjectUtil.calcRelativeToProjectPath(virtualFile, project));
              }
              indicator.setFraction(((double)myFileCount) / totalFiles);
            }
            if (file instanceof PsiJavaFile) {
              inferrer.collect(file);
            }
          }
        });
      }
    }, INFER_NULLITY_ANNOTATIONS, true, project)) return;

    final Runnable applyRunnable = new Runnable() {
      @Override
      public void run() {
        new WriteCommandAction(project, INFER_NULLITY_ANNOTATIONS) {
          @Override
          protected void run(Result result) throws Throwable {
            inferrer.apply(project);
          }
        }.execute();
      }
    };
    SwingUtilities.invokeLater(applyRunnable);
  }


  @Override
  protected JComponent getAdditionalActionSettings(Project project, BaseAnalysisActionDialog dialog) {
    final JPanel panel = new JPanel(new VerticalFlowLayout());
    panel.add(new TitledSeparator());
    myAnnotateLocalVariablesCb = new JCheckBox("Annotate local variables", false);
    panel.add(myAnnotateLocalVariablesCb);
    return panel;
  }

  @Override
  protected void canceled() {
    super.canceled();
    myAnnotateLocalVariablesCb = null;
  }
}
