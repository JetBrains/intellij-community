/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ThreadWithDefaultRunMethodInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "InstantiatingAThreadWithDefaultRunMethod";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "thread.with.default.run.method.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThreadWithDefaultRunMethodVisitor();
  }

  private static class ThreadWithDefaultRunMethodVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      if (anonymousClass != null) {
        if (definesRun(anonymousClass)) {
          return;
        }
        processExpression(expression, anonymousClass.getBaseClassReference());
      }
      else {
        final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
        if (classReference == null) {
          return;
        }
        processExpression(expression, classReference);
      }
    }

    private void processExpression(PsiNewExpression expression, PsiJavaCodeReferenceElement baseClassReference) {
      final PsiElement referent = baseClassReference.resolve();
      if (!(referent instanceof PsiClass referencedClass)) {
        return;
      }
      if (!InheritanceUtil.isInheritor(referencedClass, "java.lang.Thread")) {
        return;
      }

      final String referencedClassName = referencedClass.getQualifiedName();
      if ("java.lang.Thread".equals(referencedClassName)) {
        checkThreadCreation(expression);
        return;
      }
      //it is an inheritor
      //check there is `run` method or call constructor of Thread with Runnable
      PsiClass currentClass = referencedClass;
      while (currentClass != null && !("java.lang.Thread".equals(currentClass.getQualifiedName()) ||
                                       CommonClassNames.JAVA_LANG_OBJECT.equals(currentClass.getQualifiedName()))) {
        if (definesRun(currentClass)) {
          return;
        }
        PsiClass superClass = currentClass.getSuperClass();
        if (superClass != null && "java.lang.Thread".equals(superClass.getQualifiedName())) {
          if (hasCallSuperWithRunnable(currentClass)) {
            return;
          }
        }
        currentClass = superClass;
      }
      registerNewExpressionError(expression);
    }

    private static boolean hasCallSuperWithRunnable(@NotNull PsiClass currentClass) {
      for (PsiMethod method : currentClass.getMethods()) {
        if (method.isConstructor()) {
          PsiMethodCallExpression call = getSuperCall(method);
          if (call == null) {
            continue;
          }
          if (anyIsRunnable(call.getArgumentList().getExpressions())) {
            return true;
          }
        }
      }
      return false;
    }

    @Nullable
    private static PsiMethodCallExpression getSuperCall(@NotNull PsiMethod method) {
      PsiCodeBlock body = method.getBody();
      if (body == null) {
        return null;
      }
      PsiStatement[] statements = body.getStatements();
      PsiStatement firstStmt = statements.length > 0 ? statements[0] : null;
      if (firstStmt instanceof PsiExpressionStatement) {
        PsiExpression call = ((PsiExpressionStatement)firstStmt).getExpression();
        if (call instanceof PsiMethodCallExpression && JavaPsiConstructorUtil.isSuperConstructorCall(call)) {
          return (PsiMethodCallExpression)call;
        }
      }
      return null;
    }

    private void checkThreadCreation(PsiNewExpression expression) {
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (anyIsRunnable(arguments)) {
        return;
      }

      registerNewExpressionError(expression);
    }

    private static boolean anyIsRunnable(PsiExpression[] arguments) {
      for (PsiExpression argument : arguments) {
        if (TypeUtils.expressionHasTypeOrSubtype(argument, "java.lang.Runnable")) {
          return true;
        }
      }
      return false;
    }

    private static boolean definesRun(PsiClass aClass) {
      final PsiMethod[] methods = aClass.findMethodsByName(HardcodedMethodConstants.RUN, false);
      for (final PsiMethod method : methods) {
        final PsiParameterList parameterList = method.getParameterList();
        if (!method.hasModifierProperty(PsiModifier.ABSTRACT) && parameterList.isEmpty()) {
          return true;
        }
      }
      return false;
    }
  }
}