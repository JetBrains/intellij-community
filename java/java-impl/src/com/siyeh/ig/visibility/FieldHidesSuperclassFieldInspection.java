/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.visibility;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiModifier;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.intellij.lang.annotations.Pattern;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class FieldHidesSuperclassFieldInspection extends BaseInspection {
  @SuppressWarnings("PublicField")
  public boolean m_ignoreInvisibleFields = true;
  @SuppressWarnings("PublicField")
  public boolean ignoreStaticFields = true;

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    return new RenameFix();
  }

  @Override
  public void writeSettings(@NotNull Element node) throws WriteExternalException {
    defaultWriteSettings(node, "ignoreStaticFields");
    writeBooleanOption(node, "ignoreStaticFields", true);
  }

  @Pattern(VALID_ID_PATTERN)
  @Override
  @NotNull
  public String getID() {
    return "FieldNameHidesFieldInSuperclass";
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "field.name.hides.in.superclass.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("m_ignoreInvisibleFields", InspectionGadgetsBundle.message("field.name.hides.in.superclass.ignore.option")),
      checkbox("ignoreStaticFields", InspectionGadgetsBundle.message("field.name.hides.in.superclass.ignore.static.field.option")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new FieldHidesSuperclassFieldVisitor();
  }

  private class FieldHidesSuperclassFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitField(@NotNull PsiField field) {
      final PsiClass aClass = field.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String fieldName = field.getName();
      if (HardcodedMethodConstants.SERIAL_VERSION_UID.equals(fieldName)) {
        return;    //special case
      }
      PsiClass ancestorClass = aClass.getSuperClass();
      final Set<PsiClass> visitedClasses = new HashSet<>();
      while (ancestorClass != null) {
        if (!visitedClasses.add(ancestorClass)) {
          return;
        }
        final PsiField ancestorField = ancestorClass.findFieldByName(fieldName, false);
        ancestorClass = ancestorClass.getSuperClass();
        if (ancestorField == null) {
          continue;
        }
        if (m_ignoreInvisibleFields && !ClassUtils.isFieldVisible(ancestorField, aClass)) {
          continue;
        }
        if (ignoreStaticFields && field.hasModifierProperty(PsiModifier.STATIC) &&
            ancestorField.hasModifierProperty(PsiModifier.STATIC)) {
          continue;
        }
        registerFieldError(field);
        return;
      }
    }
  }
}