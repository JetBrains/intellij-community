// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java;

import com.intellij.ide.ui.UISettings;
import com.intellij.lang.Language;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.util.PsiExpressionTrimRenderer;
import com.intellij.refactoring.util.RefactoringDescriptionLocation;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.intellij.usageView.UsageViewShortNameLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.*;
import static com.intellij.psi.PsiNameHelper.getShortClassName;

/**
 * @author gregsh
 */
public class JavaBreadcrumbsInfoProvider implements BreadcrumbsProvider {
  private static final Language[] ourLanguages = {JavaLanguage.INSTANCE};
  @Override
  public Language[] getLanguages() {
    return ourLanguages;
  }

  @Override
  public boolean acceptElement(@NotNull PsiElement e) {
    return e instanceof PsiMember || e instanceof PsiLambdaExpression;
  }

  @NotNull
  @Override
  public String getElementInfo(@NotNull PsiElement e) {
    if (e instanceof PsiLambdaExpression) {
      return PsiExpressionTrimRenderer.render((PsiExpression)e);
    }
    else if (e instanceof PsiAnonymousClass) {
      String name = ((PsiAnonymousClass)e).getBaseClassReference().getReferenceName();
      return "new " + notNullize(name, "class");
    }
    String description = ElementDescriptionUtil.getElementDescription(e, UsageViewShortNameLocation.INSTANCE);
    String suffix = e instanceof PsiParameterListOwner? "()" : null;
    return suffix != null ? description + suffix : description;
  }

  @Nullable
  @Override
  public String getElementTooltip(@NotNull PsiElement e) {
    if (e instanceof PsiLambdaExpression) return getLambdaDescription((PsiLambdaExpression)e);
    if (e instanceof PsiMethod) return getMethodPresentableText((PsiMethod)e);
    return ElementDescriptionUtil.getElementDescription(e, RefactoringDescriptionLocation.WITH_PARENT);
  }

  @NotNull
  private static String getMethodPresentableText(PsiMethod e) {
    boolean isDumb = DumbService.isDumb(e.getProject());
    StringBuilder sb = new StringBuilder(e.isConstructor() ? "constructor" : "method");
    PsiType type = e.getReturnType();
    if (type != null) {
      sb.append(" ").append(htmlEmphasize(getTypeText(type, isDumb)));
    }
    sb.append(" ").append(htmlEmphasize(e.getName()));
    appendParameters(e, sb, false, isDumb);
    return sb.toString();
  }

  @NotNull
  private static String getLambdaDescription(@NotNull PsiLambdaExpression e) {
    boolean isDumb = DumbService.isDumb(e.getProject());
    StringBuilder sb = new StringBuilder("lambda");
    PsiType functionalInterfaceType = isDumb ? null : e.getFunctionalInterfaceType();
    if (functionalInterfaceType != null) {
      sb.append(" ").append(htmlEmphasize(getTypeText(functionalInterfaceType, false)));
    }
    appendParameters(e, sb, true, isDumb);
    return sb.toString();
  }

  private static void appendParameters(@NotNull PsiParameterListOwner e, StringBuilder sb, boolean skipIfEmpty, boolean isDumb) {
    PsiParameter[] parameters = e.getParameterList().getParameters();
    if (parameters.length == 0 && skipIfEmpty) return;
    if (skipIfEmpty) sb.append(" ");
    sb.append("(");
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) sb.append(", ");
      String typeStr;
      if (isDumb) {
        PsiTypeElement typeElement = parameters[i].getTypeElement();
        typeStr = typeElement == null ? "" : getShortClassName(typeElement.getText());
      }
      else {
        typeStr = getTypeText(parameters[i].getType(), false);
      }
      String str = isEmpty(typeStr)? parameters[i].getName() : getShortClassName(typeStr);
      sb.append(htmlEmphasize(str));
    }
    sb.append(")");
  }

  private static @NotNull @NlsSafe String getTypeText(@Nullable PsiType type, boolean isDumb) {
    // todo PsiTypeVisitor ?
    String result;
    if (type == null) result = "";
    else if (!isDumb || type instanceof PsiPrimitiveType) result = type.getCanonicalText(false);
    else if (type instanceof PsiClassReferenceType) result = ((PsiClassReferenceType)type).getReference().getReferenceName();
    else result = "";
    return getShortClassName(notNullize(result));
  }

  @Override
  public boolean isShownByDefault() {
    return !UISettings.getInstance().getShowMembersInNavigationBar();
  }
}
