/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.serialization;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class SerializableStoresNonSerializableInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiElement classOrLambda = (PsiElement)infos[0];
    final PsiType type = (PsiType)infos[1];
    if (classOrLambda instanceof PsiClass aClass) {
      if (aClass instanceof PsiAnonymousClass) {
        return InspectionGadgetsBundle.message("serializable.anonymous.class.stores.non.serializable.problem.descriptor",
                                               type.getPresentableText());
      }
      else {
        return InspectionGadgetsBundle.message("serializable.local.class.stores.non.serializable.problem.descriptor",
                                               type.getPresentableText(), aClass.getName());
      }
    }
    return InspectionGadgetsBundle.message("serializable.lambda.stores.non.serializable.problem.descriptor", type.getPresentableText());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SerializableStoresNonSerializableVisitor();
  }

  private static class SerializableStoresNonSerializableVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      super.visitClass(aClass);
      final PsiElement parent = aClass.getParent();
      if (!(parent instanceof PsiDeclarationStatement) && !(aClass instanceof PsiAnonymousClass)) {
        return;
      }
      if (!SerializationUtils.isSerializable(aClass)) {
        return;
      }
      final LocalVariableReferenceFinder visitor = new LocalVariableReferenceFinder(aClass);
      PsiElement child = aClass.getLBrace();
      while (child != null) {
        child.accept(visitor);
        child = child.getNextSibling();
      }
    }

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression lambda) {
      super.visitLambdaExpression(lambda);
      final PsiType type = lambda.getFunctionalInterfaceType();
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
      if (!SerializationUtils.isSerializable(aClass)) {
        return;
      }
      lambda.accept(new LocalVariableReferenceFinder(lambda));
    }

    private class LocalVariableReferenceFinder extends JavaRecursiveElementWalkingVisitor {
      @NotNull
      private final PsiElement myClassOrLambda;

      LocalVariableReferenceFinder(@NotNull PsiElement classOrLambda) {
        myClassOrLambda = classOrLambda;
      }

      @Override
      public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
        super.visitReferenceExpression(expression);
        if (expression.getQualifierExpression() != null) {
          return;
        }
        final PsiType type = expression.getType();
        if (SerializationUtils.isProbablySerializable(type)) {
          return;
        }
        final PsiElement target = expression.resolve();
        if (!(target instanceof PsiLocalVariable) && !(target instanceof PsiParameter)) {
          return;
        }
        final PsiVariable variable = (PsiVariable)target;
        if (!variable.hasModifierProperty(PsiModifier.FINAL)) {
          if (!PsiUtil.isLanguageLevel8OrHigher(variable) ||
              !HighlightControlFlowUtil.isEffectivelyFinal(variable, myClassOrLambda, expression)) {
            // don't warn on uncompilable code.
            return;
          }
        }
        if (PsiTreeUtil.isAncestor(myClassOrLambda, variable, true)) {
          return;
        }
        registerError(expression, myClassOrLambda, type);
      }
    }
  }
}
