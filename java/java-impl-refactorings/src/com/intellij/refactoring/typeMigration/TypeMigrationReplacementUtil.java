// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration;

import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author anna
 */
public final class TypeMigrationReplacementUtil {
  public static final Logger LOG = Logger.getInstance(TypeMigrationReplacementUtil.class);

  private TypeMigrationReplacementUtil() {
  }

  public static PsiElement replaceExpression(PsiExpression expression,
                                             final Project project,
                                             Object conversion,
                                             TypeEvaluator typeEvaluator) {
    if (conversion instanceof TypeConversionDescriptorBase) {
      try {
        return ((TypeConversionDescriptorBase)conversion).replace(expression, typeEvaluator);
      } catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    } else if (conversion instanceof String replacement) {
      try {
        return expression.replace(
            JavaPsiFacade.getElementFactory(project).createExpressionFromText(replacement, expression));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    else if (expression instanceof PsiReferenceExpression reference) {
      final PsiElement resolved = reference.resolve();
      final PsiMember replacer = ((PsiMember)conversion);
      final String method = ((PsiMember)resolved).getName();
      final String ref = expression.getText();
      final String newref = ref.substring(0, ref.lastIndexOf(method)) + replacer.getName();

      if (conversion instanceof PsiMethod) {
        if (resolved instanceof PsiMethod) {
          try {
            return expression.replace(
                JavaPsiFacade.getElementFactory(project).createExpressionFromText(newref, expression));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        else {
          try {
            return expression.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(
                newref + "()", expression));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
      }
      else if (conversion instanceof PsiField) {
        if (resolved instanceof PsiField) {
          try {
            return expression.replace(
                JavaPsiFacade.getElementFactory(project).createExpressionFromText(newref, expression));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        else {
          final PsiElement parent = Util.getEssentialParent(expression);

          if (parent instanceof PsiMethodCallExpression) {
            try {
              return parent.replace(
                  JavaPsiFacade.getElementFactory(project).createExpressionFromText(newref, expression));
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    }
    return expression;
  }

  static PsiType revalidateType(@NotNull PsiType migrationType, @NotNull Project project) {
    if (!migrationType.isValid()) {
      migrationType = JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(migrationType.getCanonicalText());
    }
    return migrationType;
  }

  static void migrateMemberOrVariableType(final PsiElement element, final Project project, PsiType migratedType) {
    try {
      migratedType = revalidateType(migratedType, project);
      final PsiTypeElement typeElement = JavaPsiFacade.getElementFactory(project).createTypeElement(migratedType);
      if (element instanceof PsiMethod) {
        final PsiTypeElement returnTypeElement = ((PsiMethod)element).getReturnTypeElement();
        if (returnTypeElement != null) {
          final PsiElement replaced = returnTypeElement.replace(typeElement);
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
        }
      }
      else if (element instanceof PsiVariable) {
        final PsiTypeElement varTypeElement = ((PsiVariable)element).getTypeElement();
        if (varTypeElement != null) {
          final PsiElement replaced = varTypeElement.replace(typeElement);
          JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced);
        }
      }
      else {
        LOG.error("Must not happen: " + element.getClass().getName());
      }
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  static PsiNewExpression replaceNewExpressionType(final Project project, final PsiNewExpression expression, final Map.Entry<TypeMigrationUsageInfo, PsiType> info) {
    final PsiType changeType = info.getValue();
    if (changeType != null) {
      try {
        final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
        final PsiType componentType = changeType.getDeepComponentType();
        if (classReference != null) {
          final PsiElement psiElement = changeType.equals(RefactoringChangeUtil.getTypeByExpression(expression))
                                        ? classReference
                                        : replaceTypeWithClassReferenceOrKeyword(project, componentType, classReference);
          final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(psiElement, PsiNewExpression.class);
          if (!tryToReplaceWithDiamond(newExpression, changeType)) {
            return newExpression;
          }
        }
        else {
          final PsiElement typeKeyword = getTypeKeyword(expression);
          if (typeKeyword != null) {
            replaceTypeWithClassReferenceOrKeyword(project, componentType, typeKeyword);
          }
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    return null;
  }

  static boolean tryToReplaceWithDiamond(PsiNewExpression newExpression, @Nullable PsiType changeType) {
    if (newExpression != null && PsiDiamondTypeUtil.canCollapseToDiamond(newExpression, newExpression, changeType)) {
      final PsiJavaCodeReferenceElement anonymousClassReference = newExpression.getClassOrAnonymousClassReference();
      if (anonymousClassReference != null) {
        RemoveRedundantTypeArgumentsUtil.replaceExplicitWithDiamond(anonymousClassReference.getParameterList());
      }
      return true;
    }
    return false;
  }

  private static PsiElement replaceTypeWithClassReferenceOrKeyword(Project project, PsiType componentType, PsiElement typePlace) {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    if (componentType instanceof PsiClassType) {
      return typePlace.replace(factory.createReferenceElementByType((PsiClassType)componentType));
    } else {
      return typePlace.replace(getTypeKeyword(((PsiNewExpression)factory.createExpressionFromText("new " + componentType.getPresentableText() + "[0]", typePlace))));
    }
  }

  private static PsiElement getTypeKeyword(PsiNewExpression expression) {
    return ((CompositeElement)expression).findChildByRoleAsPsiElement(ChildRole.TYPE_KEYWORD);
  }
}