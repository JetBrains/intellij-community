/*
 * Copyright 2003-2022 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.javabeans;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class ClassWithoutNoArgConstructorInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreClassesWithNoConstructors = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreClassesWithNoConstructors", InspectionGadgetsBundle.message("class.without.no.arg.constructor.ignore.option")));
  }

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("class.without.no.arg.constructor.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ClassWithoutNoArgConstructorVisitor();
  }

  private class ClassWithoutNoArgConstructorVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isEnum() || aClass.isRecord()) {
        return;
      }
      if (aClass instanceof PsiAnonymousClass || aClass instanceof PsiTypeParameter) {
        return;
      }
      if (classHasNoArgConstructor(aClass, m_ignoreClassesWithNoConstructors)) {
        return;
      }
      registerClassError(aClass);
    }

    private static boolean classHasNoArgConstructor(PsiClass aClass, boolean ignoreNoConstructor) {
      final PsiMethod[] constructors = aClass.getConstructors();
      if (ignoreNoConstructor && constructors.length == 0) {
        return true;
      }
      return ContainerUtil.exists(constructors, c -> c.getParameterList().isEmpty());
    }
  }
}