/*
 * Copyright 2006-2019s Bas Leijdekkers
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TypeParameterExtendsFinalClassInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final Integer problemType = (Integer)infos[1];
    final PsiClass aClass = (PsiClass)infos[0];
    final String name = aClass.getName();
    if (problemType.intValue() == 1) {
      return InspectionGadgetsBundle.message(aClass.isEnum()
                                             ? "type.parameter.extends.enum.type.parameter.problem.descriptor"
                                             : "type.parameter.extends.final.class.type.parameter.problem.descriptor", name);
    }
    else {
      return InspectionGadgetsBundle.message(aClass.isEnum()
                                             ? "type.parameter.extends.enum.wildcard.problem.descriptor"
                                             : "type.parameter.extends.final.class.wildcard.problem.descriptor", name);
    }
  }

  @Override
  @Nullable
  protected LocalQuickFix buildFix(Object... infos) {
    return new TypeParameterExtendsFinalClassFix();
  }

  private static class TypeParameterExtendsFinalClassFix extends PsiUpdateModCommandQuickFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("type.parameter.extends.final.class.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      if (parent instanceof PsiTypeParameter typeParameter) {
        replaceTypeParameterUsagesWithType(typeParameter);
        new CommentTracker().deleteAndRestoreComments(typeParameter);
      }
      else if (parent instanceof PsiTypeElement typeElement) {
        final PsiElement lastChild = typeElement.getLastChild();
        if (lastChild == null) {
          return;
        }
        new CommentTracker().replaceAndRestoreComments(typeElement, lastChild);
      }
    }

    private static void replaceTypeParameterUsagesWithType(PsiTypeParameter typeParameter) {
      final PsiClassType[] types = typeParameter.getExtendsList().getReferencedTypes();
      if (types.length < 1) {
        return;
      }
      final Project project = typeParameter.getProject();
      final PsiJavaCodeReferenceElement classReference = JavaPsiFacade.getElementFactory(project).createReferenceElementByType(types[0]);
      final Query<PsiReference> query = ReferencesSearch.search(typeParameter, typeParameter.getUseScope());
      for (PsiReference reference : query) {
        final PsiElement referenceElement = reference.getElement();
        referenceElement.replace(classReference);
      }
    }
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new TypeParameterExtendsFinalClassVisitor();
  }

  private static class TypeParameterExtendsFinalClassVisitor extends BaseInspectionVisitor {
    @Override
    public void visitTypeParameter(@NotNull PsiTypeParameter classParameter) {
      super.visitTypeParameter(classParameter);
      final PsiClassType[] extendsListTypes = classParameter.getExtendsListTypes();
      if (extendsListTypes.length < 1) {
        return;
      }
      final PsiClassType extendsType = extendsListTypes[0];
      final PsiClass aClass = extendsType.resolve();
      if (aClass == null) {
        return;
      }
      if (!aClass.hasModifierProperty(PsiModifier.FINAL) && !aClass.isEnum()) {
        return;
      }
      final PsiIdentifier nameIdentifier = classParameter.getNameIdentifier();
      if (nameIdentifier != null) {
        registerError(nameIdentifier, aClass, Integer.valueOf(1));
      }
    }

    @Override
    public void visitTypeElement(@NotNull PsiTypeElement typeElement) {
      super.visitTypeElement(typeElement);
      final PsiType type = typeElement.getType();
      if (!(type instanceof PsiWildcardType wildcardType)) {
        return;
      }
      final PsiType extendsBound = wildcardType.getExtendsBound();
      if (!(extendsBound instanceof PsiClassType classType)) {
        return;
      }
      for (PsiType typeParameter : classType.getParameters()) {
        if (typeParameter instanceof PsiWildcardType) {
          // if nested type has wildcard type parameter too, leave it
          return;
        }
      }
      final PsiClass aClass = classType.resolve();
      if (aClass == null) {
        return;
      }
      if (!aClass.hasModifierProperty(PsiModifier.FINAL) && !aClass.isEnum()) {
        return;
      }
      if (aClass.hasTypeParameters() && !PsiUtil.isLanguageLevel8OrHigher(typeElement)) {
        final PsiType[] parameters = classType.getParameters();
        if (parameters.length == 0) {
          return;
        }
        for (PsiType parameter : parameters) {
          if (parameter instanceof PsiWildcardType) {
            return;
          }
        }
      }
      if (isWildcardRequired(typeElement)) {
        return;
      }
      registerError(typeElement.getFirstChild(), aClass, Integer.valueOf(2));
    }

    private static boolean isWildcardRequired(PsiTypeElement typeElement) {
      final PsiElement ancestor = PsiTreeUtil.skipParentsOfType(
        typeElement, PsiTypeElement.class, PsiJavaCodeReferenceElement.class, PsiReferenceParameterList.class);
      if (ancestor instanceof PsiDeconstructionPattern deconstructionPattern) {
        PsiForeachStatementBase parentForEach = PsiTreeUtil.getParentOfType(deconstructionPattern, PsiForeachStatementBase.class, false, PsiStatement.class);
        if (parentForEach == null) {
          return false;
        }
        if (ancestor.getParent() instanceof PsiForeachStatementBase foreachStatement) {
          PsiExpression iteratedValue = foreachStatement.getIteratedValue();
          if (iteratedValue == null) {
            return false;
          }
          return isWildcardRequired(typeElement, deconstructionPattern.getTypeElement(),
                                    JavaGenericsUtil.getCollectionItemType(iteratedValue));
        }
        else if (deconstructionPattern.getParent() instanceof PsiDeconstructionList) {
          PsiType type = JavaPsiPatternUtil.getDeconstructedImplicitPatternType(deconstructionPattern);
          if (type == null) {
            return false;
          }
          return isWildcardRequired(typeElement, deconstructionPattern.getTypeElement(), type);
        }
      }
      else if (ancestor instanceof PsiParameter parameter) {
        final PsiElement scope = parameter.getDeclarationScope();
        if (scope instanceof PsiMethod method) {
          if (MethodUtils.hasSuper(method)) {
            return true;
          }
        }
        else if (scope instanceof PsiLambdaExpression) {
          return true;
        }
        else if (scope instanceof PsiForeachStatementBase foreachStatement) {
          final PsiExpression iteratedValue = foreachStatement.getIteratedValue();
          if (iteratedValue == null) {
            return true; // incomplete code
          }

          if (scope instanceof PsiForeachStatement normalForeach) {
            PsiParameter iterationParameter = normalForeach.getIterationParameter();
            final PsiTypeElement foreachTypeElement = iterationParameter.getTypeElement();
            assert foreachTypeElement != null;
            return isWildcardRequired(typeElement, foreachTypeElement, JavaGenericsUtil.getCollectionItemType(iteratedValue));
          } else if (ancestor instanceof PsiPatternVariable patternVariable) {
            PsiType type = JavaPsiPatternUtil.getDeconstructedImplicitPatternVariableType(patternVariable);
            if (type == null) {
              return true;
            }
            return isWildcardRequired(typeElement, patternVariable.getTypeElement(), type);
          }
        }
      }
      else if (ancestor instanceof PsiLocalVariable localVariable) {
        final PsiExpression initializer = localVariable.getInitializer();
        return initializer != null && isWildcardRequired(typeElement, localVariable.getTypeElement(), initializer.getType());
      }
      return false;
    }

    private static boolean isWildcardRequired(PsiTypeElement innerTypeElement, PsiTypeElement completeTypeElement, PsiType rhsType) {
      final PsiType lhsType = completeTypeElement.getType();
      if (lhsType.equals(rhsType) || rhsType == null || !TypeConversionUtil.isAssignable(lhsType, rhsType)) {
        return true;
      }
      final Object marker = new Object();
      PsiTreeUtil.mark(innerTypeElement, marker);
      final PsiTypeElement copy = (PsiTypeElement)completeTypeElement.copy();
      final PsiElement markedElement = PsiTreeUtil.releaseMark(copy, marker);
      assert markedElement != null;
      markedElement.replace(markedElement.getLastChild());
      return !TypeConversionUtil.isAssignable(copy.getType(), rhsType);
    }
  }
}