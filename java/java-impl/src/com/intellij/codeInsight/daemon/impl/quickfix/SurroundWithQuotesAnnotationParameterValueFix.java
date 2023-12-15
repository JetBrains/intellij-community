// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class SurroundWithQuotesAnnotationParameterValueFix extends PsiUpdateModCommandAction<PsiAnnotationMemberValue> {
  private final PsiType myExpectedType;

  public SurroundWithQuotesAnnotationParameterValueFix(final PsiAnnotationMemberValue value, final PsiType expectedType) {
    super(value);
    myExpectedType = expectedType;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiAnnotationMemberValue value) {
    if (!myExpectedType.isValid()) return null;
    final PsiClass resolvedType = PsiUtil.resolveClassInType(myExpectedType);
    if (resolvedType == null || !CommonClassNames.JAVA_LANG_STRING.equals(resolvedType.getQualifiedName())) return null;
    if (!(value instanceof PsiLiteralExpression) && (!(value instanceof PsiReferenceExpression ref) || ref.resolve() != null)) {
      return null;
    }
    return Presentation.of(getFamilyName());
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiAnnotationMemberValue value, @NotNull ModPsiUpdater updater) {
    String newText = value.getText();
    newText = StringUtil.unquoteString(newText);
    newText = "\"" + newText + "\"";
    PsiElement newToken = JavaPsiFacade.getElementFactory(context.project()).createExpressionFromText(newText, null);
    final PsiElement newElement = value.replace(newToken);
    updater.moveCaretTo(newElement.getTextOffset() + newElement.getTextLength());
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("surround.annotation.parameter.value.with.quotes");
  }

  public static void register(@NotNull QuickFixActionRegistrar registrar,
                              @NotNull PsiJavaCodeReferenceElement ref) {
    if (ref instanceof PsiReferenceExpression) {
      if (ref.getParent() instanceof PsiNameValuePair nameValuePair && nameValuePair.getValue() == ref) {
        PsiReference reference = nameValuePair.getReference();
        if (reference != null && reference.resolve() instanceof PsiMethod annotationMethod) {
          PsiType returnType = annotationMethod.getReturnType();
          if (returnType != null) {
            registrar.register(new SurroundWithQuotesAnnotationParameterValueFix((PsiReferenceExpression)ref, returnType).asIntention());
          }
        }
      }
    }
  }
}
