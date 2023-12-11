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
package com.siyeh.ig.logging;

import com.intellij.codeInsight.options.JavaClassValidator;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.MakeFieldStaticFinalFix;
import com.siyeh.ig.psiutils.JavaLoggingUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.intellij.codeInspection.options.OptPane.stringList;

public final class NonStaticFinalLoggerInspection extends BaseInspection {

  protected final List<String> loggerClassNames = new ArrayList<>();
  @SuppressWarnings("PublicField")
  public String loggerClassName = StringUtil.join(JavaLoggingUtils.DEFAULT_LOGGERS, ",");

  public NonStaticFinalLoggerInspection() {
    parseString(loggerClassName, loggerClassNames);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      stringList("loggerClassNames", InspectionGadgetsBundle.message("logger.class.name"),
                 new JavaClassValidator().withTitle(InspectionGadgetsBundle.message("choose.logger.class"))));
  }

  @Override
  @NotNull
  public String getID() {
    return "NonConstantLogger";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("non.constant.logger.problem.descriptor");
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiField field = (PsiField)infos[0];
    return MakeFieldStaticFinalFix.buildFixUnconditional(field);
  }

  @Override
  public void readSettings(@NotNull Element element) throws InvalidDataException {
    super.readSettings(element);
    parseString(loggerClassName, loggerClassNames);
  }

  @Override
  public void writeSettings(@NotNull Element element) throws WriteExternalException {
    loggerClassName = formatString(loggerClassNames);
    super.writeSettings(element);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NonStaticFinalLoggerVisitor();
  }

  private class NonStaticFinalLoggerVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      if (aClass.isInterface() || aClass.isEnum() || aClass.isAnnotationType()) {
        return;
      }
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      if (aClass.getContainingClass() != null) {
        return;
      }
      final PsiField[] fields = aClass.getFields();
      for (final PsiField field : fields) {
        if (!isLogger(field)) {
          continue;
        }
        if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
          continue;
        }
        registerFieldError(field, field);
      }
    }

    private boolean isLogger(PsiVariable variable) {
      final PsiType type = variable.getType();
      final String text = type.getCanonicalText();
      return loggerClassNames.contains(text);
    }
  }
}