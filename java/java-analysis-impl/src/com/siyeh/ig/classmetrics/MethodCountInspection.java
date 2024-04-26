/*
 * Copyright 2003-2023 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.classmetrics;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PropertyUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.*;

public final class MethodCountInspection extends BaseInspection {

  private static final int DEFAULT_METHOD_COUNT_LIMIT = 20;

  @SuppressWarnings("PublicField")
  public int m_limit = DEFAULT_METHOD_COUNT_LIMIT;

  @SuppressWarnings("PublicField")
  public boolean ignoreGettersAndSetters = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreOverridingMethods = false;

  @Override
  @NotNull
  public String getID() {
    return "ClassWithTooManyMethods";
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      number("m_limit", InspectionGadgetsBundle.message("method.count.limit.option"), 1, 1000),
      checkbox("ignoreGettersAndSetters", InspectionGadgetsBundle.message("method.count.ignore.getters.setters.option")),
      checkbox("ignoreOverridingMethods", InspectionGadgetsBundle.message("ignore.methods.overriding.super.method"))
    );
  }


  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final Integer count = (Integer)infos[0];
    return InspectionGadgetsBundle.message("too.many.methods.problem.descriptor", count);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodCountVisitor();
  }

  private class MethodCountVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      final int methodCount = calculateTotalMethodCount(aClass);
      if (methodCount <= m_limit) {
        return;
      }
      registerClassError(aClass, Integer.valueOf(methodCount));
    }

    private int calculateTotalMethodCount(PsiClass aClass) {
      int totalCount = 0;
      for (final PsiMethod method : aClass.getMethods()) {
        if (method.isConstructor() || !method.isPhysical()) {
          continue;
        }
        if (ignoreGettersAndSetters) {
          if (PropertyUtil.isSimpleGetter(method) || PropertyUtil.isSimpleSetter(method)) {
            continue;
          }
        }
        if (ignoreOverridingMethods) {
          if (MethodUtils.hasSuper(method)) {
            continue;
          }
        }
        totalCount++;
      }
      return totalCount;
    }
  }
}