// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;

public class AddAnnotationTargetFix extends PsiBasedModCommandAction<PsiAnnotation> {
  private final @NotNull PsiAnnotation.TargetType myTarget;

  public AddAnnotationTargetFix(@NotNull PsiAnnotation annotation, @NotNull PsiAnnotation.TargetType target) {
    super(annotation);
    myTarget = target;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("make.annotation.applicable.to.0.fix",
                                  StringUtil.toLowerCase(myTarget.toString()).replace('_', ' ') + 's');
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiAnnotation annotation) {
    PsiClass origAnnoType = annotation.resolveAnnotationType();
    if (origAnnoType == null) return ModCommand.nop();
    return ModCommand.psiUpdate(origAnnoType, annotationType -> {
      PsiModifierList modifierList = annotationType.getModifierList();
      if (modifierList == null) return;
      PsiAnnotation targetAnnotation = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_TARGET);
      String targetText = "java.lang.annotation.ElementType." + myTarget;
      Project project = context.project();
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      if (targetAnnotation == null) {
        targetAnnotation = modifierList.addAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_TARGET);
      }
      PsiExpression expression = factory.createExpressionFromText(targetText, targetAnnotation);
      PsiNameValuePair attribute = AnnotationUtil.findDeclaredAttribute(targetAnnotation, null);
      PsiAnnotationMemberValue value = attribute == null ? null : attribute.getValue();
      if (value == null) {
        targetAnnotation.setDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, expression);
      } else {
        if (!(value instanceof PsiArrayInitializerMemberValue)) {
          PsiAnnotation dummyAnnotation = factory.createAnnotationFromText("@A({" + value.getText() + "})", null);
          PsiAnnotationMemberValue dummyValue = dummyAnnotation.getParameterList().getAttributes()[0].getValue();
          value = targetAnnotation.setDeclaredAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, dummyValue);
        }
        value.addAfter(expression, value.getFirstChild());
      }
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(targetAnnotation);
    });
  }
}
