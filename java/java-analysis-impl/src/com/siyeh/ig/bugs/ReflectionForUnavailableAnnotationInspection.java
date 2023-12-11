/*
 * Copyright 2006-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.ChangeAnnotationParameterQuickFix;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ReflectionForUnavailableAnnotationInspection extends BaseInspection {

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    if (infos.length == 1) {
      PsiClass annotationClass = (PsiClass)infos[0];
      String text = getText(annotationClass);
      return LocalQuickFix.from(new SetRuntimeRetentionFix(text, annotationClass));
    }
    else if (infos.length == 2) {
      PsiAnnotation retentionAnnotation = (PsiAnnotation)infos[1];
      String text = getText((PsiClass)infos[0]);
      String runtimeRef = StringUtil.getQualifiedName("java.lang.annotation.RetentionPolicy", "RUNTIME");
      return LocalQuickFix.from(
        new ChangeAnnotationParameterQuickFix(retentionAnnotation, PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME, runtimeRef, text));
    }
    assert false;
    return null;
  }
  
  private static class SetRuntimeRetentionFix extends PsiUpdateModCommandAction<PsiClass> {
    @IntentionName private final String myName;

    private SetRuntimeRetentionFix(@IntentionName String name, @NotNull PsiClass psiClass) { 
      super(psiClass);
      myName = name; 
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiClass element) {
      return Presentation.of(myName);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaAnalysisBundle.message("inspection.i18n.quickfix.annotate");
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiClass psiClass, @NotNull ModPsiUpdater updater) {
      String runtimeRef = StringUtil.getQualifiedName("java.lang.annotation.RetentionPolicy", "RUNTIME");
      PsiAnnotation newAnnotation = JavaPsiFacade.getElementFactory(context.project())
        .createAnnotationFromText("@Retention(" + runtimeRef + ")", psiClass);
      PsiModifierList list = psiClass.getModifierList();
      if (list == null) return;
      PsiAnnotation annotation = AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(
        CommonClassNames.JAVA_LANG_ANNOTATION_RETENTION, newAnnotation.getParameterList().getAttributes(), list);
      if (annotation != null) {
        JavaCodeStyleManager.getInstance(context.project()).shortenClassReferences(annotation);
      }
    }
  }

  @IntentionName
  private static String getText(@NotNull PsiClass aClass) {
    return JavaAnalysisBundle.message("inspection.i18n.quickfix.annotate.element.as", "annotation", aClass.getName(),
                                      "Retention(RetentionPolicy.RUNTIME)");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("reflection.for.unavailable.annotation.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReflectionForUnavailableAnnotationVisitor();
  }

  private static class ReflectionForUnavailableAnnotationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"isAnnotationPresent".equals(methodName) && !"getAnnotation".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] args = argumentList.getExpressions();
      if (args.length != 1) {
        return;
      }
      final PsiExpression arg = args[0];
      if (arg == null) {
        return;
      }
      if (!(arg instanceof PsiClassObjectAccessExpression classObjectAccessExpression)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, "java.lang.reflect.AnnotatedElement")) {
        return;
      }
      final PsiTypeElement operand = classObjectAccessExpression.getOperand();

      final PsiClass annotationClass = PsiTypesUtil.getPsiClass(operand.getType());
      if (annotationClass == null) {
        return;
      }
      final PsiModifierList modifierList = annotationClass.getModifierList();
      if (modifierList == null) {
        return;
      }
      final PsiAnnotation retentionAnnotation = modifierList.findAnnotation(CommonClassNames.JAVA_LANG_ANNOTATION_RETENTION);
      if (retentionAnnotation == null) {
        if (annotationClass.isWritable()) {
          registerError(arg, annotationClass);
        }
        return;
      }
      final PsiAnnotationParameterList parameters = retentionAnnotation.getParameterList();
      final PsiNameValuePair[] attributes = parameters.getAttributes();
      for (PsiNameValuePair attribute : attributes) {
        @NonNls final String name = attribute.getName();
        if (name != null && !PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(name)) {
          continue;
        }
        final PsiAnnotationMemberValue value = attribute.getValue();
        if (value == null) {
          continue;
        }
        @NonNls final String text = value.getText();
        if (!text.contains("RUNTIME")) {
          if (annotationClass.isWritable()) {
            registerError(arg, annotationClass, retentionAnnotation);
          }
          return;
        }
      }
    }
  }
}