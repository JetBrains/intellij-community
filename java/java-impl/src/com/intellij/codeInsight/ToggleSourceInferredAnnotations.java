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
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.javadoc.AnnotationDocGenerator;
import com.intellij.openapi.command.undo.BasicUndoableAction;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.ExternalAnnotationsLineMarkerProvider.getAnnotationOwner;

/**
 * @author peter
 */
public class ToggleSourceInferredAnnotations extends BaseIntentionAction implements LowPriorityAction {

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "Show/Hide Gutter Icon for Annotations Inferred from Source Code";
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, Editor editor, PsiFile file) {
    final PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
    final PsiModifierListOwner owner = getAnnotationOwner(leaf);
    if (owner != null) {
      boolean hasSrcInferredAnnotation = ContainerUtil.exists(AnnotationDocGenerator.getAnnotationsToShow(owner),
                                                              AnnotationDocGenerator::isInferredFromSource);
      if (!hasSrcInferredAnnotation) {
        if (owner instanceof PsiMethod) {
          hasSrcInferredAnnotation = StreamEx.of(((PsiMethod)owner).getParameterList().getParameters())
                                             .flatCollection(AnnotationDocGenerator::getAnnotationsToShow)
                                             .anyMatch(AnnotationDocGenerator::isInferredFromSource);
        }
      }
      if (hasSrcInferredAnnotation) {
        setText((CodeInsightSettings.getInstance().SHOW_SOURCE_INFERRED_ANNOTATIONS ? "Hide" : "Show") +
                " gutter icon for annotations inferred from source code");
        return true;
      }
    }
    
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    boolean showAnnotations = !CodeInsightSettings.getInstance().SHOW_SOURCE_INFERRED_ANNOTATIONS;
    showAnnotations(project, file, showAnnotations);
    UndoManager.getInstance(project).undoableActionPerformed(new BasicUndoableAction(file.getVirtualFile()) {
      @Override
      public void undo() {
        showAnnotations(project, file, !showAnnotations);
      }

      @Override
      public void redo() {
        showAnnotations(project, file, showAnnotations);
      }
    });
  }

  private static void showAnnotations(@NotNull Project project, PsiFile file, boolean showAnnotations) {
    CodeInsightSettings.getInstance().SHOW_SOURCE_INFERRED_ANNOTATIONS = showAnnotations;
    DaemonCodeAnalyzer.getInstance(project).restart(file);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
