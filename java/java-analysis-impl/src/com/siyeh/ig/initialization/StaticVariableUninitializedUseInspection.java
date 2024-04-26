/*
 * Copyright 2003-2021 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.initialization;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.UninitializedReadCollector;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Arrays;

import static com.intellij.codeInspection.options.OptPane.*;

public final class StaticVariableUninitializedUseInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignorePrimitives = false;

  @Override
  @NotNull
  public String getID() {
    return "StaticVariableUsedBeforeInitialization";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "static.variable.used.before.initialization.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignorePrimitives", InspectionGadgetsBundle.message("primitive.fields.ignore.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StaticVariableInitializationVisitor();
  }

  private class StaticVariableInitializationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      PsiField[] fields = aClass.getFields();
      if (aClass.isEnum()) {
        return;
      }
      for (PsiField field : fields) {
        if (!field.hasModifierProperty(PsiModifier.STATIC) || field.getInitializer() != null) {
          continue;
        }
        if (m_ignorePrimitives) {
          final PsiType type = field.getType();
          if (ClassUtils.isPrimitive(type)) {
            continue;
          }
        }

        final UninitializedReadCollector uninitializedReadCollector = new UninitializedReadCollector();
        boolean assignedInInitializer = Arrays.stream(aClass.getInitializers())
          .filter(initializer -> initializer.hasModifierProperty(PsiModifier.STATIC))
          .map(PsiClassInitializer::getBody)
          .anyMatch(body -> uninitializedReadCollector.blockAssignsVariable(body, field));
        if (assignedInInitializer) {
          final PsiExpression[] badReads = uninitializedReadCollector.getUninitializedReads();
          for (PsiExpression badRead : badReads) {
            registerError(badRead);
          }
          continue;
        }

        final PsiMethod[] methods = aClass.getMethods();
        for (PsiMethod method : methods) {
          if (!method.hasModifierProperty(PsiModifier.STATIC) || !method.isPhysical() /* EA-263167 */) {
            continue;
          }
          final PsiCodeBlock body = method.getBody();
          uninitializedReadCollector.blockAssignsVariable(body, field);
        }
        final PsiExpression[] moreBadReads = uninitializedReadCollector.getUninitializedReads();
        for (PsiExpression badRead : moreBadReads) {
          registerError(badRead);
        }
      }
    }
  }
}