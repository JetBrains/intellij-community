/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.CompositeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.typeMigration.usageInfo.TypeMigrationUsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.Map;

/**
 * @author anna
 * Date: 04-Apr-2008
 */
public class TypeMigrationReplacementUtil {
  public static final Logger LOG = Logger.getInstance("#" + TypeMigrationReplacementUtil.class.getName());

  private TypeMigrationReplacementUtil() {
  }

  public static void replaceExpression(PsiExpression expression, final Project project, Object conversion) {
    if (conversion instanceof TypeConversionDescriptorBase) {
      ((TypeConversionDescriptorBase)conversion).replace(expression);
    } else if (conversion instanceof String) {
      String replacement = (String)conversion;
      try {
        expression.replace(
            JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(replacement, expression));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
    else if (expression instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression)expression).resolve();
      final PsiMember replacer = ((PsiMember)conversion);
      final String method = ((PsiMember)resolved).getName();
      final String ref = expression.getText();
      final String newref = ref.substring(0, ref.lastIndexOf(method)) + replacer.getName();

      if (conversion instanceof PsiMethod) {
        if (resolved instanceof PsiMethod) {
          try {
            expression.replace(
                JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(newref, expression));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        else {
          try {
            expression.replace(JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(
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
            expression.replace(
                JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(newref, expression));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
          }
        }
        else {
          final PsiElement parent = Util.getEssentialParent(expression);

          if (parent instanceof PsiMethodCallExpression) {
            try {
              parent.replace(
                  JavaPsiFacade.getInstance(project).getElementFactory().createExpressionFromText(newref, expression));
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
          }
        }
      }
    }
  }

  static void migratePsiMemberType(final PsiElement element, final Project project, PsiType migratedType) {
    try {
      if (!migratedType.isValid()) {
        migratedType = JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(migratedType.getCanonicalText());
      }
      final PsiTypeElement typeElement =
          JavaPsiFacade.getInstance(project).getElementFactory().createTypeElement(migratedType);
      if (element instanceof PsiMethod) {
        final PsiTypeElement returnTypeElement = ((PsiMethod)element).getReturnTypeElement();
        if (returnTypeElement != null) {
          returnTypeElement.replace(typeElement);
        }
      }
      else if (element instanceof PsiVariable) {
        final PsiTypeElement varTypeElement = ((PsiVariable)element).getTypeElement();
        if (varTypeElement != null) {
          varTypeElement.replace(typeElement);
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

  static void replaceNewExpressionType(final Project project, final PsiNewExpression expression, final Map.Entry<TypeMigrationUsageInfo, PsiType> info) {
    final PsiType changeType = info.getValue();
    if (changeType != null) {
      try {
        final PsiJavaCodeReferenceElement classReference = expression.getClassOrAnonymousClassReference();
        final PsiType componentType = changeType.getDeepComponentType();
        if (classReference != null) {
          final PsiElement psiElement = replaceTypeWithClassReferenceOrKeyword(project, componentType, classReference);
          final PsiNewExpression newExpression = PsiTreeUtil.getParentOfType(psiElement, PsiNewExpression.class);
          if (newExpression != null && PsiDiamondTypeUtil.canCollapseToDiamond(newExpression, newExpression, changeType)) {
            final PsiJavaCodeReferenceElement anonymousClassReference = newExpression.getClassOrAnonymousClassReference();
            if (anonymousClassReference != null) {
              PsiDiamondTypeUtil.replaceExplicitWithDiamond(anonymousClassReference.getParameterList());
            }
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
  }

  private static PsiElement replaceTypeWithClassReferenceOrKeyword(Project project, PsiType componentType, PsiElement typePlace) {
    final PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
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