/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: Jul 30, 2010
 */
public class PsiDiamondType extends PsiType {
  private static final PsiType[] NULL_TYPES = new PsiType[]{NULL};
  private PsiManager myManager;
  private final PsiTypeElement myTypeElement;

  public PsiDiamondType(PsiManager manager, PsiTypeElement psiTypeElement) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myManager = manager;
    myTypeElement = psiTypeElement;
  }

  @Override
  public String getPresentableText() {
    return "";
  }

  @Override
  public String getCanonicalText() {
    return "";
  }

  @Override
  public String getInternalCanonicalText() {
    return "Diamond Type";
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public boolean equalsToText(@NonNls String text) {
    return text != null && text.isEmpty();
  }

  @Override
  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return GlobalSearchScope.allScope(myManager.getProject());
  }

  @NotNull
  @Override
  public PsiType[] getSuperTypes() {
    return new PsiType[]{getJavaLangObject(myManager, getResolveScope())};
  }

  public PsiType[] getInferredTypes() {
    final PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(myTypeElement, PsiDeclarationStatement.class);
    if (declarationStatement != null) {
      final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
      if (declaredElements.length > 0 && declaredElements[0] instanceof PsiVariable) {
        return getComponentTypes(((PsiVariable)declaredElements[0]).getType());
      }
    }

    final PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(myTypeElement, PsiAssignmentExpression.class);
    if (assignmentExpression != null) {
      final PsiExpression lExpression = assignmentExpression.getLExpression();
      if (lExpression instanceof PsiReferenceExpression) {
        final PsiElement resolved = ((PsiReferenceExpression)lExpression).resolve();
        if (resolved instanceof PsiVariable) {
          return getComponentTypes(((PsiVariable)resolved).getType());
        }
      }
    }

    PsiExpression psiExpression = PsiTreeUtil.getParentOfType(myTypeElement, PsiExpression.class);
    while (psiExpression != null) {
      final PsiElement parent = psiExpression.getParent();
      if (parent instanceof PsiExpression) {
        psiExpression = (PsiExpression)parent;
        continue;
      }
      break;
    }
    if (psiExpression != null) {
      final PsiElement parent = psiExpression.getParent();
      if (parent instanceof PsiExpressionList) {
        final PsiElement parentParent = parent.getParent();
        if (parentParent instanceof PsiCallExpression) {
          final JavaResolveResult resolveResult = ((PsiCallExpression)parentParent).resolveMethodGenerics();
          final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
          final PsiElement element = resolveResult.getElement();
          if (element instanceof PsiMethod) {
            final int paramIdx = ArrayUtil.find(((PsiExpressionList)parent).getExpressions(), psiExpression);
            if (paramIdx > -1) {
              final PsiParameter parameter =
                ((PsiMethod)element).getParameterList().getParameters()[paramIdx];
              return getComponentTypes(substitutor.substitute(parameter.getType()));
            }
          }
        }
      }
    }
    return NULL_TYPES;
  }

  private static PsiType[] getComponentTypes(PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiType[] types = ((PsiClassType)type).getParameters();
      for (int i = 0; i < types.length; i++) {
        PsiType currentType = types[i];
        if (currentType instanceof PsiWildcardType) {
          final PsiType bound = ((PsiWildcardType)currentType).getBound();
          if (bound != null) {
            types[i] = bound;
          }
        }
      }
      return types;
    }
    return NULL_TYPES;
  }
}
