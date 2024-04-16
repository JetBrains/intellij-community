// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class CreateFieldFromUsageFix extends CreateVarFromUsageFix {
  public CreateFieldFromUsageFix(@NotNull PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  @Override
  protected String getText(String varName) {
    return CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.FIELD.object(), varName);
  }

  @Override
  protected @NotNull List<PsiClass> getTargetClasses(PsiElement element) {
    final List<PsiClass> targetClasses = new ArrayList<>();
    for (PsiClass psiClass : super.getTargetClasses(element)) {
      if (canModify(psiClass) &&
          (!psiClass.isInterface() && !psiClass.isAnnotationType() && !psiClass.isRecord()
           || shouldCreateStaticMember(myReferenceExpression, psiClass))) {
        PsiElement target = myReferenceExpression.resolve();
        if (!(target instanceof PsiField field) || field.getContainingClass() != psiClass) {
          targetClasses.add(psiClass);
        }
      }
    }
    return targetClasses;
  }

  @Override
  protected boolean canBeTargetClass(PsiClass psiClass) {
    return canModify(psiClass) && !psiClass.isInterface() && !psiClass.isAnnotationType() && !psiClass.isRecord();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    chooseTargetClass(project, editor, this::invokeImpl);
  }

  private void invokeImpl(@NotNull PsiClass targetClass) {
    final Project project = myReferenceExpression.getProject();
    JVMElementFactory factory = JVMElementFactories.getFactory(targetClass.getLanguage(), project);
    if (factory == null) factory = JavaPsiFacade.getElementFactory(project);

    PsiMember enclosingContext = null;
    PsiClass parentClass;
    do {
      enclosingContext = PsiTreeUtil.getParentOfType(enclosingContext == null ? myReferenceExpression : enclosingContext, PsiMethod.class,
                                                     PsiField.class, PsiClassInitializer.class);
      parentClass = enclosingContext == null ? null : enclosingContext.getContainingClass();
    }
    while (parentClass instanceof PsiAnonymousClass);

    ExpectedTypeInfo[] expectedTypes = CreateFromUsageUtils.guessExpectedTypes(myReferenceExpression, false);

    String fieldName = myReferenceExpression.getReferenceName();
    assert fieldName != null;

    PsiField field = factory.createField(fieldName, PsiTypes.intType());

    if (!targetClass.isInterface() && shouldCreateStaticMember(myReferenceExpression, targetClass)) {
      PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
    }
    if (shouldCreateFinalMember(myReferenceExpression, targetClass)) {
      PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
    }

    field = CreateFieldFromUsageHelper.insertField(targetClass, field, myReferenceExpression);

    setupVisibility(parentClass, targetClass, field.getModifierList());

    createFieldFromUsageTemplate(targetClass, project, expectedTypes, field, false, myReferenceExpression);
  }

  public static void createFieldFromUsageTemplate(final PsiClass targetClass,
                                                  final Project project,
                                                  final Object expectedTypes,
                                                  final PsiField field,
                                                  final boolean createConstantField,
                                                  final PsiElement context) {
    final PsiFile targetFile = targetClass.getContainingFile();
    final Editor newEditor = CodeInsightUtil.positionCursor(project, targetFile, field);
    if (newEditor == null) return;
    Template template =
      CreateFieldFromUsageHelper.setupTemplate(field, expectedTypes, targetClass, newEditor, context, createConstantField);

    startTemplate(newEditor, template, project, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(@NotNull Template template, boolean brokenOff) {
        PsiDocumentManager.getInstance(project).commitDocument(newEditor.getDocument());
        final int offset = newEditor.getCaretModel().getOffset();
        final PsiField psiField = PsiTreeUtil.findElementOfClassAtOffset(targetFile, offset, PsiField.class, false);
        if (psiField != null) {
          ApplicationManager.getApplication().runWriteAction(() -> {
            CodeStyleManager.getInstance(project).reformat(psiField);
          });
          newEditor.getCaretModel().moveToOffset(psiField.getTextRange().getEndOffset() - 1);
        }
      }
    });
  }

  public static boolean shouldCreateFinalMember(@NotNull PsiReferenceExpression ref, @NotNull PsiClass targetClass) {
    if (!PsiTreeUtil.isAncestor(targetClass, ref, true)) {
      return false;
    }
    final PsiElement element = PsiTreeUtil.getParentOfType(ref, PsiClassInitializer.class, PsiMethod.class, PsiField.class);
    if (element instanceof PsiClassInitializer) {
      return true;
    }

    if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
      return true;
    }

    return false;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("create.field.from.usage.family");
  }
}
