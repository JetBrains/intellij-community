// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
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
import org.jetbrains.annotations.PropertyKey;

import java.util.Collection;
import java.util.List;

import static java.util.Objects.requireNonNull;

public class ChangeParameterClassFix extends LocalQuickFixAndIntentionActionOnPsiElement implements LowPriorityAction {
  protected final @Nullable SmartPsiElementPointer<PsiClass> myClassToExtendFromPointer;
  private final PsiClassType myTypeToExtendFrom;
  private final @IntentionName String myName;

  public ChangeParameterClassFix(@NotNull PsiClass aClassToExtend, @NotNull PsiClassType parameterClass) {
    super(aClassToExtend);
    @Nullable PsiClass classToExtendFrom = parameterClass.resolve();
    myClassToExtendFromPointer = classToExtendFrom == null ? null : SmartPointerManager.createPointer(classToExtendFrom);
    myTypeToExtendFrom = aClassToExtend instanceof PsiTypeParameter ? parameterClass
                                                                    : (PsiClassType)GenericsUtil.eliminateWildcards(parameterClass);

    @PropertyKey(resourceBundle = QuickFixBundle.BUNDLE) String messageKey;
    if (classToExtendFrom != null && aClassToExtend.isInterface() == classToExtendFrom.isInterface() ||
        aClassToExtend instanceof PsiTypeParameter) {
      messageKey = "add.class.to.extends.list";
    }
    else {
      messageKey = "add.interface.to.implements.list";
    }

    myName = QuickFixBundle.message(messageKey, aClassToExtend.getName(), classToExtendFrom == null
                                                                          ? ""
                                                                          : classToExtendFrom instanceof PsiTypeParameter
                                                                            ? classToExtendFrom.getName()
                                                                            : classToExtendFrom.getQualifiedName());
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("change.parameter.class.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    PsiClass classToExtendFrom = myClassToExtendFromPointer != null ? myClassToExtendFromPointer.getElement() : null;
    final PsiClass myClass = (PsiClass)startElement;

    return myTypeToExtendFrom.isValid() && BaseIntentionAction.canModify(myClass) &&
           startElement.isPhysical() &&
           classToExtendFrom != null &&
           !classToExtendFrom.hasModifierProperty(PsiModifier.FINAL) &&
           (classToExtendFrom.isInterface() ||
            !myClass.isInterface() && myClass.getExtendsList() != null && (myClass.getExtendsList().getReferencedTypes().length == 0)) &&
           classToExtendFrom.getQualifiedName() != null;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(startElement.getContainingFile())) return;
    PsiClass classToExtendFrom = requireNonNull(myClassToExtendFromPointer).getElement();
    ApplicationManager.getApplication().runWriteAction(
      () -> new ExtendsListModCommandFix(myClass, classToExtendFrom, myTypeToExtendFrom, true).invokeImpl(myClass)
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
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiClass aClass = (PsiClass)getStartElement();
    PsiFile psiFileCopy = (PsiFile)aClass.getContainingFile().copy();
    PsiClass classCopy = PsiTreeUtil.findSameElementInCopy(aClass, psiFileCopy);
    PsiClass classToExtendFrom = requireNonNull(myClassToExtendFromPointer).getElement();
    new ExtendsListModCommandFix(classCopy, classToExtendFrom, myTypeToExtendFrom, true).invokeImpl(classCopy);
    Collection<CandidateInfo> overrideImplement = OverrideImplementExploreUtil.getMethodsToOverrideImplement(classCopy, true);
    List<PsiMethodMember> toImplement = ContainerUtil.map(
      ContainerUtil.filter(overrideImplement,
                           t -> t.getElement() instanceof PsiMethod method && !method.hasModifierProperty(PsiModifier.DEFAULT)),
      PsiMethodMember::new
    );
    if (!toImplement.isEmpty()) {
      boolean insertOverrideAnnotation = JavaCodeStyleSettings.getInstance(psiFile).INSERT_OVERRIDE_ANNOTATION;
      var prototypes = OverrideImplementUtil.overrideOrImplementMethods(classCopy, toImplement, false, insertOverrideAnnotation);
      PsiElement brace = classCopy.getRBrace();
      if (brace == null) return IntentionPreviewInfo.EMPTY;
      GenerateMembersUtil.insertMembersAtOffset(classCopy, brace.getTextOffset(), prototypes);
      CodeStyleManager.getInstance(project).reformat(classCopy);
    }
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE, aClass.getContainingFile().getText(), classCopy.getContainingFile().getText());
  }

  @Override
  public @NotNull String getText() {
    return myName;
  }
}
