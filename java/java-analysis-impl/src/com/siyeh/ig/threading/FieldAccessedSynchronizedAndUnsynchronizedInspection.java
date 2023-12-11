/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.MakeFieldFinalFix;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class FieldAccessedSynchronizedAndUnsynchronizedInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean countGettersAndSetters = false;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "field.accessed.synchronized.and.unsynchronized.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("countGettersAndSetters", InspectionGadgetsBundle.message(
        "field.accessed.synchronized.and.unsynchronized.option")));
  }

  @Override
  public boolean runForWholeFile() {
    return true;
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return MakeFieldFinalFix.buildFix((PsiField)infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FieldAccessedSynchronizedAndUnsynchronizedVisitor();
  }

  private class FieldAccessedSynchronizedAndUnsynchronizedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (!containsSynchronization(aClass)) {
        return;
      }
      final VariableAccessVisitor visitor = new VariableAccessVisitor(aClass, countGettersAndSetters);
      aClass.accept(visitor);
      final Set<PsiField> fields = visitor.getInappropriatelyAccessedFields();
      for (final PsiField field : fields) {
        if (field.hasModifierProperty(PsiModifier.FINAL) ||
            field.hasModifierProperty(PsiModifier.VOLATILE)) {
          continue;
        }
        final PsiClass containingClass = field.getContainingClass();
        if (aClass.equals(containingClass)) {
          registerFieldError(field, field);
        }
      }
    }

    private static boolean containsSynchronization(PsiElement context) {
      final ContainsSynchronizationVisitor visitor =
        new ContainsSynchronizationVisitor();
      context.accept(visitor);
      return visitor.containsSynchronization();
    }
  }
}