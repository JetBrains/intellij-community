// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AddAnnotationTargetFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  @NotNull private final PsiAnnotation.TargetType myTarget;

  public AddAnnotationTargetFix(@NotNull PsiAnnotation annotation, @NotNull PsiAnnotation.TargetType target) {
    super(annotation);
    myTarget = target;
  }

  @Override
  public @NotNull String getText() {
    return QuickFixBundle.message("make.annotation.applicable.to.0.fix",
                                  StringUtil.toLowerCase(myTarget.toString()).replace('_', ' ') + 's');
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiClass annotationType = ((PsiAnnotation)startElement).resolveAnnotationType();
    if (annotationType == null) return;
    PsiModifierList modifierList = annotationType.getModifierList();
    if (modifierList == null) return;
    PsiAnnotation targetAnnotation = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_TARGET);
    String targetText = "java.lang.annotation.ElementType." + myTarget;
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (targetAnnotation == null) {
      final PsiAnnotation newAnnotation = factory.createAnnotationFromText("@Target(" + targetText + ")", annotationType);
      AddAnnotationPsiFix fix = new AddAnnotationPsiFix(CommonClassNames.JAVA_LANG_ANNOTATION_TARGET, annotationType,
                                                        newAnnotation.getParameterList().getAttributes());
      fix.invoke(project, file, annotationType, annotationType);
      return;
    }
    PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(targetAnnotation, null);
    if (attribute == null) return;
    PsiAnnotationMemberValue value = attribute.getValue();
    if (value == null) return;
    if (!(value instanceof PsiArrayInitializerMemberValue)) {
      PsiAnnotation dummyAnnotation = factory.createAnnotationFromText("@A({" + value.getText() + "})", null);
      PsiAnnotationMemberValue dummyValue = dummyAnnotation.getParameterList().getAttributes()[0].getValue();
      value = targetAnnotation.setDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, dummyValue);
    }
    PsiExpression expression = factory.createExpressionFromText(targetText, value);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(expression);
    value.addAfter(expression, value.getFirstChild());
  }
}
