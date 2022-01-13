// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class AddAnnotationTargetFix implements IntentionAction {
  @NotNull private final PsiAnnotation myAnnotation;
  @NotNull private final PsiAnnotation.TargetType myTarget;

  public AddAnnotationTargetFix(@NotNull PsiAnnotation annotation, @NotNull PsiAnnotation.TargetType target) {
    myAnnotation = annotation;
    myTarget = target;
  }

  @Override
  public @NotNull String getText() {
    return QuickFixBundle.message("add.annotation.target.fix", myTarget);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("add.annotation.target.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiClass annotationType = myAnnotation.resolveAnnotationType();
    if (annotationType == null) return;
    final PsiModifierList modifierList = annotationType.getModifierList();
    if (modifierList == null) return;
    final PsiAnnotation annotation = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_TARGET);
    if (annotation == null) return;
    final PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(annotation, null);
    if (attribute == null) return;
    PsiAnnotationMemberValue value = attribute.getValue();
    if (value == null) return;
    if (!(value instanceof PsiArrayInitializerMemberValue)) {
      PsiAnnotation dummyAnnotation =
        JavaPsiFacade.getElementFactory(project).createAnnotationFromText("@A({" + value.getText() + "})", null);
      final PsiAnnotationMemberValue wrappedValue = dummyAnnotation.getParameterList().getAttributes()[0].getValue();
      value = annotation.setDeclaredAttributeValue("value", wrappedValue);
    }
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final PsiExpression expression  = factory.createExpressionFromText("java.lang.annotation.ElementType." + myTarget, value);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(expression);
    value.addAfter(expression, value.getFirstChild());
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
