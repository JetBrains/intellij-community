/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ChangeParameterClassFix extends ExtendsListFix {
  public ChangeParameterClassFix(@NotNull PsiClass aClassToExtend, @NotNull PsiClassType parameterClass) {
    super(aClassToExtend, parameterClass, true);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("change.parameter.class.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    PsiClass classToExtendFrom = myClassToExtendFromPointer != null ? myClassToExtendFromPointer.getElement() : null;

    return
      super.isAvailable(project, file, startElement, endElement)
      && classToExtendFrom != null
      && classToExtendFrom.isValid()
      && classToExtendFrom.getQualifiedName() != null
      ;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    ApplicationManager.getApplication().runWriteAction(
      () -> invokeImpl(myClass)
    );
    final Editor editor1 = CodeInsightUtil.positionCursorAtLBrace(project, myClass.getContainingFile(), myClass);
    if (editor1 == null) return;
    final Collection<CandidateInfo> toImplement = OverrideImplementExploreUtil.getMethodsToOverrideImplement(myClass, true);
    if (!toImplement.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        ApplicationManager.getApplication().runWriteAction(
          () -> {
            Collection<PsiMethodMember> members =
              ContainerUtil.map2List(toImplement, s -> new PsiMethodMember(s));
            OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor1, myClass, members, false);
          });
      }
      else {
        //SCR 12599
        editor1.getCaretModel().moveToOffset(myClass.getTextRange().getStartOffset());

        OverrideImplementUtil.chooseAndImplementMethods(project, editor1, myClass);
      }
    }
    UndoUtil.markPsiFileForUndo(file);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
