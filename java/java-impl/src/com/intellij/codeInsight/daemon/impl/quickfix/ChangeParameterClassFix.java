// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class ChangeParameterClassFix extends ExtendsListFix implements LowPriorityAction {
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
    if (!FileModificationService.getInstance().prepareFileForWrite(startElement.getContainingFile())) return;
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
            Collection<PsiMethodMember> members = ContainerUtil.map(toImplement, PsiMethodMember::new);
            OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor1, myClass, members, false);
          });
      }
      else {
        //SCR 12599
        editor1.getCaretModel().moveToOffset(myClass.getTextRange().getStartOffset());

        OverrideImplementUtil.chooseAndImplementMethods(project, editor1, myClass);
      }
    }
    UndoUtil.markPsiFileForUndo(startElement.getContainingFile());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiClass aClass = (PsiClass)getStartElement();
    PsiFile fileCopy = (PsiFile)aClass.getContainingFile().copy();
    PsiClass classCopy = PsiTreeUtil.findSameElementInCopy(aClass, fileCopy);
    invokeImpl(classCopy);
    Collection<CandidateInfo> overrideImplement = OverrideImplementExploreUtil.getMethodsToOverrideImplement(classCopy, true);
    List<PsiMethodMember> toImplement = ContainerUtil.map(
      ContainerUtil.filter(overrideImplement,
                           t -> t.getElement() instanceof PsiMethod method && !method.hasModifierProperty(PsiModifier.DEFAULT)),
      PsiMethodMember::new
    );
    if (!toImplement.isEmpty()) {
      boolean insertOverrideAnnotation = JavaCodeStyleSettings.getInstance(file).INSERT_OVERRIDE_ANNOTATION;
      var prototypes = OverrideImplementUtil.overrideOrImplementMethods(classCopy, toImplement, false, insertOverrideAnnotation);
      PsiElement brace = classCopy.getRBrace();
      if (brace == null) return IntentionPreviewInfo.EMPTY;
      GenerateMembersUtil.insertMembersAtOffset(classCopy, brace.getTextOffset(), prototypes);
      CodeStyleManager.getInstance(project).reformat(classCopy);
    }
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, aClass.getContainingFile().getText(), classCopy.getContainingFile().getText());
  }
}
