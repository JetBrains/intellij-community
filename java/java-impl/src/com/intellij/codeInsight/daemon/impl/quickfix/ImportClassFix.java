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

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.filters.FilterPositionUtil;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ImportClassFix extends ImportClassFixBase<PsiJavaCodeReferenceElement, PsiJavaCodeReferenceElement> {
  public ImportClassFix(@NotNull PsiJavaCodeReferenceElement element) {
    super(element, element);
  }

  @Override
  protected String getReferenceName(@NotNull PsiJavaCodeReferenceElement reference) {
    return reference.getReferenceName();
  }

  @Override
  protected PsiElement getReferenceNameElement(@NotNull PsiJavaCodeReferenceElement reference) {
    return reference.getReferenceNameElement();
  }

  @Override
  protected void bindReference(PsiReference ref, PsiClass targetClass) {
    if (ref instanceof PsiImportStaticReferenceElement) {
      ((PsiImportStaticReferenceElement)ref).bindToTargetClass(targetClass);
    }
    else {
      super.bindReference(ref, targetClass);
    }
  }

  @Override
  protected boolean hasTypeParameters(@NotNull PsiJavaCodeReferenceElement reference) {
    final PsiReferenceParameterList refParameters = reference.getParameterList();
    return refParameters != null && refParameters.getTypeParameterElements().length > 0;
  }

  @Override
  protected String getQualifiedName(PsiJavaCodeReferenceElement reference) {
    return reference.getQualifiedName();
  }

  @Override
  protected boolean isQualified(PsiJavaCodeReferenceElement reference) {
    return reference.isQualified();
  }

  @Override
  protected boolean hasUnresolvedImportWhichCanImport(final PsiFile psiFile, final String name) {
    if (!(psiFile instanceof PsiJavaFile)) return false;
    PsiImportList importList = ((PsiJavaFile)psiFile).getImportList();
    if (importList == null) return false;
    PsiImportStatement[] importStatements = importList.getImportStatements();
    for (PsiImportStatement importStatement : importStatements) {
      if (importStatement.resolve() != null) continue;
      if (importStatement.isOnDemand()) return true;
      String qualifiedName = importStatement.getQualifiedName();
      String className = qualifiedName == null ? null : ClassUtil.extractClassName(qualifiedName);
      if (Comparing.strEqual(className, name)) return true;
    }
    PsiImportStaticStatement[] importStaticStatements = importList.getImportStaticStatements();
    for (PsiImportStaticStatement importStaticStatement : importStaticStatements) {
      if (importStaticStatement.resolve() != null) continue;
      if (importStaticStatement.isOnDemand()) return true;
      String qualifiedName = importStaticStatement.getReferenceName();
      // rough heuristic, since there is no API to get class name reference from static import
      if (qualifiedName != null && StringUtil.split(qualifiedName, ".").contains(name)) return true;
    }
    return false;
  }

  @Override
  protected String getRequiredMemberName(PsiJavaCodeReferenceElement reference) {
    PsiElement parent = reference.getParent();
    if (parent instanceof PsiJavaCodeReferenceElement) {
      return ((PsiJavaCodeReferenceElement)parent).getReferenceName();
    }

    return super.getRequiredMemberName(reference);
  }

  @Override
  protected boolean canReferenceClass(PsiJavaCodeReferenceElement ref) {
    if (PsiTreeUtil.getParentOfType(ref, PsiImportStatementBase.class) != null) return false;
    if (ref instanceof PsiReferenceExpression) {
      PsiElement parent = ref.getParent();
      return parent instanceof PsiReferenceExpression || parent instanceof PsiExpressionStatement;
    }
    return !inReturnTypeOfIncompleteGenericMethod(ref);
  }

  private static boolean inReturnTypeOfIncompleteGenericMethod(PsiJavaCodeReferenceElement element) {
    PsiTypeElement type = SyntaxTraverser.psiApi().parents(element).filter(PsiTypeElement.class).last();
    PsiElement prev = FilterPositionUtil.searchNonSpaceNonCommentBack(type);
    PsiTypeParameterList typeParameterList = PsiTreeUtil.getParentOfType(prev, PsiTypeParameterList.class);
    if (typeParameterList != null && typeParameterList.getParent() instanceof PsiErrorElement) {
      return Arrays.stream(typeParameterList.getTypeParameters()).anyMatch(p -> Objects.equals(element.getReferenceName(), p.getName()));
    }
    return false;
  }

  @NotNull
  @Override
  protected List<PsiClass> filterByContext(@NotNull List<PsiClass> candidates, @NotNull PsiJavaCodeReferenceElement ref) {
    if (ref instanceof PsiReferenceExpression) {
      return Collections.emptyList();
    }

    PsiElement typeElement = ref.getParent();
    if (typeElement instanceof PsiTypeElement) {
      PsiElement var = typeElement.getParent();
      if (var instanceof PsiVariable) {
        PsiExpression initializer = ((PsiVariable)var).getInitializer();
        if (initializer != null) {
          return filterAssignableFrom(initializer.getType(), candidates);
        }
      }
      if (var instanceof PsiParameter) {
        return filterBySuperMethods((PsiParameter)var, candidates);
      }
    }

    return super.filterByContext(candidates, ref);
  }

  @Override
  protected boolean isAccessible(PsiMember member, PsiJavaCodeReferenceElement reference) {
    return PsiUtil.isAccessible(member, reference, null);
  }
}
