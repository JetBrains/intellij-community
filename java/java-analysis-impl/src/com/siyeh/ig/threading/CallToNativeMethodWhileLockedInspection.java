/*
 * Copyright 2003-2025 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SynchronizationUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public final class CallToNativeMethodWhileLockedInspection extends BaseInspection {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("call.to.native.method.while.locked.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CallToNativeMethodWhileLockedVisitor();
  }

  private static class CallToNativeMethodWhileLockedVisitor extends BaseInspectionVisitor {

    private static final Set<String> EXCLUDED_CLASS_NAMES =
      Set.of(CommonClassNames.JAVA_LANG_OBJECT, "java.lang.System", "sun.misc.Unsafe", "java.lang.invoke.MethodHandle");

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      final PsiMethod method = expression.resolveMethod();
      if (method == null || !method.hasModifierProperty(PsiModifier.NATIVE)) return;

      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return;
      String name = containingClass.getQualifiedName();
      if (name != null && EXCLUDED_CLASS_NAMES.contains(name)) return;

      if (!SynchronizationUtil.isInSynchronizedContext(expression)) return;
      registerMethodCallError(expression);
    }
  }
}