/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.lang.java;

import com.intellij.lang.Language;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.util.RefactoringDescriptionLocation;
import com.intellij.usageView.UsageViewShortNameLocation;
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.htmlEmphasize;
import static com.intellij.psi.PsiNameHelper.getShortClassName;

/**
 * @author gregsh
 */
public class JavaBreadcrumbsInfoProvider extends BreadcrumbsInfoProvider {
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
      PsiType type = DumbService.isDumb(e.getProject()) ? null : ((PsiFunctionalExpression)e).getFunctionalInterfaceType();
      return type == null ? "<lambda>" : "lambda " + getShortClassName(type.getCanonicalText());
    }
    String description = ElementDescriptionUtil.getElementDescription(e, UsageViewShortNameLocation.INSTANCE);
    String suffix = e instanceof PsiParameterListOwner? "()" :
                    //e instanceof PsiAnonymousClass || e instanceof PsiClassInitializer ? " {}" :
                    null;
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
      String shortClassName = getShortClassName(type.getCanonicalText(false));
      sb.append(" ").append(htmlEmphasize(shortClassName));
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
      String shortClassName = getShortClassName(functionalInterfaceType.getCanonicalText(false));
      sb.append(" ").append(htmlEmphasize(shortClassName));
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
        typeStr = typeElement == null ? "" : typeElement.getText();
      }
      else {
        typeStr = parameters[i].getType().getCanonicalText(false);
      }
      String str = getShortClassName(typeStr);
      if (StringUtil.isEmpty(str)) str = StringUtil.notNullize(parameters[i].getName());
      sb.append(htmlEmphasize(str));
    }
    sb.append(")");
  }
}
