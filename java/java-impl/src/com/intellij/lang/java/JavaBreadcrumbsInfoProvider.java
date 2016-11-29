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
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.refactoring.util.RefactoringDescriptionLocation;
import com.intellij.usageView.UsageViewShortNameLocation;
import com.intellij.xml.breadcrumbs.BreadcrumbsInfoProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.util.text.StringUtil.*;
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
      boolean isDumb = DumbService.isDumb(e.getProject());
      PsiType type = isDumb ? null : ((PsiFunctionalExpression)e).getFunctionalInterfaceType();
      return type == null ? "->" : "-> " + getTypeText(type, false);
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
      String str = isEmpty(typeStr)? notNullize(parameters[i].getName()) : getShortClassName(typeStr);
      sb.append(htmlEmphasize(str));
    }
    sb.append(")");
  }

  @NotNull
  private static String getTypeText(@Nullable PsiType type, boolean isDumb) {
    // todo PsiTypeVisitor ?
    String result;
    if (type == null) result = "";
    else if (!isDumb || type instanceof PsiPrimitiveType) result = type.getCanonicalText(false);
    else if (type instanceof PsiClassReferenceType) result = ((PsiClassReferenceType)type).getReference().getReferenceName();
    else result = "";
    return getShortClassName(notNullize(result));
  }
}
