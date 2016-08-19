/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.ExpectedTypeInfo;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateEditingAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Mike
 */
public class CreateFieldFromUsageFix extends CreateVarFromUsageFix {
  public CreateFieldFromUsageFix(@NotNull PsiReferenceExpression referenceElement) {
    super(referenceElement);
  }

  @Override
  protected String getText(String varName) {
    return QuickFixBundle.message("create.field.from.usage.text", varName);
  }

  protected boolean createConstantField() {
    return false;
  }

  @NotNull
  @Override
  protected List<PsiClass> getTargetClasses(PsiElement element) {
    final List<PsiClass> targetClasses = new ArrayList<>();
    for (PsiClass psiClass : super.getTargetClasses(element)) {
      if (psiClass.getManager().isInProject(psiClass) && 
          (!psiClass.isInterface() && !psiClass.isAnnotationType() || shouldCreateStaticMember(myReferenceExpression, psiClass))) {
        targetClasses.add(psiClass);
      }
    }
    return targetClasses;
  }

  @Override
  protected boolean canBeTargetClass(PsiClass psiClass) {
    return psiClass.getManager().isInProject(psiClass) && !psiClass.isInterface() && !psiClass.isAnnotationType();
  }

  @Override
  protected void invokeImpl(final PsiClass targetClass) {
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

    PsiField field = factory.createField(fieldName, PsiType.INT);
    if (createConstantField()) {
      PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
    }

    if (createConstantField()) {
      PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
      PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
    } else {
      if (!targetClass.isInterface() && shouldCreateStaticMember(myReferenceExpression, targetClass)) {
        PsiUtil.setModifierProperty(field, PsiModifier.STATIC, true);
      }
      if (shouldCreateFinalMember(myReferenceExpression, targetClass)) {
        PsiUtil.setModifierProperty(field, PsiModifier.FINAL, true);
      }
    }

    field = CreateFieldFromUsageHelper.insertField(targetClass, field, myReferenceExpression);

    setupVisibility(parentClass, targetClass, field.getModifierList());

    createFieldFromUsageTemplate(targetClass, project, expectedTypes, field, createConstantField(), myReferenceExpression);
  }

  public static void createFieldFromUsageTemplate(final PsiClass targetClass,
                                                  final Project project,
                                                  final ExpectedTypeInfo[] expectedTypes,
                                                  final PsiField field,
                                                  final boolean createConstantField,
                                                  final PsiElement context) {
    final PsiFile targetFile = targetClass.getContainingFile();
    final Editor newEditor = positionCursor(project, targetFile, field);
    if (newEditor == null) return;
    Template template =
      CreateFieldFromUsageHelper.setupTemplate(field, expectedTypes, targetClass, newEditor, context, createConstantField);

    startTemplate(newEditor, template, project, new TemplateEditingAdapter() {
      @Override
      public void templateFinished(Template template, boolean brokenOff) {
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

  private static boolean shouldCreateFinalMember(@NotNull PsiReferenceExpression ref, @NotNull PsiClass targetClass) {
    if (!PsiTreeUtil.isAncestor(targetClass, ref, true)) {
      return false;
    }
    final PsiElement element = PsiTreeUtil.getParentOfType(ref, PsiClassInitializer.class, PsiMethod.class);
    if (element instanceof PsiClassInitializer) {
      return true;
    }

    if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
      return true;
    }

    return false;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("create.field.from.usage.family");
  }
}
